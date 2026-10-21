package app.ticktodo.ui.editor

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.ui.focus.PlanEditor
import app.ticktodo.ui.focus.formatDuration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.R
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.ui.common.ConfirmDialog
import app.ticktodo.ui.common.DatePickerModal
import app.ticktodo.ui.common.ReminderPermissions
import app.ticktodo.ui.common.SectionTitle
import app.ticktodo.ui.common.TimePickerModal
import app.ticktodo.ui.common.formatDate
import app.ticktodo.ui.common.formatRecurrence
import app.ticktodo.ui.common.formatReminder
import app.ticktodo.ui.common.formatTime
import app.ticktodo.ui.tasks.priorityLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    onOpenTask: (String) -> Unit,
    onStartFocus: (String) -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val form = state.form
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(true) }
    var exactOk by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        notificationsOk = ReminderPermissions.notificationsAllowed(context)
        exactOk = ReminderPermissions.exactAlarmsAllowed(context)
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOk = granted
    }

    LaunchedEffect(state.notFound) { if (state.notFound) onClose() }
    val saveAndClose = { viewModel.save(onClose) }
    BackHandler(onBack = saveAndClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_save_and_close)) }
                },
                actions = {
                    state.original?.let { task ->
                        IconButton(onClick = viewModel::toggleCompleted) {
                            Icon(
                                if (task.isCompleted) Icons.Outlined.TaskAlt else Icons.Outlined.Done,
                                stringResource(if (task.isCompleted) R.string.a11y_mark_incomplete else R.string.a11y_mark_complete),
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 32.dp),
            ) {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        textDecoration = if (state.original?.isCompleted == true) TextDecoration.LineThrough else null,
                    ),
                    label = { Text(stringResource(R.string.field_title)) },
                    isError = state.titleError,
                    supportingText = { if (state.titleError) Text(stringResource(R.string.error_title_required)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 3,
                )

                if (!state.isSubtask) {
                    EditorRow(
                        icon = Icons.Outlined.CalendarToday,
                        label = stringResource(R.string.field_due_date),
                        value = form.dueDate?.let { formatDate(it, state.today) } ?: stringResource(R.string.value_none),
                        onClick = { showDate = true },
                        onClear = if (form.dueDate != null) ({ viewModel.setDueDate(null) }) else null,
                    )
                    if (form.dueDate != null) {
                        EditorRow(
                            icon = Icons.Outlined.AccessTime,
                            label = stringResource(R.string.field_time),
                            value = form.dueTime?.let { formatTime(it) } ?: stringResource(R.string.value_all_day),
                            onClick = { showTime = true },
                            onClear = if (form.dueTime != null) ({ viewModel.setDueTime(null) }) else null,
                        )
                        ReminderRow(form.reminderOffset) { offset ->
                            viewModel.setReminder(offset)
                            if (offset != null && !notificationsOk && ReminderPermissions.canRequestNotificationsAtRuntime()) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (form.reminderOffset != null && (!notificationsOk || !exactOk)) {
                            ReminderWarning(notificationsOk, exactOk)
                        }
                        RepeatRow(form.recurrence, viewModel::setRecurrence)
                    }
                    ListRow(state.lists.map { it.id to it.name }, form.listId, viewModel::setList)
                }

                SectionTitle(stringResource(R.string.field_priority))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Priority.entries.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = form.priority == p,
                            onClick = { viewModel.setPriority(p) },
                            shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                        ) { Text(priorityLabel(p), maxLines = 1) }
                    }
                }

                if (!state.isSubtask) {
                    SectionTitle(stringResource(R.string.field_tags))
                    TagEditor(form.tags, viewModel::addTag, viewModel::removeTag)
                }

                if (!state.isSubtask) {
                    FocusSection(
                        plan = form.focusPlan,
                        defaultPlan = state.defaultFocusPlan,
                        focusedMs = state.focusedMs,
                        onPlanChange = viewModel::setFocusPlan,
                        onStart = { state.original?.let { t -> viewModel.save { onStartFocus(t.id) } } },
                    )
                }

                SectionTitle(stringResource(R.string.field_notes))
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.notes_hint)) },
                    minLines = 3,
                )

                if (!state.isSubtask) {
                    SectionTitle(stringResource(R.string.field_subtasks))
                    state.subtasks.forEach { sub ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = sub.isCompleted, onCheckedChange = { viewModel.toggleSubtask(sub.id, sub.isCompleted) })
                            Text(
                                sub.title,
                                modifier = Modifier.weight(1f).clickable { onOpenTask(sub.id) },
                                textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                            )
                            IconButton(onClick = { viewModel.deleteSubtask(sub.id) }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.action_delete_subtask, sub.title))
                            }
                        }
                    }
                    AddSubtaskField(viewModel::addSubtask)
                }

                Spacer(Modifier.height(24.dp))
                state.original?.let { task ->
                    Text(
                        stringResource(if (task.pendingSync) R.string.editor_pending_sync else R.string.editor_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showDate) DatePickerModal(form.dueDate ?: state.today, viewModel::setDueDate) { showDate = false }
    if (showTime) TimePickerModal(form.dueTime, { viewModel.setDueTime(it) }) { showTime = false }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_task_title),
            text = stringResource(R.string.confirm_delete_task_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete(onClose) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun EditorRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)?) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        trailingContent = onClear?.let {
            { IconButton(onClick = it) { Icon(Icons.Outlined.Clear, stringResource(R.string.action_clear_field, label)) } }
        },
    )
}

