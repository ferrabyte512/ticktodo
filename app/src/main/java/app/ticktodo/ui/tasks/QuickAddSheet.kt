package app.ticktodo.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.TaskList
import app.ticktodo.ui.common.DatePickerModal
import app.ticktodo.ui.common.formatDate
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.LocalDate

/**
 * Fast entry: the keyboard opens immediately, Enter saves and keeps the sheet
 * open for the next task, and the most common fields are one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    defaults: QuickAddInput,
    today: LocalDate,
    lists: List<TaskList>,
    onAdd: (QuickAddInput) -> Unit,
    onAddWithDetails: (QuickAddInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf(defaults.text) }
    var dueEpochDay by rememberSaveable { mutableStateOf(defaults.dueDate?.toEpochDay()) }
    var priority by rememberSaveable { mutableStateOf(defaults.priority) }
    var listId by rememberSaveable { mutableStateOf(defaults.listId) }
    var lastAdded by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val dueDate = dueEpochDay?.let(LocalDate::ofEpochDay)

    fun current() = QuickAddInput(text, dueDate, priority, listId)
    fun submit() {
        if (text.isBlank()) return
        onAdd(current())
        lastAdded = QuickAddParser.parse(text).title
        text = defaults.text
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= TaskValidator.MAX_TITLE) text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                supportingText = { Text(stringResource(R.string.quick_add_tag_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    FilledIconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_add_task))
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = dueDate == today,
                    onClick = { dueEpochDay = if (dueDate == today) null else today.toEpochDay() },
                    label = { Text(stringResource(R.string.date_today)) },
                )
                FilterChip(
                    selected = dueDate == today.plusDays(1),
                    onClick = { dueEpochDay = if (dueDate == today.plusDays(1)) null else today.plusDays(1).toEpochDay() },
                    label = { Text(stringResource(R.string.date_tomorrow)) },
                )
                val customDate = dueDate != null && dueDate != today && dueDate != today.plusDays(1)
                FilterChip(
                    selected = customDate,
                    onClick = { showDatePicker = true },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null) },
                    label = { Text(if (customDate) formatDate(dueDate!!, today) else stringResource(R.string.pick_date)) },
                )
                val priorityColor = LocalExtraColors.current.priority(priority)
                FilterChip(
                    selected = priority != Priority.NONE,
                    onClick = { priority = Priority.entries[(priority.ordinal + 1) % Priority.entries.size] },
                    leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = { Text(priorityLabel(priority)) },
                )
                Box {
                    FilterChip(
                        selected = listId != null,
                        onClick = { showListMenu = true },
                        leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                        label = { Text(lists.firstOrNull { it.id == listId }?.name ?: stringResource(R.string.scope_inbox)) },
                    )
                    DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.scope_inbox)) }, onClick = { listId = null; showListMenu = false })
                        lists.forEach { list ->
                            DropdownMenuItem(text = { Text(list.name) }, onClick = { listId = list.id; showListMenu = false })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lastAdded?.let { stringResource(R.string.quick_add_added, it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    maxLines = 1,
                )
                TextButton(onClick = { onAddWithDetails(current()) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.quick_add_more))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(initial = dueDate ?: today, onPick = { dueEpochDay = it.toEpochDay() }, onDismiss = { showDatePicker = false })
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
fun priorityLabel(priority: Priority): String = stringResource(
    when (priority) {
        Priority.NONE -> R.string.priority_none
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
    },
)
