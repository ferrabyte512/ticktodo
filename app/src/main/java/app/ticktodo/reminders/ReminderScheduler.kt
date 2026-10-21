package app.ticktodo.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import app.ticktodo.core.AppLog
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one alarm per task reminder.
 *
 * Exact alarms are used when the user has allowed them ("Alarms & reminders"
 * special access). Otherwise an inexact alarm is used, which Android may delay;
 * the Settings screen explains this and links to the permission.
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
    private val settings: SettingsRepository,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val dao get() = db.taskDao()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-evaluates the reminders of specific tasks after they changed. */
    suspend fun refresh(taskIds: Collection<String>) {
        val uid = session.uid.value ?: return
        val defaultMinute = settings.current().defaultReminderMinute
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
        val defaultMinute = settings.current().defaultReminderMinute
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
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
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
