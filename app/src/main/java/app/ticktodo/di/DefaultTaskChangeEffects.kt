package app.ticktodo.di

import app.ticktodo.core.AppLog
import app.ticktodo.data.repository.TaskChangeEffects
import app.ticktodo.reminders.ReminderScheduler
import app.ticktodo.sync.SyncScheduler
import app.ticktodo.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTaskChangeEffects @Inject constructor(
    private val reminders: ReminderScheduler,
    private val sync: SyncScheduler,
    private val widget: WidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) : TaskChangeEffects {

    override fun tasksChanged(taskIds: Collection<String>) {
        val ids = taskIds.toList()
        scope.launch {
            try {
                reminders.refresh(ids)
            } catch (e: Exception) {
                AppLog.e("reminder refresh failed", e)
            }
        }
        widget.update()
        sync.requestSync()
    }

    override fun listsChanged() {
        sync.requestSync()
    }
}
