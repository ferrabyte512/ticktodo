package app.ticktodo.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.ticktodo.ui.common.TimePickerModal
import app.ticktodo.ui.common.formatDate
import app.ticktodo.ui.common.formatTime
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.LocalDate
import java.time.LocalTime

/**
 * Fast entry: the keyboard opens immediately, Enter (or Save task) saves and
 * keeps the sheet open for the next task, and the most common fields are one
 * tap away. "More options" opens the full editor for the task.
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
    var dueMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var priority by rememberSaveable { mutableStateOf(defaults.priority) }
    var listId by rememberSaveable { mutableStateOf(defaults.listId) }
    var lastAdded by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val dueDate = dueEpochDay?.let(LocalDate::ofEpochDay)
    val dueTime = dueMinute?.let { LocalTime.of(it / 60, it % 60) }

    fun current() = QuickAddInput(text, dueDate, priority, listId, dueTime)
    fun submit() {
        if (text.isBlank()) return
        onAdd(current())
        lastAdded = QuickAddParser.parse(text).title
        text = defaults.text
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp).imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.a11y_close_sheet)) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= TaskValidator.MAX_TITLE) text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            // Quick date shortcuts.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateChip(Icons.Outlined.Today, stringResource(R.string.date_today), dueDate == today) {
                    dueEpochDay = if (dueDate == today) null else today.toEpochDay()
                }
                DateChip(Icons.Outlined.WbSunny, stringResource(R.string.date_tomorrow), dueDate == today.plusDays(1)) {
                    dueEpochDay = if (dueDate == today.plusDays(1)) null else today.plusDays(1).toEpochDay()
                }
                val customDate = dueDate != null && dueDate != today && dueDate != today.plusDays(1)
                DateChip(Icons.Outlined.CalendarToday, if (customDate) formatDate(dueDate!!, today) else stringResource(R.string.pick_date), customDate) {
                    showDatePicker = true
                }
            }

            // One row per field, value on the right.
            Column(Modifier.padding(top = 6.dp)) {
                OptionRow(
                    icon = Icons.Outlined.AccessTime,
                    label = stringResource(R.string.field_time),
                    value = dueTime?.let { formatTime(it) } ?: stringResource(if (dueDate != null) R.string.value_all_day else R.string.value_none),
                    enabled = dueDate != null,
                    onClick = { showTimePicker = true },
                )
                Box {
                    val priorityColor = LocalExtraColors.current.priority(priority)
                    OptionRow(
                        icon = Icons.Outlined.Flag,
                        label = stringResource(R.string.field_priority),
                        value = priorityLabel(priority),
                        iconTint = priorityColor,
                        onClick = { showPriorityMenu = true },
                    )
                    DropdownMenu(expanded = showPriorityMenu, onDismissRequest = { showPriorityMenu = false }) {
                        Priority.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(priorityLabel(p)) }, onClick = { priority = p; showPriorityMenu = false })
                        }
                    }
                }
                Box {
                    OptionRow(
                        icon = Icons.Outlined.Inbox,
                        label = stringResource(R.string.field_list),
                        value = lists.firstOrNull { it.id == listId }?.name ?: stringResource(R.string.scope_inbox),
                        onClick = { showListMenu = true },
                    )
                    DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.scope_inbox)) }, onClick = { listId = null; showListMenu = false })
                        lists.forEach { list ->
                            DropdownMenuItem(text = { Text(list.name) }, onClick = { listId = list.id; showListMenu = false })
                        }
                    }
                }
                OptionRow(
                    icon = Icons.Outlined.Tag,
                    label = stringResource(R.string.field_tags),
                    value = stringResource(R.string.quick_add_tag_hint),
                    onClick = { focus.requestFocus() },
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAddWithDetails(current()) }, enabled = text.isNotBlank(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.quick_add_more))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = lastAdded?.let { stringResource(R.string.quick_add_added, it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    maxLines = 1,
                )
            }
            Button(
                onClick = { submit() },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.action_save_task), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showDatePicker) {
        DatePickerModal(initial = dueDate ?: today, onPick = { dueEpochDay = it.toEpochDay() }, onDismiss = { showDatePicker = false })
    }
    if (showTimePicker) {
        TimePickerModal(dueTime, { dueMinute = it.hour * 60 + it.minute }) { showTimePicker = false }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun DateChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: Color? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(88.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
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
