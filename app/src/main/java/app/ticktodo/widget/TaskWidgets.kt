package app.ticktodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.ticktodo.R
import app.ticktodo.domain.model.TaskScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAX_ROWS = 15

/** Shared frame: rounded background, header, list or empty text. */
@Composable
private fun TaskWidgetBody(context: Context, title: String, subtitle: String?, rows: List<WidgetRow>, emptyText: String) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
    ) {
        WidgetHeader(context, title, subtitle)
        if (rows.isEmpty()) {
            WidgetEmpty(emptyText)
        } else {
            LazyColumn {
                items(rows, itemId = { it.stableId }) { row ->
                    when (row) {
                        is WidgetRow.Header -> WidgetSectionLabel(row.text)
                        is WidgetRow.Item -> WidgetTaskCard(context, row.task)
                    }
                }
            }
        }
    }
}

sealed interface WidgetRow {
    val stableId: Long
    data class Header(val text: String) : WidgetRow { override val stableId = text.hashCode().toLong() shl 1 }
    data class Item(val task: WidgetTask) : WidgetRow { override val stableId = (task.id.hashCode().toLong() shl 1) or 1 }
}

// ---------------------------------------------------------------------------------------------
// Today & overdue
// ---------------------------------------------------------------------------------------------

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntry(context)
        val uid = entry.session().uid.value
        val today = entry.clock().today()
        val tasks = if (uid == null) emptyList()
        else entry.database().taskDao().todayForWidget(uid, today.toEpochDay(), MAX_ROWS).map { it.toWidgetTask(context, today) }
        val (overdue, due) = tasks.partition { it.overdue }
        val rows = buildList {
            if (overdue.isNotEmpty()) {
                add(WidgetRow.Header(context.getString(R.string.section_overdue)))
                overdue.forEach { add(WidgetRow.Item(it)) }
                if (due.isNotEmpty()) add(WidgetRow.Header(context.getString(R.string.date_today)))
            }
            due.forEach { add(WidgetRow.Item(it)) }
        }
        val subtitle = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val empty = context.getString(if (uid == null) R.string.widget_signed_out else R.string.widget_empty)
        provideContent { GlanceTheme { TaskWidgetBody(context, context.getString(R.string.widget_title), subtitle, rows, empty) } }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---------------------------------------------------------------------------------------------
// Upcoming (next 7 days, grouped by day)
// ---------------------------------------------------------------------------------------------

class UpcomingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntry(context)
        val uid = entry.session().uid.value
        val today = entry.clock().today()
        val tasks = if (uid == null) emptyList()
        else entry.database().taskDao().upcomingForWidget(uid, today.toEpochDay(), today.plusDays(7).toEpochDay(), MAX_ROWS)
        val rows = buildList {
            tasks.groupBy { it.dueEpochDay }.forEach { (day, group) ->
                val date = LocalDate.ofEpochDay(day ?: return@forEach)
                add(WidgetRow.Header(if (date == today.plusDays(1)) context.getString(R.string.date_tomorrow) else date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))))
                // The day is already in the header, so the card subtitle shows time and notes only.
                group.forEach { add(WidgetRow.Item(it.copy(dueEpochDay = null).toWidgetTask(context, today).withTime(it, context))) }
            }
        }
        val empty = context.getString(if (uid == null) R.string.widget_signed_out else R.string.empty_upcoming_title)
        provideContent { GlanceTheme { TaskWidgetBody(context, context.getString(R.string.scope_upcoming), context.getString(R.string.widget_next_7_days), rows, empty) } }
    }
}

private fun WidgetTask.withTime(source: app.ticktodo.data.local.TaskEntity, context: Context): WidgetTask {
    val time = source.dueMinuteOfDay?.let {
        java.time.LocalTime.of(it / 60, it % 60).format(
            DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"),
        )
    } ?: return this
    return copy(subtitle = listOfNotNull(time, subtitle).joinToString("  ·  "))
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

// ---------------------------------------------------------------------------------------------
// Task list (configurable: list, tag, inbox, all, today, upcoming)
// ---------------------------------------------------------------------------------------------

class ListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scopeKey = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[SCOPE_KEY] ?: "inbox"
        val scope = TaskScope.fromKey(scopeKey)
        val entry = widgetEntry(context)
        val uid = entry.session().uid.value
        val today = entry.clock().today()
        val dao = entry.database().taskDao()
        var title = context.getString(R.string.scope_inbox)
        val tasks = if (uid == null) emptyList() else when (scope) {
            TaskScope.Today -> dao.todayForWidget(uid, today.toEpochDay(), MAX_ROWS).also { title = context.getString(R.string.scope_today) }
            TaskScope.Upcoming -> dao.upcomingForWidget(uid, today.toEpochDay(), today.plusDays(30).toEpochDay(), MAX_ROWS)
                .also { title = context.getString(R.string.scope_upcoming) }
            TaskScope.All, TaskScope.Completed -> dao.allForWidget(uid, MAX_ROWS).also { title = context.getString(R.string.scope_all) }
            TaskScope.Inbox -> dao.inboxForWidget(uid, MAX_ROWS)
            is TaskScope.InList -> {
                title = entry.database().taskListDao().get(uid, scope.listId)?.takeUnless { it.deleted }?.name
                    ?: context.getString(R.string.widget_list_missing)
                dao.inListForWidget(uid, scope.listId, MAX_ROWS)
            }
            is TaskScope.WithTag -> dao.withTagForWidget(uid, scope.tag, MAX_ROWS).also { title = "#${scope.tag}" }
        }
        val rows = tasks.map { WidgetRow.Item(it.toWidgetTask(context, today)) }
        val empty = context.getString(if (uid == null) R.string.widget_signed_out else R.string.empty_list_title)
        provideContent { GlanceTheme { TaskWidgetBody(context, title, null, rows, empty) } }
    }

    companion object {
        val SCOPE_KEY: Preferences.Key<String> = stringPreferencesKey("scope")
    }
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()
}

// ---------------------------------------------------------------------------------------------
// Shared action
// ---------------------------------------------------------------------------------------------

/** Tick box on any task widget: completes the task and refreshes every widget. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val entry = widgetEntry(context)
        if (entry.session().uid.value == null) return
        entry.repository().setCompleted(taskId, true)
        updateAllWidgets(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("task_id")
    }
}

suspend fun updateAllWidgets(context: Context) {
    TodayWidget().updateAll(context)
    UpcomingWidget().updateAll(context)
    ListWidget().updateAll(context)
    FocusWidget().updateAll(context)
}
