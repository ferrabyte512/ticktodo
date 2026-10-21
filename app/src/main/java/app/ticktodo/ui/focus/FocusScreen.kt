package app.ticktodo.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ticktodo.R
import app.ticktodo.domain.focus.FocusPhase
import app.ticktodo.domain.focus.FocusState
import app.ticktodo.focus.FocusNotifications
import app.ticktodo.ui.theme.LocalExtraColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onBack: () -> Unit, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active = state.active

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    active != null -> {
                        RunningSession(active, viewModel, onBack)
                        if (state.task != null && state.task!!.id != active.taskId) {
                            Text(
                                stringResource(R.string.focus_other_running, state.task!!.title),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = viewModel::start) { Text(stringResource(R.string.focus_switch_task)) }
                        }
                    }
                    state.task != null -> Setup(state, viewModel)
                    else -> Text(stringResource(R.string.focus_pick_task), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 48.dp))
                }
                Stats(state.focusedTodayMs, state.focusedOnTaskMs)
            }
        }
    }
}

@Composable
private fun Setup(state: FocusUiState, viewModel: FocusViewModel) {
    val task = state.task ?: return
    Text(task.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.focus_setup_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            PlanEditor(state.draftPlan, viewModel::setDraft)
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = state.saveToTask, role = Role.Checkbox, onValueChange = viewModel::setSaveToTask)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.saveToTask, onCheckedChange = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.focus_save_for_task))
            }
        }
    }
    Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Rounded.PlayArrow, null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.focus_start), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RunningSession(active: FocusState, viewModel: FocusViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val extra = LocalExtraColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick while running. When the countdown hits zero the phase is advanced
    // right away, without waiting for the (possibly inexact) alarm.
    LaunchedEffect(active.running, active.phaseEndsAt) {
        while (active.running) {
            now = System.currentTimeMillis()
            if (active.remainingMs(now) <= 0) {
                viewModel.timeUp()
                break
            }
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    // Keep the screen on while a phase is running and this screen is visible.
    val view = LocalView.current
    DisposableEffect(active.running) {
        view.keepScreenOn = active.running
        onDispose { view.keepScreenOn = false }
    }

    val remaining = active.remainingMs(now)
    val total = active.phaseLengthMs().coerceAtLeast(1)
    val progress by animateFloatAsState((1f - remaining.toFloat() / total).coerceIn(0f, 1f), label = "progress")
    val ringColor = if (active.phase == FocusPhase.FOCUS) extra.focus else extra.breakTime
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val phaseText = FocusNotifications.phaseLabel(context, active.phase)
    val plan = active.decodedPlan
    val roundText = plan.totalRounds?.let { stringResource(R.string.focus_round_of, active.round, it) }
        ?: stringResource(R.string.focus_round, active.round)
    val timeText = FocusNotifications.formatMs(remaining)

    Text(active.taskTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Box(
        Modifier.fillMaxWidth(0.85f).widthIn(max = 340.dp).aspectRatio(1f)
            .semantics { contentDescription = "$phaseText, $timeText, $roundText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(ringColor, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(phaseText, style = MaterialTheme.typography.titleMedium, color = ringColor)
            Text(
                timeText,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                if (active.running) roundText else stringResource(R.string.focus_paused) + " · " + roundText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        FilledTonalIconButton(onClick = { viewModel.stop() }, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Rounded.Stop, stringResource(R.string.focus_stop), Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = { if (active.running) viewModel.pause() else viewModel.resume() },
            modifier = Modifier.size(96.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ringColor),
        ) {
            Icon(
                if (active.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (active.running) R.string.focus_pause else R.string.focus_resume),
                Modifier.size(48.dp),
            )
        }
        FilledTonalIconButton(onClick = { viewModel.skip() }, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.focus_skip), Modifier.size(32.dp))
        }
    }
    TextButton(onClick = { viewModel.completeTask(onDone) }) { Text(stringResource(R.string.focus_mark_done)) }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Stats(todayMs: Long, taskMs: Long) {
    if (todayMs <= 0 && taskMs <= 0) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(stringResource(R.string.focus_stat_today), formatDuration(todayMs))
            StatItem(stringResource(R.string.focus_stat_task), formatDuration(taskMs))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) stringResource(R.string.duration_h_m, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_m, minutes)
}
