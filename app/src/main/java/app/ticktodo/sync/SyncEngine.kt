package app.ticktodo.sync

import androidx.room.withTransaction
import app.ticktodo.core.AppLog
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.local.SyncStateEntity
import app.ticktodo.data.remote.RemoteException
import app.ticktodo.data.remote.RemoteMapper
import app.ticktodo.data.remote.RemoteStore
import app.ticktodo.data.remote.RemoteStore.Collection
import app.ticktodo.data.remote.RemoteStore.PushResult
import app.ticktodo.domain.ConflictResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Serialises sync runs with sign-out / account deletion so a wipe never races a sync. */
@Singleton
class SyncLock @Inject constructor() {
    val mutex = Mutex()
}

/**
 * Two-way sync between Room and the remote store. See docs/sync-design.md.
 *
 * One run:
 *  1. Pull remote changes since the stored cursor (lists, then tasks), page by
 *     page, resolving conflicts with [ConflictResolver].
 *  2. Push dirty rows one document at a time with a check-and-write transaction.
 *  3. Purge old tombstones (local after 30 days; remote after 90 days, at most daily).
 *
 * Every step is idempotent: a run interrupted at any point (process death,
 * network loss) can simply be run again.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val session: UserSession,
    private val clock: Clock,
    private val lock: SyncLock,
) {
    sealed interface Outcome {
        data class Success(
            val pulled: Int,
            val pushed: Int,
            val conflicts: Int,
            val rejected: Int,
            val changedTaskIds: Set<String>,
        ) : Outcome

        data class Failed(val code: RemoteException.Code, val retryable: Boolean) : Outcome

        /** The user changed (or signed out) before or during the run. */
        data object Skipped : Outcome
    }

    private class UserChanged : Exception()

    private val taskDao = db.taskDao()
    private val listDao = db.taskListDao()
    private val stateDao = db.syncStateDao()

    suspend fun sync(uid: String, forceFull: Boolean = false): Outcome = lock.mutex.withLock {
        if (session.uid.value != uid) return Outcome.Skipped
        val start = clock.now()
        val state = stateDao.get(uid) ?: SyncStateEntity(uid)
        stateDao.upsert(state.copy(lastAttemptAt = start))
        val full = forceFull || state.lastSuccessAt == null || start - state.lastSuccessAt > FULL_RESYNC_AFTER_MS

        val stats = Stats()
        try {
            val listsCursor = pull(uid, Collection.LISTS, if (full) 0 else state.listsCursor, full, stats)
            val tasksCursor = pull(uid, Collection.TASKS, if (full) 0 else state.tasksCursor, full, stats)
            push(uid, Collection.LISTS, stats)
            push(uid, Collection.TASKS, stats)
            val purgedAt = purge(uid, state, start)
            ensureSameUser(uid)
            stateDao.upsert(
                state.copy(
                    listsCursor = listsCursor,
                    tasksCursor = tasksCursor,
                    lastAttemptAt = start,
                    lastSuccessAt = clock.now(),
                    lastErrorCode = if (stats.rejected > 0) REJECTED_CODE else null,
                    lastRemotePurgeAt = purgedAt,
                ),
            )
            Outcome.Success(stats.pulled, stats.pushed, stats.conflicts, stats.rejected, stats.changedTaskIds)
        } catch (e: UserChanged) {
            Outcome.Skipped
        } catch (e: RemoteException) {
            // Debug builds log the full cause (e.g. which rule or database is missing).
            AppLog.w("sync failed: ${e.code}", e.cause)
            if (session.uid.value == uid) {
                stateDao.upsert(state.copy(lastAttemptAt = start, lastErrorCode = e.code.name))
            }
            Outcome.Failed(e.code, e.retryable)
        }
    }

    private class Stats {
        var pulled = 0
        var pushed = 0
        var conflicts = 0
        var rejected = 0
        val changedTaskIds = mutableSetOf<String>()
    }

    private fun ensureSameUser(uid: String) {
        if (session.uid.value != uid) throw UserChanged()
    }

    /** Returns the new cursor (largest server commit time seen). */
    private suspend fun pull(uid: String, collection: Collection, since: Long, full: Boolean, stats: Stats): Long {
        var cursor = since
        var after: Any? = null
        val seen = if (full) HashSet<String>() else null
        do {
            val page = remote.pullPage(uid, collection, since, PAGE_SIZE, after)
            ensureSameUser(uid)
            db.withTransaction {
                for (doc in page.docs) {
                    seen?.add(doc.id)
                    cursor = maxOf(cursor, doc.serverUpdatedAt)
                    val remoteUpdatedAt = RemoteMapper.updatedAtOf(doc) ?: continue
                    when (collection) {
                        Collection.TASKS -> {
                            val local = taskDao.get(uid, doc.id)
                            val decision = ConflictResolver.resolve(local?.let { ConflictResolver.Meta(it.updatedAt, it.dirty) }, remoteUpdatedAt)
                            if (local?.dirty == true) stats.conflicts++
                            if (decision == ConflictResolver.Decision.TAKE_REMOTE) {
                                val entity = RemoteMapper.taskFromRemote(doc, uid, local) ?: continue
                                if (entity != local) {
                                    taskDao.upsert(entity)
                                    stats.pulled++
                                    stats.changedTaskIds += doc.id
                                }
                            }
                        }
                        Collection.LISTS -> {
                            val local = listDao.get(uid, doc.id)
                            val decision = ConflictResolver.resolve(local?.let { ConflictResolver.Meta(it.updatedAt, it.dirty) }, remoteUpdatedAt)
                            if (local?.dirty == true) stats.conflicts++
                            if (decision == ConflictResolver.Decision.TAKE_REMOTE) {
                                val entity = RemoteMapper.listFromRemote(doc, uid, local) ?: continue
                                if (entity != local) {
                                    listDao.upsert(entity)
                                    stats.pulled++
                                }
                            }
                        }
                    }
                }
            }
            after = page.next
        } while (after != null)

        // After a full pull, clean local rows the server no longer has were purged remotely.
        if (seen != null) {
            ensureSameUser(uid)
            when (collection) {
                Collection.TASKS -> {
                    val stale = taskDao.cleanIds(uid).filterNot(seen::contains)
                    stale.chunked(500).forEach { taskDao.deleteCleanByIds(uid, it) }
                    stats.changedTaskIds += stale
                }
                Collection.LISTS -> {
                    val stale = listDao.cleanIds(uid).filterNot(seen::contains)
                    stale.chunked(500).forEach { listDao.deleteCleanByIds(uid, it) }
                }
            }
        }
        return cursor
    }

    private suspend fun push(uid: String, collection: Collection, stats: Stats) {
        val skipped = HashSet<String>()
        repeat(MAX_PUSH_ROUNDS) {
            val rows: List<Pair<String, Long>> = when (collection) {
                Collection.TASKS -> taskDao.dirty(uid, PUSH_BATCH + skipped.size).map { it.id to it.localRevision }
                Collection.LISTS -> listDao.dirty(uid, PUSH_BATCH + skipped.size).map { it.id to it.localRevision }
            }.filterNot { it.first in skipped }
            if (rows.isEmpty()) return

            for ((id, _) in rows) {
                ensureSameUser(uid)
                // Re-read so we push the newest local version.
                val (data, updatedAt, revision) = when (collection) {
                    Collection.TASKS -> taskDao.get(uid, id)?.takeIf { it.dirty }
                        ?.let { Triple(RemoteMapper.taskToMap(it), it.updatedAt, it.localRevision) }
                    Collection.LISTS -> listDao.get(uid, id)?.takeIf { it.dirty }
                        ?.let { Triple(RemoteMapper.listToMap(it), it.updatedAt, it.localRevision) }
                } ?: continue

                val result = try {
                    remote.pushIfNewer(uid, collection, id, data, updatedAt)
                } catch (e: RemoteException) {
                    if (e.code == RemoteException.Code.PERMISSION_DENIED || e.code == RemoteException.Code.INVALID) {
                        // The server refused this document. Keep it locally and carry on with the rest.
                        AppLog.w("push rejected for one ${collection.path} document: ${e.code}")
                        skipped += id
                        stats.rejected++
                        continue
                    }
                    throw e
                }
                when (result) {
                    PushResult.Written -> {
                        when (collection) {
                            Collection.TASKS -> taskDao.markClean(uid, id, revision)
                            Collection.LISTS -> listDao.markClean(uid, id, revision)
                        }
                        stats.pushed++
                    }
                    is PushResult.RemoteWins -> db.withTransaction {
                        stats.conflicts++
                        when (collection) {
                            Collection.TASKS -> {
                                val current = taskDao.get(uid, id)
                                if (current != null && current.localRevision == revision) {
                                    val entity = RemoteMapper.taskFromRemote(result.doc, uid, current)
                                    if (entity != null) taskDao.upsert(entity) else taskDao.markClean(uid, id, revision)
                                    stats.changedTaskIds += id
                                }
                            }
                            Collection.LISTS -> {
                                val current = listDao.get(uid, id)
                                if (current != null && current.localRevision == revision) {
                                    val entity = RemoteMapper.listFromRemote(result.doc, uid, current)
                                    if (entity != null) listDao.upsert(entity) else listDao.markClean(uid, id, revision)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Returns the time of the last remote purge. Purge failures never fail the sync. */
    private suspend fun purge(uid: String, state: SyncStateEntity, now: Long): Long? {
        taskDao.purgeTombstones(uid, now - LOCAL_TOMBSTONE_TTL_MS)
        listDao.purgeTombstones(uid, now - LOCAL_TOMBSTONE_TTL_MS)
        val last = state.lastRemotePurgeAt
        if (last != null && now - last < DAY_MS) return last
        return try {
            remote.purgeTombstones(uid, Collection.TASKS, now - REMOTE_TOMBSTONE_TTL_MS, PURGE_LIMIT)
            remote.purgeTombstones(uid, Collection.LISTS, now - REMOTE_TOMBSTONE_TTL_MS, PURGE_LIMIT)
            now
        } catch (e: RemoteException) {
            AppLog.w("tombstone purge skipped: ${e.code}")
            last
        }
    }

    companion object {
        const val PAGE_SIZE = 300
        const val PUSH_BATCH = 100
        const val MAX_PUSH_ROUNDS = 20
        const val PURGE_LIMIT = 200
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val LOCAL_TOMBSTONE_TTL_MS = 30 * DAY_MS
        const val REMOTE_TOMBSTONE_TTL_MS = 90 * DAY_MS
        /** Must stay below the remote tombstone TTL so devices learn about purged deletions. */
        const val FULL_RESYNC_AFTER_MS = 60 * DAY_MS
        const val REJECTED_CODE = "REJECTED"
    }
}