@Composable
private fun ReminderRow(current: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        EditorRow(
            icon = Icons.Outlined.Notifications,
            label = stringResource(R.string.field_reminder),
            value = current?.let { formatReminder(it) } ?: stringResource(R.string.value_none),
            onClick = { open = true },
            onClear = if (current != null) ({ onSelect(null) }) else null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.value_none)) }, onClick = { onSelect(null); open = false })
            TaskValidator.REMINDER_OFFSETS.forEach { offset ->
                DropdownMenuItem(text = { Text(formatReminder(offset)) }, onClick = { onSelect(offset); open = false })
            }
        }
    }
}

@Composable
private fun ReminderWarning(notificationsOk: Boolean, exactOk: Boolean) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(if (!notificationsOk) R.string.warning_notifications_off else R.string.warning_exact_alarms_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            val intent = if (!notificationsOk) ReminderPermissions.notificationSettingsIntent(context)
            else ReminderPermissions.exactAlarmSettingsIntent(context)
            intent?.let { runCatching { context.startActivity(it) } }
        }) { Text(stringResource(R.string.action_fix)) }
    }
}

@Composable
private fun RepeatRow(current: Recurrence?, onSelect: (Recurrence?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        Recurrence(Recurrence.Frequency.DAILY),
        Recurrence(Recurrence.Frequency.WEEKLY, 1, Recurrence.WEEKDAYS),
        Recurrence(Recurrence.Frequency.WEEKLY),
        Recurrence(Recurrence.Frequency.WEEKLY, 2),
        Recurrence(Recurrence.Frequency.MONTHLY),
        Recurrence(Recurrence.Frequency.YEARLY),
    )
    Box {
        EditorRow(
            icon = Icons.Outlined.Repeat,
            label = stringResource(R.string.field_repeat),
            value = current?.let { formatRecurrence(it) } ?: stringResource(R.string.repeat_never),
            onClick = { open = true },
            onClear = if (current != null) ({ onSelect(null) }) else null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.repeat_never)) }, onClick = { onSelect(null); open = false })
            options.forEach { rule ->
                DropdownMenuItem(text = { Text(formatRecurrence(rule)) }, onClick = { onSelect(rule); open = false })
            }
        }
    }
}

@Composable
private fun ListRow(lists: List<Pair<String, String>>, current: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        EditorRow(
            icon = Icons.Outlined.Inbox,
            label = stringResource(R.string.field_list),
            value = lists.firstOrNull { it.first == current }?.second ?: stringResource(R.string.scope_inbox),
            onClick = { open = true },
            onClear = null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.scope_inbox)) }, onClick = { onSelect(null); open = false })
            lists.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(tags: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove_tag, tag)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= TaskValidator.MAX_TAG_LENGTH + 1) input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.tag_hint)) },
            singleLine = true,
            enabled = tags.size < TaskValidator.MAX_TAGS,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(input); input = "" }),
        )
    }
}

@Composable
private fun AddSubtaskField(onAdd: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.outline)
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= TaskValidator.MAX_TITLE) input = it },
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            placeholder = { Text(stringResource(R.string.subtask_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(input); input = "" }),
        )
    }
}

@Composable
private fun FocusSection(
    plan: FocusPlan?,
    defaultPlan: FocusPlan,
    focusedMs: Long,
    onPlanChange: (FocusPlan?) -> Unit,
    onStart: () -> Unit,
) {
    SectionTitle(stringResource(R.string.focus_section))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = plan != null, role = Role.Switch) { onPlanChange(if (it) defaultPlan else null) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_use_for_task), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.focus_use_for_task_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = plan != null, onCheckedChange = null)
            }
            if (plan != null) {
                PlanEditor(plan, onPlanChange, Modifier.padding(top = 8.dp))
            }
            if (focusedMs > 0) {
                Text(
                    stringResource(R.string.focus_total_on_task, formatDuration(focusedMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            FilledTonalButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Timer, null)
                Text(stringResource(R.string.focus_start_session), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
