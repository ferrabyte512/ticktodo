package app.ticktodo.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.reminders.ReminderScheduler
import app.ticktodo.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val session: UserSession,
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
    private val widget: WidgetUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = session.uid.value ?: return Result.success()
        val changed = mutableSetOf<String>()
        val throttlePull = inputData.getBoolean(KEY_THROTTLE_PULL, false)
        var outcome = engine.sync(uid, inputData.getBoolean(KEY_FULL, false), throttlePull)

        // Edits made while the run was in progress are picked up by a follow-up pass.
        var extraPasses = 0
        while (outcome is SyncEngine.Outcome.Success && outcome.rejected == 0 && extraPasses < 2 && pendingRows(uid) > 0) {
            changed += outcome.changedTaskIds
            outcome = engine.sync(uid, throttlePull = throttlePull)
            extraPasses++
        }

        return when (outcome) {
            is SyncEngine.Outcome.Success -> {
                changed += outcome.changedTaskIds
                if (changed.isNotEmpty()) {
                    reminders.refresh(changed)
                    widget.update()
                }
                Result.success()
            }
            SyncEngine.Outcome.Skipped -> Result.success()
            is SyncEngine.Outcome.Failed ->
                if (outcome.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun pendingRows(uid: String): Int =
        db.taskDao().dirtyCount(uid) + db.taskListDao().dirtyCount(uid)

    companion object {
        const val KEY_FULL = "full"
        const val KEY_THROTTLE_PULL = "throttle_pull"
        /** With 30 s exponential backoff this spans roughly two hours before giving up until the next trigger. */
        const val MAX_ATTEMPTS = 8
    }
}
