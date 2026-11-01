package app.ticktodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import app.ticktodo.R
import app.ticktodo.data.local.TaskEntity
import app.ticktodo.domain.model.TaskScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAX_ROWS = 15

/**
 * What a task widget shows. Built from live database streams that are
 * collected inside the widget's composition, so while Android keeps a widget
 * session open every change (a completed task, a new list choice) appears at
 * once instead of showing data captured when the session started.
 */
data class WidgetContentState(
    val signedIn: Boolean,
    val title: String,
    val subtitle: String?,
    val rows: List<WidgetRow>,
)

sealed interface WidgetRow {
    val stableId: Long
    data class Header(val text: String) : WidgetRow { override val stableId = text.hashCode().toLong() shl 1 }
    data class Item(val task: WidgetTask) : WidgetRow { override val stableId = (task.id.hashCode().toLong() shl 1) or 1 }
}

/** Shared frame: rounded background, header, list or empty text. */
@Composable
private fun TaskWidgetBody(context: Context, state: WidgetContentState?, emptyText: Int) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
    ) {
        if (state == null) return@Column
        WidgetHeader(context, state.title, state.subtitle, showAdd = state.signedIn)
        if (state.rows.isEmpty()) {
            WidgetEmpty(context.getString(if (state.signedIn) emptyText else R.string.widget_signed_out))
        } else {
            LazyColumn {
                items(state.rows, itemId = { it.stableId }) { row ->
                    when (row) {
                        is WidgetRow.Header -> WidgetSectionLabel(row.text)
                        is WidgetRow.Item -> WidgetTaskCard(context, row.task)
                    }
                }
            }
        }
    }
}

/** Live task rows for the signed-in user; emits an empty list when signed out. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun userTasks(context: Context, query: (String) -> Flow<List<TaskEntity>>): Flow<Pair<Boolean, List<TaskEntity>>> =
    widgetEntry(context).session().uid.flatMapLatest { uid ->
        if (uid == null) flowOf(false to emptyList()) else query(uid).map { true to it }
    }

// ---------------------------------------------------------------------------------------------
// Today & overdue
// ---------------------------------------------------------------------------------------------

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntry(context)
        val today = entry.clock().today()
        val dao = entry.database().taskDao()
        val title = context.getString(R.string.widget_title)
        val subtitle = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val flow = userTasks(context) { uid -> dao.widgetToday(uid, today.toEpochDay(), MAX_ROWS) }.map { (signedIn, rows) ->
            val tasks = rows.map { it.toWidgetTask(context, today) }
            val (overdue, due) = tasks.partition { it.overdue }
            WidgetContentState(
                signedIn, title, subtitle,
                buildList {
                    if (overdue.isNotEmpty()) {
                        add(WidgetRow.Header(context.getString(R.string.section_overdue)))
                        overdue.forEach { add(WidgetRow.Item(it)) }
                        if (due.isNotEmpty()) add(WidgetRow.Header(context.getString(R.string.date_today)))
                    }
                    due.forEach { add(WidgetRow.Item(it)) }
                },
            )
        }
        provideContent {
            val state by remember { flow }.collectAsState(null)
            GlanceTheme { TaskWidgetBody(context, state, R.string.widget_empty) }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---------------------------------------------------------------------------------------------
// Upcoming (next 7 days, grouped by day)
// ---------------------------------------------------------------------------------------------

class UpcomingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntry(context)
        val today = entry.clock().today()
        val dao = entry.database().taskDao()
        val flow = userTasks(context) { uid ->
            dao.widgetUpcoming(uid, today.toEpochDay(), today.plusDays(7).toEpochDay(), MAX_ROWS)
        }.map { (signedIn, tasks) ->
            WidgetContentState(
                signedIn,
                context.getString(R.string.scope_upcoming),
                context.getString(R.string.widget_next_7_days),
                buildList {
                    tasks.groupBy { it.dueEpochDay }.forEach { (day, group) ->
                        val date = LocalDate.ofEpochDay(day ?: return@forEach)
                        add(
                            WidgetRow.Header(
                                if (date == today.plusDays(1)) context.getString(R.string.date_tomorrow)
                                else date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                            ),
                        )
                        // The day is already in the header, so the card shows time and notes only.
                        group.forEach { add(WidgetRow.Item(it.copy(dueEpochDay = null).toWidgetTask(context, today).withTime(it, context))) }
                    }
                },
            )
        }
        provideContent {
            val state by remember { flow }.collectAsState(null)
            GlanceTheme { TaskWidgetBody(context, state, R.string.empty_upcoming_title) }
        }
    }
}

private fun WidgetTask.withTime(source: TaskEntity, context: Context): WidgetTask {
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
    override val sizeMode: SizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntry(context)
        val today = entry.clock().today()
        val dao = entry.database().taskDao()
        val listDao = entry.database().taskListDao()

        fun flowFor(scopeKey: String): Flow<WidgetContentState> {
            val scope = TaskScope.fromKey(scopeKey)
            return entry.session().uid.flatMapLatest { uid ->
                if (uid == null) return@flatMapLatest flowOf(WidgetContentState(false, context.getString(R.string.app_name), null, emptyList()))
                val tasks: Flow<List<TaskEntity>> = when (scope) {
                    TaskScope.Today -> dao.widgetToday(uid, today.toEpochDay(), MAX_ROWS)
                    TaskScope.Upcoming -> dao.widgetUpcoming(uid, today.toEpochDay(), today.plusDays(30).toEpochDay(), MAX_ROWS)
                    TaskScope.All, TaskScope.Completed -> dao.widgetAll(uid, MAX_ROWS)
                    TaskScope.Inbox -> dao.widgetInbox(uid, MAX_ROWS)
                    is TaskScope.InList -> dao.widgetInList(uid, scope.listId, MAX_ROWS)
                    is TaskScope.WithTag -> dao.widgetWithTag(uid, scope.tag, MAX_ROWS)
                }
                val title: Flow<String> = when (scope) {
                    TaskScope.Today -> flowOf(context.getString(R.string.scope_today))
                    TaskScope.Upcoming -> flowOf(context.getString(R.string.scope_upcoming))
                    TaskScope.All, TaskScope.Completed -> flowOf(context.getString(R.string.scope_all))
                    TaskScope.Inbox -> flowOf(context.getString(R.string.scope_inbox))
                    is TaskScope.InList -> listDao.observe(uid, scope.listId).map {
                        it?.takeUnless { l -> l.deleted }?.name ?: context.getString(R.string.widget_list_missing)
                    }
                    is TaskScope.WithTag -> flowOf("#${scope.tag}")
                }
                combine(tasks, title) { rows, t ->
                    WidgetContentState(true, t, null, rows.map { WidgetRow.Item(it.toWidgetTask(context, today)) })
                }
            }
        }

        provideContent {
            // Read inside composition, so a new choice from the configuration screen applies at once.
            val scopeKey = currentState(SCOPE_KEY) ?: "inbox"
            val state by remember(scopeKey) { flowFor(scopeKey) }.collectAsState(null)
            GlanceTheme { TaskWidgetBody(context, state, R.string.empty_list_title) }
        }
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
