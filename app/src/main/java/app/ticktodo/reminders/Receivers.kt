package app.ticktodo.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ticktodo.core.AppLog
import app.ticktodo.di.ApplicationScope
import app.ticktodo.focus.FocusManager
import app.ticktodo.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives reminder alarms. Not exported: only our own PendingIntents reach it. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: ReminderHandler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                handler.onAlarm(taskId)
            } catch (e: Exception) {
                AppLog.e("reminder handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.ticktodo.action.REMINDER"
        const val EXTRA_TASK_ID = "task_id"
    }
}

/** Complete / Snooze buttons on the reminder notification. Not exported. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: ReminderHandler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> handler.onComplete(taskId)
                    ACTION_SNOOZE -> handler.onSnooze(taskId)
                }
            } catch (e: Exception) {
                AppLog.e("notification action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "app.ticktodo.action.COMPLETE"
        const val ACTION_SNOOZE = "app.ticktodo.action.SNOOZE"
    }
}

/**
 * Rebuilds alarms when the system clears or invalidates them. Not exported:
 * these protected broadcasts are only sent by the system.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var widget: WidgetUpdater
    @Inject lateinit var focus: FocusManager
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll(catchUpMissed = action == Intent.ACTION_BOOT_COMPLETED)
                focus.restore()
                widget.update()
            } catch (e: Exception) {
                AppLog.e("rescheduling failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
