package app.ticktodo.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.data.repository.InvalidInputException
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.focus.FocusManager
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskDraft
import app.ticktodo.domain.model.TaskList
import app.ticktodo.ui.common.Celebrations
import app.ticktodo.ui.common.DayTicker
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EditorForm(
    val title: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminderOffset: Int? = null,
    val recurrence: Recurrence? = null,
    val priority: Priority = Priority.NONE,
    val listId: String? = null,
    val tags: List<String> = emptyList(),
    val focusPlan: FocusPlan? = null,
)

data class EditorUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val original: Task? = null,
    val form: EditorForm = EditorForm(),
    val subtasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val titleError: Boolean = false,
    val focusedMs: Long = 0,
    val defaultFocusPlan: FocusPlan = FocusPlan.DEFAULT,
    /** The form as loaded, to tell whether anything was edited. */
    val initialForm: EditorForm = EditorForm(),
) {
    val isSubtask: Boolean get() = original?.parentId != null
    val hasChanges: Boolean get() = !loading && original != null && form != initialForm
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val messenger: UiMessenger,
    private val settings: SettingsRepository,
    focus: FocusManager,
    dayTicker: DayTicker,
    private val celebrations: Celebrations,
) : ViewModel() {

    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()
    private val local = MutableStateFlow(EditorUiState())

    val state: StateFlow<EditorUiState> = combine(
        local,
        repository.observeTask(taskId),
        repository.observeSubtasks(taskId),
        combine(repository.observeLists(), focus.observeTaskFocusTotal(taskId), settings.settings) { l, f, s -> Triple(l, f, s) },
        dayTicker.today,
    ) { s, task, subtasks, (lists, focused, prefs), today ->
        s.copy(
            focusedMs = focused,
            defaultFocusPlan = prefs.defaultFocusPlan,
            notFound = !s.loading && task == null,
            original = task ?: s.original,
            subtasks = subtasks,
            lists = lists,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    init {
        viewModelScope.launch {
            val task = repository.observeTask(taskId).first()
            local.value = if (task == null) {
                EditorUiState(loading = false, notFound = true)
            } else {
                val form = EditorForm(
                    title = task.title,
                    notes = task.notes,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    reminderOffset = task.reminderOffsetMinutes,
                    recurrence = task.recurrence,
                    priority = task.priority,
                    listId = task.listId,
                    tags = task.tags,
                    focusPlan = task.focusPlan,
                )
                EditorUiState(loading = false, original = task, form = form, initialForm = form)
            }
        }
    }

    private fun edit(block: (EditorForm) -> EditorForm) = local.update { it.copy(form = block(it.form), titleError = false) }

    fun setTitle(value: String) { if (value.length <= TaskValidator.MAX_TITLE) edit { it.copy(title = value) } }
    fun setNotes(value: String) { if (value.length <= TaskValidator.MAX_NOTES) edit { it.copy(notes = value) } }
    fun setPriority(value: Priority) = edit { it.copy(priority = value) }
    fun setList(value: String?) = edit { it.copy(listId = value) }
    fun setDueTime(value: LocalTime?) = edit { it.copy(dueTime = value) }
    fun setReminder(value: Int?) = edit { it.copy(reminderOffset = value) }
    fun setRecurrence(value: Recurrence?) = edit { it.copy(recurrence = value) }

    /** Clearing the date also clears what depends on it. */
    fun setDueDate(value: LocalDate?) = edit {
        if (value == null) it.copy(dueDate = null, dueTime = null, reminderOffset = null, recurrence = null)
        else it.copy(dueDate = value)
    }

    fun addTag(raw: String) = edit { form ->
        val tags = TaskValidator.cleanTags(form.tags + raw.split(' ', ','))
        form.copy(tags = tags)
    }

    fun setFocusPlan(value: FocusPlan?) = edit { it.copy(focusPlan = value) }

    fun removeTag(tag: String) = edit { it.copy(tags = it.tags - tag) }

    /** Saves if anything changed. Returns false (and flags the title) when the title is empty. */
    private var saving = false

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val s = local.value
        val original = state.value.original ?: run { onSaved(); return }
        val f = s.form
        if (TaskValidator.cleanTitle(f.title).isBlank()) {
            local.update { it.copy(titleError = true) }
            return
        }
        val updated = original.copy(
            title = f.title,
            notes = f.notes,
            dueDate = f.dueDate,
            dueTime = f.dueTime,
            reminderOffsetMinutes = f.reminderOffset,
            recurrence = f.recurrence,
            priority = f.priority,
            listId = f.listId,
            tags = f.tags,
            focusPlan = f.focusPlan,
        )
        saving = true
        viewModelScope.launch {
            try {
                if (updated != original) repository.update(updated)
                onSaved()
            } catch (e: InvalidInputException) {
                messenger.show(UiMessage(R.string.error_invalid_task))
            } finally {
                saving = false
            }
        }
    }

    fun toggleCompleted() {
        val task = state.value.original ?: return
        viewModelScope.launch {
            val change = repository.setCompleted(task.id, !task.isCompleted) ?: return@launch
            if (change.completed) {
                messenger.show(UiMessage(celebrations.taskDoneMessage(), actionLabel = R.string.action_undo, onAction = { repository.undoCompletion(change) }))
                celebrations.afterCompletion(task.id)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val affected = repository.delete(listOf(taskId))
            if (affected.isNotEmpty()) {
                messenger.show(
                    UiMessage(R.string.msg_deleted, listOf(1), R.string.action_undo) { repository.restore(affected) },
                )
            }
            onDeleted()
        }
    }

    fun addSubtask(title: String) {
        if (title.isBlank() || state.value.subtasks.size >= TaskValidator.MAX_SUBTASKS) return
        viewModelScope.launch {
            try {
                repository.create(TaskDraft(title = title, parentId = taskId))
            } catch (e: InvalidInputException) {
                messenger.show(UiMessage(R.string.error_invalid_task))
            }
        }
    }

    fun toggleSubtask(id: String, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(id, !completed) }
    }

    fun deleteSubtask(id: String) {
        viewModelScope.launch { repository.delete(listOf(id)) }
    }
}
