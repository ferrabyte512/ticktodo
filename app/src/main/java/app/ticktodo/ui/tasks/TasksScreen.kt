package app.ticktodo.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import app.ticktodo.ui.common.rememberReorderState
import app.ticktodo.ui.common.reorderItem
import app.ticktodo.ui.common.reorderHandle
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.ticktodo.R
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.domain.model.TaskSort
import app.ticktodo.ui.common.EmptyState
import app.ticktodo.ui.common.ListBadge
import app.ticktodo.ui.common.SectionTitle
import app.ticktodo.ui.common.StatusStrip
import app.ticktodo.ui.common.TaskRow
import app.ticktodo.ui.common.formatDate
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onOpenTask: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFocus: () -> Unit,
    onBack: (() -> Unit)?,
    openQuickAdd: Boolean = false,
    onQuickAddConsumed: () -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val focusTaskId by viewModel.focusTaskId.collectAsStateWithLifecycle()
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val scope = viewModel.scope
    var filter by rememberSaveable { mutableStateOf(TodayFilter.ALL) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val coroutines = rememberCoroutineScope()

    LaunchedEffect(openQuickAdd) {
        if (openQuickAdd) {
            showQuickAdd = true
            onQuickAddConsumed()
        }
    }
    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    // "My order": while a card is dragged (and until the saved order arrives) the
    // screen shows a local copy of the sections that the drag rearranges.
    val manual = state.sort == TaskSort.MANUAL && !state.selectionMode && scope != TaskScope.Completed
    val listState = rememberLazyListState()
    var localSections by remember { mutableStateOf<List<TaskSection>?>(null) }
    LaunchedEffect(state.sections) { localSections = null }
    val reorder = rememberReorderState(listState) { from, to ->
        val sections = localSections ?: state.sections
        val section = sections.firstOrNull { s -> s.items.any { it.task.id == from } && s.items.any { it.task.id == to } }
            ?: return@rememberReorderState false
        val items = section.items.toMutableList()
        val fromIndex = items.indexOfFirst { it.task.id == from }
        val toIndex = items.indexOfFirst { it.task.id == to }
        items.add(toIndex, items.removeAt(fromIndex))
        localSections = sections.map { if (it.key == section.key) it.copy(items = items) else it }
        true
    }
    val drop by rememberUpdatedState { key: Any ->
        val section = localSections?.firstOrNull { s -> s.items.any { it.task.id == key } }
        if (section != null) viewModel.reorder(section.items, key as String) else localSections = null
    }
    val onDrop = remember { { key: Any -> drop(key) } }
    val shownSections = localSections ?: state.sections
    // Screen-reader alternative to dragging: move one place up or down.
    fun moveBy(section: TaskSection, id: String, delta: Int) {
        val items = section.items.toMutableList()
        val from = items.indexOfFirst { it.task.id == id }
        val to = from + delta
        if (from < 0 || to !in items.indices) return
        items.add(to, items.removeAt(from))
        localSections = shownSections.map { if (it.key == section.key) it.copy(items = items) else it }
        viewModel.reorder(items, id)
    }
    val moveUpLabel = stringResource(R.string.a11y_move_up)
    val moveDownLabel = stringResource(R.string.a11y_move_down)
    val dragLabel = stringResource(R.string.a11y_drag_to_reorder)

    val title = when (scope) {
        TaskScope.Today -> stringResource(R.string.scope_today)
        TaskScope.Upcoming -> stringResource(R.string.scope_upcoming)
        TaskScope.Inbox -> stringResource(R.string.scope_inbox)
        TaskScope.All -> stringResource(R.string.scope_all)
        TaskScope.Completed -> stringResource(R.string.scope_completed)
        is TaskScope.InList -> state.lists.firstOrNull { it.id == scope.listId }?.name ?: ""
        is TaskScope.WithTag -> "#${scope.tag}"
    }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    lists = state.lists.map { it.id to it.name },
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onComplete = viewModel::completeSelected,
                    onDelete = viewModel::deleteSelected,
                    onPriority = viewModel::setPriorityForSelected,
                    onMove = viewModel::moveSelected,
                    showComplete = scope != TaskScope.Completed,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(title)
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                        if (scope != TaskScope.Completed) SortMenu(state.sort, viewModel::setSort)
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.AccountCircle, stringResource(R.string.settings_title)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode && scope != TaskScope.Completed) {
                ExtendedFloatingActionButton(
                    onClick = { showQuickAdd = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.action_add_task)) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = status.sync.running,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                item(key = "status") {
                    StatusStrip(
                        offline = status.offline,
                        sync = status.sync,
                        onRetry = viewModel::refresh,
                        onDetails = onOpenSettings,
                    )
                }
                if (scope == TaskScope.Today) {
                    item(key = "date") {
                        Text(
                            state.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    item(key = "dashboard") {
                        val overdue = shownSections.firstOrNull { it.header == SectionHeader.Overdue }?.items?.size ?: 0
                        val due = shownSections.firstOrNull { it.header == SectionHeader.Today }?.items?.size ?: 0
                        TodayDashboard(done = state.completedToday, remaining = due, overdue = overdue, filter = filter, onFilter = { filter = it })
                    }
                }
                if (scope == TaskScope.Upcoming) {
                    item(key = "dates") {
                        val days = shownSections.mapNotNull { (it.header as? SectionHeader.Day)?.date }.toSet()
                        DateStrip(
                            today = state.today,
                            daysWithTasks = days,
                            selected = pickedDay,
                            onPick = { day ->
                                pickedDay = day
                                // Items before the sections: status, this strip, maybe the empty state.
                                var index = 2 + if (state.isEmpty) 1 else 0
                                for (section in shownSections) {
                                    if ((section.header as? SectionHeader.Day)?.date == day) break
                                    index += (if (section.header != null) 1 else 0) + section.items.size
                                }
                                coroutines.launch { listState.animateScrollToItem(index) }
                            },
                        )
                    }
                }
                if (state.isEmpty) {
                    item(key = "empty") { EmptyFor(scope) }
                }
                val visibleSections = if (scope != TaskScope.Today) shownSections else when (filter) {
                    TodayFilter.ALL -> shownSections
                    TodayFilter.TODAY -> shownSections.filter { it.header == SectionHeader.Today }
                    TodayFilter.OVERDUE -> shownSections.filter { it.header == SectionHeader.Overdue }
                }
                visibleSections.forEach { section ->
                    section.header?.let { header ->
                        item(key = "h-${section.key}") {
                            when (header) {
                                SectionHeader.Overdue -> SectionTitle(stringResource(R.string.section_overdue), LocalExtraColors.current.overdue)
                                SectionHeader.Today -> SectionTitle(stringResource(R.string.date_today))
                                is SectionHeader.Day -> SectionTitle(formatDate(header.date, state.today))
                            }
                        }
                    }
                    items(section.items, key = { it.task.id }) { item ->
                        val list = item.task.listId?.let { id -> state.lists.firstOrNull { it.id == id } }
                        TaskRow(
                            item = item,
                            today = state.today,
                            list = list?.takeIf { scope !is TaskScope.InList }?.let { ListBadge(it.name, it.colorIndex) },
                            selectionMode = state.selectionMode,
                            selected = item.task.id in state.selection,
                            onToggleComplete = { viewModel.toggleComplete(item.task.id, item.task.isCompleted) },
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(item.task.id) else onOpenTask(item.task.id)
                            },
                            onLongClick = { viewModel.toggleSelection(item.task.id) },
                            onDelete = { viewModel.delete(listOf(item.task.id)) },
                            modifier = if (reorder.draggingKey == item.task.id) Modifier.reorderItem(reorder, item.task.id) else Modifier.animateItem(),
                            onStartFocus = if (item.task.focusPlan != null) ({
                                viewModel.startFocus(item.task)
                                onOpenFocus()
                            }) else null,
                            focusing = item.task.id == focusTaskId,
                            dragHandle = if (manual && !item.task.isCompleted) ({
                                Icon(
                                    Icons.Outlined.DragHandle,
                                    contentDescription = "$dragLabel: ${item.task.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(48.dp)
                                        .padding(12.dp)
                                        .reorderHandle(reorder, item.task.id, onDrop),
                                )
                            }) else null,
                            extraActions = if (manual) listOf(
                                CustomAccessibilityAction(moveUpLabel) { moveBy(section, item.task.id, -1); true },
                                CustomAccessibilityAction(moveDownLabel) { moveBy(section, item.task.id, 1); true },
                            ) else emptyList(),
                        )
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            defaults = viewModel.quickAddDefaults(state.today),
            today = state.today,
            lists = state.lists,
            onAdd = { viewModel.quickAdd(it) },
            onAddWithDetails = { input ->
                viewModel.quickAdd(input) { id -> onOpenTask(id) }
                showQuickAdd = false
            },
            onDismiss = { showQuickAdd = false },
        )
    }
}

@Composable
private fun EmptyFor(scope: TaskScope) {
    val (icon: ImageVector, title: Int, body: Int) = when (scope) {
        TaskScope.Today -> Triple(Icons.Outlined.TaskAlt, R.string.empty_today_title, R.string.empty_today_body)
        TaskScope.Upcoming -> Triple(Icons.Outlined.EventAvailable, R.string.empty_upcoming_title, R.string.empty_upcoming_body)
        TaskScope.Completed -> Triple(Icons.Outlined.DoneAll, R.string.empty_completed_title, R.string.empty_completed_body)
        else -> Triple(Icons.Outlined.TaskAlt, R.string.empty_list_title, R.string.empty_list_body)
    }
    EmptyState(icon, stringResource(title), stringResource(body))
}

@Composable
private fun SortMenu(current: TaskSort, onSelect: (TaskSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.action_sort)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        TaskSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (sort) {
                                TaskSort.MANUAL -> R.string.sort_manual
                                TaskSort.DUE_DATE -> R.string.sort_due
                                TaskSort.PRIORITY -> R.string.sort_priority
                                TaskSort.CREATED -> R.string.sort_created
                                TaskSort.TITLE -> R.string.sort_title
                            },
                        ),
                    )
                },
                leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                onClick = { onSelect(sort); open = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    lists: List<Pair<String, String>>,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onPriority: (Priority) -> Unit,
    onMove: (String?) -> Unit,
    showComplete: Boolean,
) {
    var priorityMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear_selection)) } },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.action_select_all)) }
            if (showComplete) {
                IconButton(onClick = onComplete) { Icon(Icons.Outlined.DoneAll, stringResource(R.string.action_complete)) }
            }
            Box {
                IconButton(onClick = { priorityMenu = true }) { Icon(Icons.Outlined.Flag, stringResource(R.string.field_priority)) }
                DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    Priority.entries.forEach { p ->
                        DropdownMenuItem(text = { Text(priorityLabel(p)) }, onClick = { onPriority(p); priorityMenu = false })
                    }
                }
            }
            Box {
                IconButton(onClick = { moveMenu = true }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.action_move)) }
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.scope_inbox)) }, onClick = { onMove(null); moveMenu = false })
                    lists.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { onMove(id); moveMenu = false })
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
        },
    )
}
