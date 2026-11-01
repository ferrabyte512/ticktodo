package app.ticktodo.ui.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskList
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.domain.model.TaskSort
import app.ticktodo.ui.common.Celebrations
import app.ticktodo.ui.common.DayTicker
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import app.ticktodo.ui.tasks.QuickAddInput
import app.ticktodo.ui.tasks.quickAddTask
import app.ticktodo.ui.tasks.TaskGrouping
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MatrixUiState(
    val today: LocalDate = LocalDate.now(),
    val quadrants: Map<Quadrant, List<TaskItem>> = emptyMap(),
    val lists: List<TaskList> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatrixViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val messenger: UiMessenger,
    private val celebrations: Celebrations,
    dayTicker: DayTicker,
) : ViewModel() {

    val state: StateFlow<MatrixUiState> = combine(
        dayTicker.today.flatMapLatest { today -> repository.observeScope(TaskScope.All, today).map { today to it } },
        repository.observeLists(),
    ) { (today, items), lists ->
        MatrixUiState(
            today = today,
            // Within a box, the nearest deadline comes first.
            quadrants = Quadrant.entries.associateWith { q ->
                TaskGrouping.sort(items.filter { it.task.priority == q.priority }, TaskSort.DUE_DATE)
            },
            lists = lists,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatrixUiState())

    fun complete(taskId: String) {
        viewModelScope.launch {
            val change = repository.setCompleted(taskId, true) ?: return@launch
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

    fun quickAdd(input: QuickAddInput, onCreated: (String) -> Unit = {}) {
        quickAddTask(repository, messenger, input, onCreated)
    }
}
