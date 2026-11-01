package app.ticktodo.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.core.Clock
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.domain.model.Task
import app.ticktodo.focus.FocusManager
import app.ticktodo.ui.common.Celebrations
import app.ticktodo.ui.common.DayTicker
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val active: FocusState? = null,
    /** The task this screen was opened for (may differ from the active session's task). */
    val task: Task? = null,
    val draftPlan: FocusPlan = FocusPlan.DEFAULT,
    val saveToTask: Boolean = false,
    val focusedTodayMs: Long = 0,
    val focusedOnTaskMs: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manager: FocusManager,
    private val repository: TaskRepository,
    private val settings: SettingsRepository,
    private val messenger: UiMessenger,
    private val clock: Clock,
    dayTicker: DayTicker,
    private val celebrations: Celebrations,
) : ViewModel() {

    private val requestedTaskId: String? = savedStateHandle.get<String>("taskId")?.takeIf { it.isNotBlank() }
    private val draft = MutableStateFlow<FocusPlan?>(null)
    private val saveToTask = MutableStateFlow(false)

    private val taskFlow = repository.observeTask(requestedTaskId ?: manager.state.value?.taskId ?: "")

    val state: StateFlow<FocusUiState> = combine(
        manager.state,
        taskFlow,
        combine(draft, saveToTask) { d, s -> d to s },
        dayTicker.today.flatMapLatest { today ->
            manager.observeFocusSince(today.atStartOfDay(clock.zone()).toInstant().toEpochMilli())
        },
        manager.state.flatMapLatest { active ->
            val id = requestedTaskId ?: active?.taskId
            if (id == null) flowOf(0L) else manager.observeTaskFocusTotal(id)
        },
    ) { active, task, (d, save), today, onTask ->
        FocusUiState(
            active = active,
            task = task,
            draftPlan = d ?: task?.focusPlan ?: FocusPlan.DEFAULT,
            saveToTask = save,
            focusedTodayMs = today,
            focusedOnTaskMs = onTask,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState(active = manager.state.value))

    init {
        viewModelScope.launch {
            if (draft.value == null) {
                val task = taskFlow.first()
                draft.value = task?.focusPlan ?: settings.current().defaultFocusPlan
            }
        }
    }

    fun setDraft(plan: FocusPlan) { draft.value = plan }
    fun setSaveToTask(value: Boolean) { saveToTask.value = value }

    fun start() {
        val s = state.value
        val task = s.task ?: return
        viewModelScope.launch {
            if (s.saveToTask && task.focusPlan != s.draftPlan) repository.update(task.copy(focusPlan = s.draftPlan))
            manager.start(task.id, task.title, s.draftPlan)
        }
    }

    fun pause() = viewModelScope.launch { manager.pause() }
    fun resume() = viewModelScope.launch { manager.resume() }
    fun skip() = viewModelScope.launch { manager.skip() }
    fun extend() = viewModelScope.launch { manager.extend() }
    fun stop() = viewModelScope.launch { manager.stop() }
    fun timeUp() = viewModelScope.launch { manager.onPhaseTimeUp() }

    /** Marks the focused task as done and ends the session. */
    fun completeTask(onDone: () -> Unit) {
        val active = state.value.active ?: return
        viewModelScope.launch {
            manager.stop()
            val change = repository.setCompleted(active.taskId, true)
            if (change != null) {
                messenger.show(UiMessage(celebrations.taskDoneMessage(), actionLabel = R.string.action_undo, onAction = { repository.undoCompletion(change) }))
                celebrations.afterCompletion(active.taskId)
            }
            onDone()
        }
    }
}
