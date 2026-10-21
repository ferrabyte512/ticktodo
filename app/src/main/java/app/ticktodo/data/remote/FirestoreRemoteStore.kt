package app.ticktodo.data.remote

import app.ticktodo.data.remote.RemoteStore.Collection
import app.ticktodo.data.remote.RemoteStore.Page
import app.ticktodo.data.remote.RemoteStore.PushResult
import app.ticktodo.data.remote.RemoteStore.RemoteDoc
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout:
 *   users/{uid}/tasks/{taskId}
 *   users/{uid}/lists/{listId}
 * Access is limited to the owner by firestore.rules.
 */
@Singleton
class FirestoreRemoteStore @Inject constructor(
    private val firebase: FirebaseProvider,
) : RemoteStore {

    private fun col(uid: String, c: Collection): CollectionReference {
        require(uid.isNotBlank())
        return firebase.firestore.collection("users").document(uid).collection(c.path)
    }

    override suspend fun pullPage(uid: String, collection: Collection, sinceMillis: Long, pageSize: Int, after: Any?): Page = call {
        var query = col(uid, collection)
            .whereGreaterThanOrEqualTo(SERVER_UPDATED_AT, sinceMillis.toTimestamp())
            .orderBy(SERVER_UPDATED_AT)
            .limit(pageSize.toLong())
        if (after is DocumentSnapshot) query = query.startAfter(after)
        val snap = query.get(Source.SERVER).await()
        val docs = snap.documents.mapNotNull { it.toRemoteDoc() }
        Page(docs, if (snap.size() < pageSize) null else snap.documents.last())
    }

    override suspend fun pushIfNewer(
        uid: String,
        collection: Collection,
        id: String,
        data: Map<String, Any?>,
        updatedAt: Long,
    ): PushResult = call {
        val ref = col(uid, collection).document(id)
        firebase.firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val remoteUpdatedAt = snap.getLong("updatedAt")
            val remote = snap.toRemoteDoc()
            if (remote != null && remoteUpdatedAt != null && remoteUpdatedAt >= updatedAt) {
                return@runTransaction PushResult.RemoteWins(remote)
            }
            tx.set(ref, data + (SERVER_UPDATED_AT to FieldValue.serverTimestamp()))
            PushResult.Written
        }.await()
    }

    override suspend fun purgeTombstones(uid: String, collection: Collection, beforeMillis: Long, limit: Int): Int = call {
        val snap = col(uid, collection)
            .whereEqualTo("deleted", true)
            .whereLessThan("updatedAt", beforeMillis)
            .limit(limit.toLong())
            .get(Source.SERVER)
            .await()
        if (snap.isEmpty) return@call 0
        val batch = firebase.firestore.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        snap.size()
    }

    override suspend fun deleteAllUserData(uid: String): Unit = call {
        for (collection in Collection.entries) {
            while (true) {
                val snap = col(uid, collection).limit(DELETE_BATCH.toLong()).get(Source.SERVER).await()
                if (snap.isEmpty) break
                val batch = firebase.firestore.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        firebase.firestore.collection("users").document(uid).delete().await()
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        throw RemoteException(
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED -> RemoteException.Code.NETWORK
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> RemoteException.Code.UNAUTHENTICATED
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> RemoteException.Code.PERMISSION_DENIED
                FirebaseFirestoreException.Code.INVALID_ARGUMENT -> RemoteException.Code.INVALID
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> RemoteException.Code.QUOTA
                FirebaseFirestoreException.Code.FAILED_PRECONDITION -> RemoteException.Code.INDEX_MISSING
                // Returned when the project has no Cloud Firestore database yet.
                FirebaseFirestoreException.Code.NOT_FOUND -> RemoteException.Code.DATABASE_MISSING
                else -> RemoteException.Code.UNKNOWN
            },
            e,
        )
    } catch (e: FirebaseNetworkException) {
        throw RemoteException(RemoteException.Code.NETWORK, e)
    } catch (e: IOException) {
        throw RemoteException(RemoteException.Code.NETWORK, e)
    }

    private fun DocumentSnapshot.toRemoteDoc(): RemoteDoc? {
        if (!exists()) return null
        val ts = getTimestamp(SERVER_UPDATED_AT) ?: return null
        return RemoteDoc(id, data.orEmpty(), ts.toDate().time)
    }

    private fun Long.toTimestamp(): Timestamp =
        Timestamp(this / 1_000, ((this % 1_000) * 1_000_000).toInt())

    private companion object {
        const val SERVER_UPDATED_AT = "serverUpdatedAt"
        const val DELETE_BATCH = 400
    }
}
