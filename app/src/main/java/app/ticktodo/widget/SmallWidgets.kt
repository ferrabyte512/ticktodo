package app.ticktodo.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.ticktodo.MainActivity
import app.ticktodo.R
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.focus.FocusNotifications
import app.ticktodo.focus.FocusReceiver
import java.time.Instant
import java.time.format.DateTimeFormatter

/** One-tap capture: opens the app straight into quick add. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp)
                        .clickable(actionStartActivity(openAppIntent(context, MainActivity.ACTION_QUICK_ADD))),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            ImageProvider(R.drawable.ic_widget_add),
                            context.getString(R.string.action_add_task),
                            GlanceModifier.size(32.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        )
                        Text(
                            context.getString(R.string.action_add_task),
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimaryContainer),
                        )
                    }
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

/**
 * Focus timer at a glance. Widgets cannot tick every second without draining
 * the battery, so this shows when the current phase ends; the notification and
 * the in-app screen show the live countdown.
 */
class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetEntry(context).focus().state.value
        val now = System.currentTimeMillis()
        provideContent { GlanceTheme { FocusContent(context, state, now) } }
    }

    @androidx.compose.runtime.Composable
    private fun FocusContent(context: Context, state: FocusState?, now: Long) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity(openAppIntent(context, MainActivity.ACTION_OPEN_FOCUS))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == null) {
                Text(context.getString(R.string.focus_title), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface))
                Text(context.getString(R.string.widget_focus_idle), style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
                return@Column
            }
            Text(
                FocusNotifications.phaseLabel(context, state.phase),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlanceTheme.colors.primary),
            )
            Text(state.taskTitle, maxLines = 1, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface))
            val detail = if (state.running) {
                val end = Instant.ofEpochMilli(state.phaseEndsAt!!).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                context.getString(R.string.widget_focus_until, end.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")))
            } else {
                context.getString(R.string.focus_paused_left, FocusNotifications.formatMs(state.remainingMs(now)))
            }
            Text(detail, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
            Spacer(GlanceModifier.size(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                WidgetButton(
                    context.getString(if (state.running) R.string.focus_pause else R.string.focus_resume),
                    focusIntent(context, if (state.running) FocusReceiver.ACTION_PAUSE else FocusReceiver.ACTION_RESUME),
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetButton(context.getString(R.string.focus_stop), focusIntent(context, FocusReceiver.ACTION_STOP))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WidgetButton(label: String, intent: Intent) {
        Box(
            GlanceModifier.background(GlanceTheme.colors.secondaryContainer).cornerRadius(16.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clickable(actionSendBroadcast(intent)),
        ) {
            Text(label, style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSecondaryContainer))
        }
    }

    private fun focusIntent(context: Context, action: String) = Intent(context, FocusReceiver::class.java).setAction(action)
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
