package app.ticktodo.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.ticktodo.MainActivity
import app.ticktodo.R
import app.ticktodo.domain.focus.FocusPhase
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.domain.focus.FocusTransition
import app.ticktodo.reminders.Notifications

object FocusNotifications {
    const val CHANNEL_TIMER = "focus_timer"
    const val CHANNEL_ALERTS = "focus_alerts"
    private const val ONGOING_ID = 7_100
    private const val ALERT_ID = 7_101

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, context.getString(R.string.channel_focus_timer), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_focus_timer_description)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_focus_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_focus_alerts_description)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
        )
    }

    fun phaseLabel(context: Context, phase: FocusPhase): String = context.getString(
        when (phase) {
            FocusPhase.FOCUS -> R.string.focus_phase_focus
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> R.string.focus_phase_short_break
        },
    )

    /** The ongoing countdown. The system updates the chronometer, so no service or polling is needed. */
    fun showOngoing(context: Context, state: FocusState?, now: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (state == null) {
            nm.cancel(ONGOING_ID)
            return
        }
        if (!Notifications.canPost(context)) return
        val roundText = context.getString(R.string.focus_round, state.round)
        val remaining = state.remainingMs(now)
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${phaseLabel(context, state.phase)} · ${state.taskTitle}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_TIMER)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.focus_public_title))
                    .build(),
            )
            .setContentIntent(openFocus(context))
        if (state.running) {
            builder.setContentText(roundText)
                .setWhen(state.phaseEndsAt!!)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, context.getString(R.string.focus_pause), action(context, FocusReceiver.ACTION_PAUSE))
        } else {
            builder.setContentText(context.getString(R.string.focus_paused_left, formatMs(remaining)) + " · " + roundText)
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.focus_resume), action(context, FocusReceiver.ACTION_RESUME))
        }
        // Android shows at most three buttons; Skip stays available on the focus screen.
        builder.addAction(0, context.getString(R.string.focus_extend), action(context, FocusReceiver.ACTION_EXTEND))
            .addAction(0, context.getString(R.string.focus_stop), action(context, FocusReceiver.ACTION_STOP))
        try {
            nm.notify(ONGOING_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission revoked; the in-app timer still works.
        }
    }

    /** "Focus done — break started" and similar, with sound. */
    fun showPhaseAlert(context: Context, transition: FocusTransition) {
        if (!Notifications.canPost(context)) return
        val next = transition.state
        val title = when {
            transition.sessionFinished -> context.getString(R.string.focus_alert_session_done)
            transition.finishedPhase == FocusPhase.FOCUS -> context.getString(R.string.focus_alert_focus_done)
            else -> context.getString(R.string.focus_alert_break_done)
        }
        val text = when {
            next == null -> context.getString(R.string.focus_alert_session_done_body)
            next.running -> context.getString(R.string.focus_alert_started, phaseLabel(context, next.phase))
            else -> context.getString(R.string.focus_alert_tap_to_start, phaseLabel(context, next.phase))
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .setContentIntent(openFocus(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ALERT_ID, n)
        } catch (e: SecurityException) {
            // Ignored; see showOngoing.
        }
    }

    private fun openFocus(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        ONGOING_ID,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, FocusReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun formatMs(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
