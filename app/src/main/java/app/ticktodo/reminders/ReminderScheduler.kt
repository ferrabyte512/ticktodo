package app.ticktodo.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import app.ticktodo.MainActivity
import app.ticktodo.core.AppLog
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one alarm per task reminder.
 *
 * With "Alarms & reminders" access the reminder is set as an alarm clock, the
 * same kind the Clock app uses: it fires at the exact time even in battery
 * saver or Doze, and the system shows the alarm icon in the status bar for the
 * next one. Without that access an inexact alarm is used, which Android may
 * delay; the Settings screen explains this and links to the permission.
 *
 * Alarms are re-created after reboot, app update, time or time-zone change and
 * permission change (see SystemEventsReceiver). An alarm that fires for a task
 * that changed in the meantime is ignored by [ReminderHandler].
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val session: UserSession,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val dao get() = db.taskDao()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-evaluates the reminders of specific tasks after they changed. */
    suspend fun refresh(taskIds: Collection<String>) {
        val uid = session.uid.value ?: return
        val defaultMinute = ReminderMath.ALL_DAY_REMINDER_MINUTE
        val now = clock.now()
        val rows = dao.getMany(uid, taskIds).associateBy { it.id }
        for (id in taskIds) {
            val row = rows[id]
            if (row == null) {
                cancel(id)
                continue
            }
            when (val plan = ReminderMath.plan(row, now, clock.zone(), defaultMinute, catchUpMissed = false)) {
                ReminderMath.Plan.None -> cancel(id)
                is ReminderMath.Plan.At -> schedule(id, plan.triggerAt)
            }
        }
    }

    /** Rebuilds every alarm for the signed-in user. */
    suspend fun rescheduleAll(catchUpMissed: Boolean = false) {
        val uid = session.uid.value ?: return
        val defaultMinute = ReminderMath.ALL_DAY_REMINDER_MINUTE
        val now = clock.now()
        val scheduled = HashSet<String>()
        for (row in dao.withReminders(uid)) {
            when (val plan = ReminderMath.plan(row, now, clock.zone(), defaultMinute, catchUpMissed)) {
                ReminderMath.Plan.None -> Unit
                is ReminderMath.Plan.At -> {
                    schedule(row.id, plan.triggerAt)
                    scheduled += row.id
                }
            }
        }
        dao.idsWithAnyReminder(uid).filterNot(scheduled::contains).forEach(::cancel)
    }

    /** Cancels every alarm and notification of [uid]; used on sign-out and account deletion. */
    suspend fun cancelAllFor(uid: String) {
        dao.idsWithAnyReminder(uid).forEach(::cancel)
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun schedule(taskId: String, triggerAt: Long) {
        val pi = alarmIntent(taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canScheduleExact()) {
                // Tapping the status-bar alarm icon opens the task.
                val show = PendingIntent.getActivity(
                    context,
                    taskId.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_OPEN_TASK)
                        .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // Exact-alarm access was revoked between the check and the call.
            AppLog.w("exact alarm refused, using inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(taskId: String) {
        alarmIntent(taskId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(taskId: String, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            // The data URI makes each task's PendingIntent distinct; the intent is explicit.
            .setData(Uri.Builder().scheme("ticktodo").authority("reminder").appendPath(taskId).build())
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
