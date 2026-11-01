package app.ticktodo.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ticktodo.MainActivity
import app.ticktodo.R
import app.ticktodo.data.local.TaskEntity

object Notifications {
    /**
     * Reminders ring with the phone's alarm tone and vibrate, so they are heard
     * like a clock alarm rather than a quiet notification. The channel id
     * carries a version because Android never changes an existing channel's
     * sound: the old "reminders" channel is removed on upgrade.
     */
    const val CHANNEL_REMINDERS = "reminders_alarm"
    private const val LEGACY_CHANNEL = "reminders"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            // Task titles are hidden on a secure lock screen; see showReminder.
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            setSound(
                alarmTone,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 600)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** All reminders share one group, so several due tasks collapse into a single expandable entry. */
    private const val GROUP_REMINDERS = "app.ticktodo.reminders"
    private const val SUMMARY_ID = 7_100
    private const val EXTRA_DUE_AT = "app.ticktodo.due_at"
    private const val SUMMARY_LINES = 6

    fun notificationId(taskId: String): Int = taskId.hashCode()

    /**
     * Shows one task's reminder. [dueAt] orders reminders by deadline within the
     * group. [alert] is false for reminders that fire together with another one,
     * so a batch of due tasks makes one sound instead of several.
     */
    fun showReminder(context: Context, task: TaskEntity, subtitle: String, dueAt: Long, alert: Boolean = true) {
        if (!canPost(context)) return
        val id = notificationId(task.id)

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_TASK)
                .putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // What a secure lock screen shows instead of the task title.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_public_title))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            // Wakes the screen and shows over the lock screen like an alarm. Android 14+
            // may ask the user to allow full-screen notifications for the app.
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setGroup(GROUP_REMINDERS)
            // Zero-padded so the text order matches the deadline order.
            .setSortKey(dueAt.coerceAtLeast(0).toString().padStart(15, '0'))
            .setWhen(dueAt)
            .setShowWhen(true)
            .setSilent(!alert)
            .addExtras(android.os.Bundle().apply { putLong(EXTRA_DUE_AT, dueAt) })
            .addAction(0, context.getString(R.string.action_complete), actionIntent(context, ReminderActionReceiver.ACTION_COMPLETE, task.id))
            .addAction(0, context.getString(R.string.action_snooze), actionIntent(context, ReminderActionReceiver.ACTION_SNOOZE, task.id))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call; nothing else to do.
        }
        refreshSummary(context)
    }

    fun cancel(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
        refreshSummary(context)
    }

    /**
     * Keeps the group summary in step with the reminders on screen: with two or
     * more it lists them, earliest deadline first; with fewer it is removed.
     */
    fun refreshSummary(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val shown = manager.activeNotifications
            .filter { it.notification.group == GROUP_REMINDERS && it.id != SUMMARY_ID }
            .sortedBy { it.notification.extras.getLong(EXTRA_DUE_AT, Long.MAX_VALUE) }
        if (shown.size < 2 || !canPost(context)) {
            manager.cancel(SUMMARY_ID)
            return
        }
        val title = context.resources.getQuantityString(R.plurals.notification_group_title, shown.size, shown.size)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        shown.take(SUMMARY_LINES).forEach { sbn ->
            val extras = sbn.notification.extras
            style.addLine("${extras.getCharSequence(android.app.Notification.EXTRA_TEXT) ?: ""}  ${extras.getCharSequence(android.app.Notification.EXTRA_TITLE) ?: ""}")
        }
        if (shown.size > SUMMARY_LINES) style.setSummaryText(context.getString(R.string.notification_group_more, shown.size - SUMMARY_LINES))
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .build()
        val summary = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(shown.first().notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_REMINDERS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            // See showReminder.
        }
    }

    private fun actionIntent(context: Context, action: String, taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId(taskId),
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .setData(android.net.Uri.Builder().scheme("ticktodo").authority("action").appendPath(taskId).build())
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
