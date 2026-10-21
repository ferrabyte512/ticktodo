package app.ticktodo.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ticktodo.R
import app.ticktodo.ads.AdPlacement
import app.ticktodo.ads.AdSlot
import app.ticktodo.ads.AdsGateway
import app.ticktodo.data.repository.InvalidInputException
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.model.TaskList
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.ui.common.ConfirmDialog
import app.ticktodo.ui.common.SectionTitle
import app.ticktodo.ui.common.UiMessage
import app.ticktodo.ui.common.UiMessenger
import app.ticktodo.ui.theme.LocalExtraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(val lists: List<TaskList> = emptyList(), val tags: List<String> = emptyList())

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val messenger: UiMessenger,
    val ads: AdsGateway,
) : ViewModel() {
    val state: StateFlow<BrowseUiState> = combine(repository.observeLists(), repository.observeTags()) { lists, tags ->
        BrowseUiState(lists, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    fun saveList(id: String?, name: String, color: Int) {
        viewModelScope.launch {
            try {
                if (id == null) repository.createList(name, color) else repository.updateList(id, name, color)
            } catch (e: InvalidInputException) {
                messenger.show(UiMessage(R.string.error_invalid_list))
            }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch { repository.deleteList(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    onOpenScope: (TaskScope) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskList?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskList?>(null) }
    val extra = LocalExtraColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_browse)) },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.AccountCircle, stringResource(R.string.settings_title)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    modifier = Modifier.clickable { onOpenScope(TaskScope.Inbox) },
                    leadingContent = { Icon(Icons.Outlined.Inbox, null) },
                    headlineContent = { Text(stringResource(R.string.scope_inbox)) },
                )
                ListItem(
                    modifier = Modifier.clickable { onOpenScope(TaskScope.All) },
                    leadingContent = { Icon(Icons.Outlined.ViewList, null) },
                    headlineContent = { Text(stringResource(R.string.scope_all)) },
                )
                ListItem(
                    modifier = Modifier.clickable { onOpenScope(TaskScope.Completed) },
                    leadingContent = { Icon(Icons.Outlined.DoneAll, null) },
                    headlineContent = { Text(stringResource(R.string.scope_completed)) },
                )
                SectionTitle(stringResource(R.string.browse_lists))
            }
            items(state.lists, key = { it.id }) { list ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.clickable { onOpenScope(TaskScope.InList(list.id)) },
                    leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(extra.list(list.colorIndex))) },
                    headlineContent = { Text(list.name) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.a11y_list_options, list.name))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; editing = list })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; deleting = list })
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { creating = true },
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    headlineContent = { Text(stringResource(R.string.action_new_list), color = MaterialTheme.colorScheme.primary) },
                )
                if (state.tags.isNotEmpty()) SectionTitle(stringResource(R.string.field_tags))
            }
            if (state.tags.isNotEmpty()) {
                item {
                    FlowRow(Modifier.padding(horizontal = 8.dp)) {
                        state.tags.forEach { tag ->
                            ListItem(
                                modifier = Modifier.clickable { onOpenScope(TaskScope.WithTag(tag)) },
                                leadingContent = { Icon(Icons.Outlined.Tag, null) },
                                headlineContent = { Text(tag) },
                            )
                        }
                    }
                }
            }
            item { AdSlot(viewModel.ads, AdPlacement.BROWSE_FOOTER, Modifier.fillMaxWidth()) }
        }
    }

    if (creating || editing != null) {
        ListDialog(
            initial = editing,
            onSave = { name, color -> viewModel.saveList(editing?.id, name, color); creating = false; editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { list ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_list_title, list.name),
            text = stringResource(R.string.confirm_delete_list_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteList(list.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListDialog(initial: TaskList?, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.colorIndex ?: 0) }
    val extra = LocalExtraColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.action_new_list else R.string.action_edit_list)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= TaskValidator.MAX_LIST_NAME) name = it },
                    label = { Text(stringResource(R.string.field_list_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    extra.lists.forEachIndexed { index, c ->
                        val label = stringResource(R.string.a11y_color_n, index + 1)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (index == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = index }
                                .semantics {
                                    role = Role.RadioButton
                                    selected = index == color
                                    contentDescription = label
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
