package app.ticktodo.sync

import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.remote.RemoteException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val pending: Int = 0,
    val lastSuccessAt: Long? = null,
    val lastErrorCode: String? = null,
    val running: Boolean = false,
) {
    /** A problem worth showing; plain network failures are shown as "offline" instead. */
    val hasError: Boolean
        get() = lastErrorCode != null && lastErrorCode != RemoteException.Code.NETWORK.name

    val error: SyncError? get() = if (hasError) SyncError.from(lastErrorCode) else null
}

/** What went wrong, in terms the UI can explain. */
enum class SyncError {
    ACCESS_DENIED, SIGNED_OUT, DATABASE_MISSING, BUSY, CHANGES_REFUSED, OTHER;

    companion object {
        fun from(code: String?): SyncError = when (code) {
            RemoteException.Code.PERMISSION_DENIED.name -> ACCESS_DENIED
            RemoteException.Code.UNAUTHENTICATED.name -> SIGNED_OUT
            RemoteException.Code.DATABASE_MISSING.name -> DATABASE_MISSING
            RemoteException.Code.QUOTA.name -> BUSY
            SyncEngine.REJECTED_CODE, RemoteException.Code.INVALID.name -> CHANGES_REFUSED
            else -> OTHER
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SyncStatusRepository @Inject constructor(
    db: AppDatabase,
    session: UserSession,
    scheduler: SyncScheduler,
) {
    val status: Flow<SyncStatus> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(SyncStatus())
        else combine(
            db.syncStateDao().observe(uid),
            db.taskDao().observeDirtyCount(uid),
            db.taskListDao().observeDirtyCount(uid),
            scheduler.isRunning,
        ) { state, dirtyTasks, dirtyLists, running ->
            SyncStatus(dirtyTasks + dirtyLists, state?.lastSuccessAt, state?.lastErrorCode, running)
        }
    }
}
