package app.ticktodo.reminders

import android.content.Context
import android.text.format.DateFormat
import app.ticktodo.R
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** What happens when a reminder alarm fires or a notification action is tapped. */
@Singleton
class ReminderHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val session: UserSession,
    private val clock: Clock,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val repository: TaskRepository,
) {
    private val dao get() = db.taskDao()

    suspend fun onAlarm(taskId: String) {
        // Signed out, or the alarm belongs to another account: show nothing.
        val uid = session.uid.value ?: return
        val task = dao.get(uid, taskId) ?: return
        if (task.deleted || task.completed) return
        val zone = clock.zone()
        val time = ReminderMath.reminderTime(task, zone, settings.current().defaultReminderMinute) ?: return
        if (time == task.lastNotifiedTrigger) return
        val now = clock.now()
        if (time > now + EARLY_TOLERANCE_MS) {
            // The task was rescheduled after this alarm was set.
            scheduler.schedule(taskId, time)
            return
        }
        val timeText = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
            .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
        Notifications.showReminder(context, task, context.getString(R.string.notification_due_at, timeText))
        dao.markNotified(uid, taskId, time)
    }

    suspend fun onComplete(taskId: String) {
        Notifications.cancel(context, taskId)
        if (session.uid.value == null) return
        repository.setCompleted(taskId, true)
    }

    suspend fun onSnooze(taskId: String) {
        Notifications.cancel(context, taskId)
        val uid = session.uid.value ?: return
        val until = clock.now() + SNOOZE_MS
        dao.setSnooze(uid, taskId, until)
        scheduler.schedule(taskId, until)
    }

    companion object {
        const val SNOOZE_MS = 15L * 60 * 1000
        private const val EARLY_TOLERANCE_MS = 60_000L
    }
}
