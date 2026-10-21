package app.ticktodo.data.remote

/**
 * Backend-neutral view of the cloud store. Documents are plain maps so the
 * sync engine can be tested against an in-memory fake with the same
 * semantics as Firestore.
 *
 * Every call is scoped to one user; the server rules reject any access to
 * another user's path regardless of what the client asks for.
 */
interface RemoteStore {

    enum class Collection(val path: String) { TASKS("tasks"), LISTS("lists") }

    data class RemoteDoc(val id: String, val data: Map<String, Any?>, val serverUpdatedAt: Long)

    data class Page(val docs: List<RemoteDoc>, val next: Any?)

    sealed interface PushResult {
        data object Written : PushResult
        /** The server already holds a version at least as new; it is returned so it can be applied. */
        data class RemoteWins(val doc: RemoteDoc) : PushResult
    }

    /** Documents whose server commit time is at or after [sinceMillis], oldest first. */
    suspend fun pullPage(uid: String, collection: Collection, sinceMillis: Long, pageSize: Int, after: Any?): Page

    /**
     * Writes [data] unless the stored document has an `updatedAt` greater than
     * or equal to [updatedAt]. Atomic: check and write happen in one transaction.
     */
    suspend fun pushIfNewer(uid: String, collection: Collection, id: String, data: Map<String, Any?>, updatedAt: Long): PushResult

    /** Hard-deletes tombstones last edited before [beforeMillis]. Returns how many were removed. */
    suspend fun purgeTombstones(uid: String, collection: Collection, beforeMillis: Long, limit: Int): Int

    /** Deletes every document the user owns. Used by account deletion. */
    suspend fun deleteAllUserData(uid: String)
}

/** Backend errors translated into categories the sync engine can act on. */
class RemoteException(val code: Code, cause: Throwable? = null) : Exception(code.name, cause) {
    enum class Code { NETWORK, UNAUTHENTICATED, PERMISSION_DENIED, INVALID, QUOTA, INDEX_MISSING, DATABASE_MISSING, UNKNOWN }

    val retryable: Boolean get() = code == Code.NETWORK || code == Code.QUOTA || code == Code.UNKNOWN
}
