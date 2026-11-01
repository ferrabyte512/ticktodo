package app.ticktodo.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.remote.RemoteException
import app.ticktodo.data.remote.RemoteMapper
import app.ticktodo.data.remote.RemoteStore
import app.ticktodo.data.remote.RemoteStore.Collection
import app.ticktodo.data.remote.RemoteStore.Page
import app.ticktodo.data.remote.RemoteStore.PushResult
import app.ticktodo.data.remote.RemoteStore.RemoteDoc
import app.ticktodo.data.repository.TaskChangeEffects
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now(): Long = time
    override fun zone(): ZoneId = ZoneOffset.UTC
    fun advance(ms: Long) { time += ms }
}

class FakeSession(uid: String?) : UserSession {
    override val uid = MutableStateFlow(uid)
}

class RecordingEffects : TaskChangeEffects {
    val changed = mutableListOf<String>()
    var listChanges = 0
    override fun tasksChanged(taskIds: kotlin.collections.Collection<String>) { changed += taskIds }
    override fun listsChanged() { listChanges++ }
}

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/**
 * In-memory stand-in for Firestore with the same contract as FirestoreRemoteStore:
 * a server clock stamps every write, pulls are ordered by that stamp, and
 * pushIfNewer refuses to overwrite a version with an equal or newer updatedAt.
 * It also mirrors the key ownership rule: one user's calls never see another's data.
 */
class FakeRemoteStore : RemoteStore {
    private var serverClock = 1_000L
    private val docs = mutableMapOf<Triple<String, Collection, String>, RemoteDoc>()

    /** When set, every call throws this (simulates outages and auth problems). */
    var failWith: RemoteException.Code? = null
    /** Document ids the "rules" refuse to accept. */
    val rejectIds = mutableSetOf<String>()
    var pushCalls = 0
    var pullCalls = 0
    /** Documents returned by pulls: what Firestore would bill as reads (an empty page bills one). */
    var billedReads = 0
    /** Forces every page to this size, to exercise pagination. */
    var pageSizeOverride: Int? = null
    /** When true, pushes share one server commit time, like a batch of writes landing together. */
    var freezeServerClock = false

    fun all(uid: String, c: Collection): List<RemoteDoc> =
        docs.filterKeys { it.first == uid && it.second == c }.values.sortedBy { it.serverUpdatedAt }

    fun get(uid: String, c: Collection, id: String): RemoteDoc? = docs[Triple(uid, c, id)]

    private fun check() { failWith?.let { throw RemoteException(it) } }

    override suspend fun pullPage(uid: String, collection: Collection, sinceMillis: Long, sinceId: String?, pageSize: Int, after: Any?): Page {
        check()
        pullCalls++
        val size = pageSizeOverride ?: pageSize
        val sorted = all(uid, collection)
            .filter { if (sinceId == null) it.serverUpdatedAt >= sinceMillis else it.serverUpdatedAt > sinceMillis || (it.serverUpdatedAt == sinceMillis && it.id > sinceId) }
            .sortedWith(compareBy({ it.serverUpdatedAt }, { it.id }))
        val start = (after as? Int) ?: 0
        val page = sorted.drop(start).take(size)
        billedReads += maxOf(1, page.size)
        return Page(page, if (page.size < size) null else start + page.size)
    }

    override suspend fun pushIfNewer(uid: String, collection: Collection, id: String, data: Map<String, Any?>, updatedAt: Long): PushResult {
        check()
        pushCalls++
        if (id in rejectIds) throw RemoteException(RemoteException.Code.PERMISSION_DENIED)
        val key = Triple(uid, collection, id)
        val existing = docs[key]
        val existingUpdated = existing?.let { RemoteMapper.updatedAtOf(it) }
        if (existing != null && existingUpdated != null && existingUpdated >= updatedAt) {
            billedReads++
            return PushResult.RemoteWins(existing)
        }
        // The transaction's read of the current document is billed too.
        billedReads++
        docs[key] = RemoteDoc(id, data, if (freezeServerClock) serverClock else ++serverClock)
        return PushResult.Written
    }

    override suspend fun purgeTombstones(uid: String, collection: Collection, beforeMillis: Long, limit: Int): Int {
        check()
        val victims = all(uid, collection).filter { it.data["deleted"] == true && (RemoteMapper.updatedAtOf(it) ?: 0) < beforeMillis }.take(limit)
        victims.forEach { docs.remove(Triple(uid, collection, it.id)) }
        return victims.size
    }

    override suspend fun deleteAllUserData(uid: String) {
        check()
        docs.keys.filter { it.first == uid }.forEach(docs::remove)
    }
}
