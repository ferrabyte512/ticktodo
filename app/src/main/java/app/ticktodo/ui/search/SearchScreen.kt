package app.ticktodo.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskList
import app.ticktodo.ui.common.DayTicker
import app.ticktodo.ui.common.EmptyState
import app.ticktodo.ui.common.ListBadge
import app.ticktodo.ui.common.TaskRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<TaskItem> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    dayTicker: DayTicker,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow("q", "")

    val state: StateFlow<SearchUiState> = combine(
        query,
        query.debounce(150).flatMapLatest { repository.search(it) },
        repository.observeLists(),
        dayTicker.today,
    ) { q, results, lists, today ->
        SearchUiState(q, if (q.isBlank()) emptyList() else results, lists, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) { savedStateHandle["q"] = value.take(100) }

    fun toggleComplete(item: TaskItem) {
        viewModelScope.launch { repository.setCompleted(item.task.id, !item.task.isCompleted) }
    }

    fun delete(item: TaskItem) {
        viewModelScope.launch { repository.delete(listOf(item.task.id)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.action_clear_search)) }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.query.isNotBlank() && state.results.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
                }
            }
            items(state.results, key = { it.task.id }) { item ->
                val list = item.task.listId?.let { id -> state.lists.firstOrNull { it.id == id } }
                TaskRow(
                    item = item,
                    today = state.today,
                    list = list?.let { ListBadge(it.name, it.colorIndex) },
                    selectionMode = false,
                    selected = false,
                    onToggleComplete = { viewModel.toggleComplete(item) },
                    onClick = { onOpenTask(item.task.id) },
                    onLongClick = { onOpenTask(item.task.id) },
                    onDelete = { viewModel.delete(item) },
                )
            }
        }
    }
}
