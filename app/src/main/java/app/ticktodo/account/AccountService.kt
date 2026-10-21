package app.ticktodo.account

import android.app.Activity
import app.ticktodo.core.AppLog
import app.ticktodo.data.auth.AuthRepository
import app.ticktodo.data.auth.AuthResult
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.remote.RemoteException
import app.ticktodo.data.remote.RemoteStore
import app.ticktodo.focus.FocusManager
import app.ticktodo.reminders.ReminderScheduler
import app.ticktodo.sync.SyncLock
import app.ticktodo.sync.SyncScheduler
import app.ticktodo.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data class AuthFailed(val reason: AuthResult) : DeleteResult
    data object Network : DeleteResult
    data object Failed : DeleteResult
}

/**
 * Account lifecycle: sign-in, sign-out, account switching and deletion.
 *
 * Local data is wiped on sign-out and whenever a different account signs in,
 * under the same lock the sync engine uses, so one user's tasks are never
 * shown to, or uploaded for, another user.
 */
@Singleton
class AccountService @Inject constructor(
    private val auth: AuthRepository,
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val syncScheduler: SyncScheduler,
    private val reminders: ReminderScheduler,
    private val widget: WidgetUpdater,
    private val lock: SyncLock,
    private val focus: FocusManager,
) {
    /** Post-sign-in work runs from [onSignedIn], which the application calls whenever the uid changes. */
    suspend fun signIn(activity: Activity): AuthResult = auth.signIn(activity)

    /** Called once per process for a signed-in user, and again after every new sign-in. */
    suspend fun onSignedIn(uid: String) {
        lock.mutex.withLock {
            val removed = db.taskDao().deleteOtherOwners(uid) + db.taskListDao().deleteOtherOwners(uid) +
                db.focusDao().deleteOtherOwners(uid)
            if (removed > 0) AppLog.d("removed rows left by a previous account")
        }
        syncScheduler.requestSync(delaySeconds = 0)
        syncScheduler.schedulePeriodic()
        reminders.rescheduleAll()
        widget.update()
    }

    suspend fun pendingChanges(uid: String): Int =
        db.taskDao().dirtyCount(uid) + db.taskListDao().dirtyCount(uid)

    /** Signs out and removes all local data. Unsynced changes are lost; the UI warns first. */
    suspend fun signOut() {
        val uid = auth.currentUid()
        syncScheduler.cancelAll()
        focus.clear()
        lock.mutex.withLock {
            if (uid != null) reminders.cancelAllFor(uid)
            auth.signOut()
            withContext(Dispatchers.IO) { db.clearAllTables() }
        }
        widget.update()
    }

    /**
     * Deletes the cloud data, the Firebase account and the local data. The user
     * must confirm their Google account first (Firebase requires a recent sign-in).
     */
    suspend fun deleteAccount(activity: Activity): DeleteResult {
        val uid = auth.currentUid() ?: return DeleteResult.Failed
        when (val reauth = auth.reauthenticate(activity)) {
            is AuthResult.Success -> if (reauth.user.uid != uid) return DeleteResult.AuthFailed(AuthResult.WrongAccount)
            AuthResult.Network -> return DeleteResult.Network
            else -> return DeleteResult.AuthFailed(reauth)
        }
        syncScheduler.cancelAll()
        return lock.mutex.withLock {
            try {
                remote.deleteAllUserData(uid)
                auth.deleteCurrentUser()
                reminders.cancelAllFor(uid)
                focus.clear()
                withContext(Dispatchers.IO) { db.clearAllTables() }
                DeleteResult.Deleted
            } catch (e: RemoteException) {
                AppLog.w("account data deletion failed: ${e.code}")
                if (e.code == RemoteException.Code.NETWORK) DeleteResult.Network else DeleteResult.Failed
            } catch (e: Exception) {
                AppLog.e("account deletion failed", e)
                DeleteResult.Failed
            }
        }.also { widget.update() }
    }
}
