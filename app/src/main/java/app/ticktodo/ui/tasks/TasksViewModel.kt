package app.ticktodo.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.data.repository.InvalidInputException
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Task
import app.ticktodo.focus.FocusManager
import app.ticktodo.domain.model.TaskDraft
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskList
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.domain.model.TaskSort
import app.ticktodo.sync.SyncScheduler
import app.ticktodo.sync.SyncStatus
import app.ticktodo.sync.SyncStatusRepository
import app.ticktodo.ui.common.Celebrations
import app.ticktodo.ui.common.Connectivity
import app.ticktodo.ui.common.DayTicker
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TasksUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val sections: List<TaskSection> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val sort: TaskSort = TaskSort.DUE_DATE,
    val selection: Set<String> = emptySet(),
    /** Top-level tasks completed today; drives the Today ring. */
    val completedToday: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && sections.isEmpty()
    val selectionMode: Boolean get() = selection.isNotEmpty()
    val allIds: List<String> get() = sections.flatMap { s -> s.items.map { it.task.id } }
}

data class StatusUi(val offline: Boolean = false, val sync: SyncStatus = SyncStatus())

data class QuickAddInput(
    val text: String,
    val dueDate: LocalDate?,
    val priority: Priority,
    val listId: String?,
    val dueTime: LocalTime? = null,
)

/** Creates a task from the quick-add sheet; shared by every screen that hosts the sheet. */
fun ViewModel.quickAddTask(repository: TaskRepository, messenger: UiMessenger, input: QuickAddInput, onCreated: (String) -> Unit) {
    val parsed = QuickAddParser.parse(input.text)
    if (parsed.title.isBlank()) return
    viewModelScope.launch {
        try {
            val id = repository.create(
                TaskDraft(
                    title = parsed.title,
                    tags = parsed.tags,
                    dueDate = input.dueDate,
                    dueTime = input.dueTime?.takeIf { input.dueDate != null },
                    priority = input.priority,
                    listId = input.listId,
                ),
            )
            onCreated(id)
        } catch (e: InvalidInputException) {
            messenger.show(UiMessage(R.string.error_invalid_task))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val settings: SettingsRepository,
    private val messenger: UiMessenger,
    private val syncScheduler: SyncScheduler,
    dayTicker: DayTicker,
    connectivity: Connectivity,
    syncStatus: SyncStatusRepository,
    private val focus: FocusManager,
    private val celebrations: Celebrations,
) : ViewModel() {

    /** Id of the task currently in a focus session, if any. */
    val focusTaskId: StateFlow<String?> = focus.state.map { it?.taskId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), focus.state.value?.taskId)

    /** Starts a focus session with the task's own plan. */
    fun startFocus(task: Task) {
        val plan = task.focusPlan ?: return
        viewModelScope.launch {
            if (focus.state.value?.taskId != task.id) focus.start(task.id, task.title, plan)
        }
    }


    val scope: TaskScope = TaskScope.fromKey(savedStateHandle.get<String>("scopeKey") ?: "today")
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<TasksUiState> = combine(
        dayTicker.today.flatMapLatest { today -> repository.observeScope(scope, today).map { today to it } },
        settings.settings,
        repository.observeLists(),
        selection,
        if (scope == TaskScope.Today) dayTicker.today.flatMapLatest { repository.observeCompletedOn(it) } else flowOf(0),
    ) { (today, items), prefs, lists, selected, completedToday ->
        val visible = items.map { it.task.id }.toSet()
        TasksUiState(
            loading = false,
            today = today,
            sections = TaskGrouping.group(scope, items, today, prefs.sort),
            lists = lists,
            sort = prefs.sort,
            selection = selected intersect visible,
            completedToday = completedToday,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    val status: StateFlow<StatusUi> = combine(connectivity.isOnline, syncStatus.status) { online, sync ->
        StatusUi(offline = !online, sync = sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUi())

    /** Defaults for the quick-add sheet on this screen. */
    fun quickAddDefaults(today: LocalDate): QuickAddInput = when (scope) {
        TaskScope.Today -> QuickAddInput("", today, Priority.NONE, null)
        TaskScope.Upcoming -> QuickAddInput("", today.plusDays(1), Priority.NONE, null)
        is TaskScope.InList -> QuickAddInput("", null, Priority.NONE, scope.listId)
        is TaskScope.WithTag -> QuickAddInput("#${scope.tag} ", null, Priority.NONE, null)
        else -> QuickAddInput("", null, Priority.NONE, null)
    }

    fun quickAdd(input: QuickAddInput, onCreated: (String) -> Unit = {}) {
        quickAddTask(repository, messenger, input, onCreated)
    }

    fun toggleComplete(taskId: String, currentlyCompleted: Boolean) {
        viewModelScope.launch {
            val change = repository.setCompleted(taskId, !currentlyCompleted) ?: return@launch
            if (change.completed) {
                messenger.show(
                    UiMessage(
                        text = if (change.nextOccurrenceId != null) R.string.msg_completed_repeating else celebrations.taskDoneMessage(),
                        actionLabel = R.string.action_undo,
                        onAction = { repository.undoCompletion(change) },
                    ),
                )
                celebrations.afterCompletion(taskId)
            }
        }
    }

    fun delete(ids: Collection<String>) {
        viewModelScope.launch {
            val affected = repository.delete(ids)
            selection.value = emptySet()
            if (affected.isNotEmpty()) {
                messenger.show(
                    UiMessage(
                        text = R.string.msg_deleted,
                        args = listOf(ids.size),
                        actionLabel = R.string.action_undo,
                        onAction = { repository.restore(affected) },
                    ),
                )
            }
        }
    }

    fun toggleSelection(id: String) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { state.value.allIds.toSet() }
    fun clearSelection() { selection.value = emptySet() }

    fun completeSelected() {
        val ids = selection.value
        viewModelScope.launch {
            val changes = repository.completeMany(ids)
            selection.value = emptySet()
            if (changes.isNotEmpty()) {
                changes.forEach { celebrations.afterCompletion(it.taskId) }
                messenger.show(
                    UiMessage(
                        text = R.string.msg_completed_many,
                        args = listOf(changes.size),
                        actionLabel = R.string.action_undo,
                        onAction = { changes.forEach { repository.undoCompletion(it) } },
                    ),
                )
            }
        }
    }

    fun deleteSelected() = delete(selection.value)

    fun setPriorityForSelected(priority: Priority) {
        val ids = selection.value
        viewModelScope.launch {
            repository.setPriority(ids, priority)
            selection.value = emptySet()
        }
    }

    fun moveSelected(listId: String?) {
        val ids = selection.value
        viewModelScope.launch {
            repository.moveToList(ids, listId)
            selection.value = emptySet()
        }
    }

    /** Saves "My order" after [movedId] was dropped; [sectionItems] are in their new on-screen order. */
    fun reorder(sectionItems: List<TaskItem>, movedId: String) {
        val updates = TaskGrouping.manualOrderUpdates(sectionItems, movedId)
        viewModelScope.launch { repository.setSortOrders(updates) }
    }

    fun setSort(sort: TaskSort) {
        viewModelScope.launch { settings.setSort(sort) }
    }

    fun refresh() = syncScheduler.requestSync(delaySeconds = 0)
}
