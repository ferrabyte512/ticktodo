package app.ticktodo.domain

/**
 * The single place where sync conflicts are decided. See docs/sync-design.md.
 *
 * Policy: last writer wins per document, using the edit time recorded on the
 * device that made the edit (`updatedAt`).
 *  - A local row with no unsynced changes always takes the server version.
 *  - A local row with unsynced changes keeps them only if they are strictly
 *    newer than the server version. Ties go to the server, which also makes a
 *    retried push (where the server already has our data) settle cleanly.
 *  - Deletions are ordinary edits (a tombstone with its own `updatedAt`), so a
 *    later restore beats an earlier delete and vice versa.
 *
 * Local edits are stamped with max(now, previousUpdatedAt + 1), so an edit made
 * after seeing a version always beats that version even if this device's clock
 * is behind.
 */
object ConflictResolver {

    enum class Decision { TAKE_REMOTE, KEEP_LOCAL }

    data class Meta(val updatedAt: Long, val dirty: Boolean)

    fun resolve(local: Meta?, remoteUpdatedAt: Long): Decision = when {
        local == null -> Decision.TAKE_REMOTE
        !local.dirty -> Decision.TAKE_REMOTE
        local.updatedAt > remoteUpdatedAt -> Decision.KEEP_LOCAL
        else -> Decision.TAKE_REMOTE
    }

    /** Timestamp for a new local edit of a row last stamped [previous]. */
    fun nextEditTime(now: Long, previous: Long?): Long =
        if (previous == null) now else maxOf(now, previous + 1)
}
