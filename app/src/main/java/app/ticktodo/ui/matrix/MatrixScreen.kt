package app.ticktodo.ui.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.R
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.ui.common.GlassSurface
import app.ticktodo.ui.common.formatDate
import app.ticktodo.ui.common.formatTime
import app.ticktodo.ui.tasks.QuickAddInput
import app.ticktodo.ui.tasks.QuickAddSheet
import app.ticktodo.ui.theme.LocalExtraColors
import java.time.LocalDate

/**
 * The four boxes of the urgent / important matrix. Each box is one priority:
 * High = urgent and important, Medium = urgent but not important,
 * Low = important but not urgent, Optional (no priority) = neither.
 */
enum class Quadrant(val priority: Priority, val title: Int, val meaning: Int) {
    HIGH(Priority.HIGH, R.string.matrix_high, R.string.matrix_high_meaning),
    MEDIUM(Priority.MEDIUM, R.string.matrix_medium, R.string.matrix_medium_meaning),
    LOW(Priority.LOW, R.string.matrix_low, R.string.matrix_low_meaning),
    OPTIONAL(Priority.NONE, R.string.matrix_optional, R.string.matrix_optional_meaning),
    ;

    companion object {
        fun of(priority: Priority): Quadrant = entries.first { it.priority == priority }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    onOpenTask: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MatrixViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addTo by rememberSaveable { mutableStateOf<Quadrant?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_matrix)) },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.AccountCircle, stringResource(R.string.settings_title)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top row: urgent. Left column: important.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantBox(Quadrant.HIGH, state, onOpenTask, viewModel::complete, { addTo = it }, Modifier.weight(1f))
                QuadrantBox(Quadrant.MEDIUM, state, onOpenTask, viewModel::complete, { addTo = it }, Modifier.weight(1f))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantBox(Quadrant.LOW, state, onOpenTask, viewModel::complete, { addTo = it }, Modifier.weight(1f))
                QuadrantBox(Quadrant.OPTIONAL, state, onOpenTask, viewModel::complete, { addTo = it }, Modifier.weight(1f))
            }
        }
    }

    addTo?.let { quadrant ->
        QuickAddSheet(
            defaults = QuickAddInput("", null, quadrant.priority, null),
            today = state.today,
            lists = state.lists,
            onAdd = { viewModel.quickAdd(it) },
            onAddWithDetails = { input ->
                viewModel.quickAdd(input) { id -> onOpenTask(id) }
                addTo = null
            },
            onDismiss = { addTo = null },
        )
    }
}

@Composable
private fun QuadrantBox(
    quadrant: Quadrant,
    state: MatrixUiState,
    onOpenTask: (String) -> Unit,
    onComplete: (String) -> Unit,
    onAdd: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtraColors.current
    val accent = extra.priority(quadrant.priority) ?: MaterialTheme.colorScheme.outline
    val items = state.quadrants[quadrant].orEmpty()
    GlassSurface(modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                        Text(
                            stringResource(quadrant.title),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            items.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Text(
                        stringResource(quadrant.meaning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = { onAdd(quadrant) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.matrix_add_to, stringResource(quadrant.title)))
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.matrix_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(items, key = { it.task.id }) { item ->
                        MatrixRow(item, state.today, accent, onOpenTask, onComplete, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixRow(
    item: TaskItem,
    today: LocalDate,
    accent: Color,
    onOpenTask: (String) -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = item.task
    val overdue = task.dueDate?.isBefore(today) == true
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenTask(task.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onComplete(task.id) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Circle, stringResource(R.string.a11y_mark_complete) + ": " + task.title, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.dueDate?.let { date ->
                Text(
                    formatDate(date, today) + (task.dueTime?.let { " · " + formatTime(it) } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) LocalExtraColors.current.overdue else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (item.subtaskTotal > 0) {
            Text(
                stringResource(R.string.subtask_progress, item.subtaskDone, item.subtaskTotal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).width(32.dp),
            )
        }
    }
}
