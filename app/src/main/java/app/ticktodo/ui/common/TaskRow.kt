package app.ticktodo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.LocalDate

data class ListBadge(val name: String, val colorIndex: Int)

private const val COMPLETE_DELAY_MS = 320L

/**
 * A task as a large, easy-to-tap card: the whole card opens the task, the big
 * circle completes it, swipe right completes, swipe left deletes, long-press
 * selects. Tasks with a focus plan show a start-focus button.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    item: TaskItem,
    today: LocalDate,
    list: ListBadge?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onStartFocus: (() -> Unit)? = null,
    focusing: Boolean = false,
) {
    val task = item.task
    val extra = LocalExtraColors.current
    val completeLabel = stringResource(if (task.isCompleted) R.string.a11y_mark_incomplete else R.string.a11y_mark_complete)
    val deleteLabel = stringResource(R.string.action_delete)
    val focusLabel = stringResource(R.string.focus_start)
    val stateText = stringResource(if (task.isCompleted) R.string.a11y_completed else R.string.a11y_not_completed)
    val priorityColor = extra.priority(task.priority)

    // Tapping the circle fills it and strikes the title first, then completes the
    // task, so the change is visible before the card leaves the list.
    var completing by remember(task.id) { mutableStateOf(false) }
    val shownDone = task.isCompleted || completing
    LaunchedEffect(completing) {
        if (completing) {
            delay(COMPLETE_DELAY_MS)
            onToggleComplete()
            completing = false
        }
    }
    val tapCheck = { if (task.isCompleted) onToggleComplete() else if (!completing) completing = true }
    val checkScale by animateFloatAsState(
        targetValue = if (shownDone) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "check",
    )
    val checkTint by animateColorAsState(
        when {
            shownDone -> MaterialTheme.colorScheme.primary
            priorityColor != null -> priorityColor
            else -> MaterialTheme.colorScheme.outline
        },
        label = "checkTint",
    )
    val titleAlpha by animateFloatAsState(if (shownDone) 0.6f else 1f, label = "titleAlpha")

    // The card springs back after a swipe; the list update then removes it.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleComplete()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "swipe",
            )
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(color).padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Outlined.Done, contentDescription = null)
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Outlined.Delete, contentDescription = null)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
    ) {
        GlassSurface(
            shape = MaterialTheme.shapes.large,
            color = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                focusing -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    stateDescription = stateText
                    // Swipes are not available to screen-reader users, so offer them as actions.
                    customActions = buildList {
                        add(CustomAccessibilityAction(completeLabel) { onToggleComplete(); true })
                        add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                        if (onStartFocus != null) add(CustomAccessibilityAction(focusLabel) { onStartFocus(); true })
                    }
                },
        ) {
            Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
                // Priority mark: a short rounded pill inset from the card edge.
                Box(
                    Modifier
                        .padding(start = 6.dp, top = 16.dp, bottom = 16.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(priorityColor ?: Color.Transparent),
                )
                IconButton(
                    onClick = tapCheck,
                    modifier = Modifier.padding(start = 2.dp).size(56.dp).semantics { contentDescription = "$completeLabel: ${task.title}" },
                ) {
                    Icon(
                        imageVector = if (shownDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                        tint = checkTint,
                    )
                }
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (shownDone) TextDecoration.LineThrough else null,
                        color = if (shownDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                    )
                    if (task.notes.isNotBlank()) {
                        Text(
                            text = task.notes.lineSequence().first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MetaChips(item, today, list, focusing)
                }
                if (task.pendingSync) {
                    Icon(
                        Icons.Outlined.CloudUpload,
                        contentDescription = stringResource(R.string.a11y_pending_sync),
                        modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                if (onStartFocus != null && !task.isCompleted) {
                    FilledTonalIconButton(
                        onClick = onStartFocus,
                        modifier = Modifier.padding(end = 8.dp).size(48.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "$focusLabel: ${task.title}")
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaChips(item: TaskItem, today: LocalDate, list: ListBadge?, focusing: Boolean) {
    val task = item.task
    val extra = LocalExtraColors.current
    val hasMeta = focusing || task.dueDate != null || list != null || task.tags.isNotEmpty() || item.subtaskTotal > 0 || task.focusPlan != null
    if (!hasMeta) return
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (focusing) Chip(Icons.Outlined.Timer, stringResource(R.string.focus_in_progress), extra.focus)
        task.dueDate?.let { date ->
            val overdue = !task.isCompleted && dueStatus(date, today) == DueStatus.OVERDUE
            val text = formatDate(date, today) + (task.dueTime?.let { " · " + formatTime(it) } ?: "")
            Chip(
                icon = when {
                    task.recurrence != null -> Icons.Outlined.Repeat
                    task.reminderOffsetMinutes != null -> Icons.Outlined.Notifications
                    else -> null
                },
                text = text,
                color = if (overdue) extra.overdue else MaterialTheme.colorScheme.primary,
            )
        }
        if (item.subtaskTotal > 0) {
            Chip(Icons.Outlined.Checklist, stringResource(R.string.subtask_progress, item.subtaskDone, item.subtaskTotal))
        }
        if (task.focusPlan != null && !focusing) {
            Chip(Icons.Outlined.Timer, stringResource(R.string.focus_minutes_short, task.focusPlan.focusMinutes))
        }
        list?.let { ListChip(it) }
        task.tags.take(3).forEach { Chip(null, "#$it") }
    }
}

@Composable
private fun Chip(icon: ImageVector?, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListChip(list: ListBadge) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LocalExtraColors.current.list(list.colorIndex)))
            Spacer(Modifier.width(5.dp))
            Text(list.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
