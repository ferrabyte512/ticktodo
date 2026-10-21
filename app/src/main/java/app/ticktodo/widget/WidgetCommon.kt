package app.ticktodo.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.ticktodo.MainActivity
import app.ticktodo.R
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.local.TaskEntity
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.focus.FocusManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun database(): AppDatabase
    fun session(): UserSession
    fun clock(): Clock
    fun repository(): TaskRepository
    fun focus(): FocusManager
}

fun widgetEntry(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Plain data the widget UI needs, read once per update. */
data class WidgetTask(
    val id: String,
    val title: String,
    val subtitle: String?,
    val priority: Int,
    val overdue: Boolean,
)

private val PriorityColors = mapOf(3 to Color(0xFFD9483B), 2 to Color(0xFFE08A00), 1 to Color(0xFF4A7FC9))

fun TaskEntity.toWidgetTask(context: Context, today: LocalDate): WidgetTask {
    val due = dueEpochDay?.let(LocalDate::ofEpochDay)
    val parts = mutableListOf<String>()
    if (due != null) {
        val day = when {
            due == today -> context.getString(R.string.date_today)
            due == today.plusDays(1) -> context.getString(R.string.date_tomorrow)
            due == today.minusDays(1) -> context.getString(R.string.date_yesterday)
            else -> due.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM")))
        }
        val time = dueMinuteOfDay?.let {
            LocalTime.of(it / 60, it % 60).format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
        }
        parts += if (time != null) "$day · $time" else day
    }
    notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.let { parts += it.take(80) }
    return WidgetTask(id, title, parts.joinToString("  ·  ").ifBlank { null }, priority, due != null && due.isBefore(today))
}

fun openAppIntent(context: Context, action: String? = null, taskId: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        action?.let(::setAction)
        taskId?.let { putExtra(MainActivity.EXTRA_TASK_ID, it) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

@Composable
fun WidgetHeader(context: Context, title: String, subtitle: String?, showAdd: Boolean = true) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openAppIntent(context)))) {
            Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
        }
        if (showAdd) {
            Box(
                GlanceModifier
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(openAppIntent(context, MainActivity.ACTION_QUICK_ADD))),
            ) {
                Text(context.getString(R.string.widget_add), style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimary))
            }
        }
    }
}

/** One task as a rounded card: tick box, title, due and notes line, priority mark. */
@Composable
fun WidgetTaskCard(context: Context, task: WidgetTask) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(14.dp)
                .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mark = PriorityColors[task.priority]
            Box(
                GlanceModifier.width(4.dp).height(36.dp).cornerRadius(2.dp)
                    .background(if (mark != null) ColorProvider(mark) else ColorProvider(Color.Transparent)),
            ) {}
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TaskIdKey to task.id)),
            )
            Column(
                GlanceModifier.defaultWeight().clickable(
                    actionStartActivity(openAppIntent(context, MainActivity.ACTION_OPEN_TASK, task.id)),
                ),
            ) {
                Text(
                    task.title,
                    maxLines = 2,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
                task.subtitle?.let {
                    Text(
                        it,
                        maxLines = 1,
                        style = TextStyle(fontSize = 12.sp, color = if (task.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
fun WidgetSectionLabel(text: String) {
    Text(
        text,
        modifier = GlanceModifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
    )
}

@Composable
fun WidgetEmpty(text: String) {
    Box(GlanceModifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
    }
}
