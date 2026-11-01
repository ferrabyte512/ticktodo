package app.ticktodo.reminders

import android.content.Context
import android.text.format.DateFormat
import app.ticktodo.R
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.repository.TaskRepository
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
        val time = ReminderMath.reminderTime(task, zone, ReminderMath.ALL_DAY_REMINDER_MINUTE) ?: return
        if (time == task.lastNotifiedTrigger) return
        val now = clock.now()
        if (time > now + EARLY_TOLERANCE_MS) {
            // The task was rescheduled after this alarm was set.
            scheduler.schedule(taskId, time)
            return
        }
        // Several tasks often share a deadline (e.g. everything due at 9:00). Their
        // alarms fire within moments of each other; handle the whole batch now, in
        // deadline order, with one sound, and let the later alarms find them done.
        val batch = dao.withReminders(uid)
            .mapNotNull { row -> ReminderMath.reminderTime(row, zone, ReminderMath.ALL_DAY_REMINDER_MINUTE)?.let { row to it } }
            .filter { (row, at) -> row.id == taskId || (at != row.lastNotifiedTrigger && at in now - BATCH_WINDOW_MS..now + EARLY_TOLERANCE_MS) }
            .sortedBy { (row, at) -> dueTimeOf(row, zone) ?: at }
        val first = batch.firstOrNull { it.first.id == taskId } ?: (task to time)
        (listOf(first) + batch.filter { it.first.id != taskId }).forEachIndexed { index, (row, at) ->
            val dueAt = dueTimeOf(row, zone) ?: at
            Notifications.showReminder(context, row, context.getString(R.string.notification_due_at, format(dueAt)), dueAt, alert = index == 0)
            dao.markNotified(uid, row.id, at)
        }
    }

    private fun dueTimeOf(task: app.ticktodo.data.local.TaskEntity, zone: java.time.ZoneId): Long? {
        val day = task.dueEpochDay ?: return null
        val minute = task.dueMinuteOfDay ?: ReminderMath.ALL_DAY_REMINDER_MINUTE
        return java.time.LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    }

    private fun format(at: Long): String = Instant.ofEpochMilli(at).atZone(clock.zone()).toLocalTime()
        .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))

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
        /** Reminders that came due this recently and have not been shown join the batch. */
        private const val BATCH_WINDOW_MS = 2 * 60_000L
    }
}
