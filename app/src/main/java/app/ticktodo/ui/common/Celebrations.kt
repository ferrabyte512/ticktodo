package app.ticktodo.ui.common

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed interface Celebration {
    /** The last open task in a list was completed. */
    data class ListDone(val listName: String) : Celebration
    /** Nothing is left due today or overdue. */
    data object DayDone : Celebration
}

/**
 * Encouragement after completing tasks: a varied snackbar line for every task,
 * and a bigger moment when a whole list or the day's tasks are finished.
 */
@Singleton
class Celebrations @Inject constructor(
    private val db: AppDatabase,
    private val session: UserSession,
    private val clock: Clock,
) {
    private val _events = MutableSharedFlow<Celebration>(extraBufferCapacity = 2)
    val events: SharedFlow<Celebration> = _events.asSharedFlow()

    /** A short, varied confirmation for one completed task. */
    @StringRes
    fun taskDoneMessage(): Int = TASK_DONE[Random.nextInt(TASK_DONE.size)]

    /** Call after [taskId] was marked complete; emits a celebration if that finished a list or the day. */
    suspend fun afterCompletion(taskId: String) {
        val uid = session.uid.value ?: return
        val dao = db.taskDao()
        val task = dao.get(uid, taskId) ?: return
        if (!task.completed || task.parentId != null) return
        val listId = task.listId
        if (listId != null && dao.countOpenInList(uid, listId) == 0) {
            val name = db.taskListDao().get(uid, listId)?.takeUnless { it.deleted }?.name
            if (name != null) {
                _events.tryEmit(Celebration.ListDone(name))
                return
            }
        }
        val today = clock.today().toEpochDay()
        val due = task.dueEpochDay
        if (due != null && due <= today && dao.countOpenDueBy(uid, today) == 0) {
            _events.tryEmit(Celebration.DayDone)
        }
    }

    private companion object {
        val TASK_DONE = listOf(R.string.msg_done_1, R.string.msg_done_2, R.string.msg_done_3, R.string.msg_done_4)
    }
}

/** Card with falling confetti, shown over the app for a few seconds. Tap to close. */
@Composable
fun CelebrationOverlay(celebration: Celebration, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(celebration) {
        if (animate) progress.animateTo(1f, tween(DURATION_MS.toInt(), easing = LinearEasing)) else delay(DURATION_MS)
        onDismiss()
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFFF2B33D),
        Color(0xFF5BB98C),
    )
    val pieces = remember { List(70) { Confetti.random() } }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        if (animate) {
            Canvas(Modifier.fillMaxSize()) {
                val t = progress.value
                pieces.forEachIndexed { i, p ->
                    val y = (p.startY + t * p.speed) * size.height
                    if (y > size.height) return@forEachIndexed
                    val x = (p.x + kotlin.math.sin((t * p.sway + p.phase) * 6.28f) * 0.04f) * size.width
                    rotate(p.spin * t * 720f, Offset(x, y)) {
                        drawRect(colors[i % colors.size], topLeft = Offset(x - 6f, y - 3f), size = Size(12f, 6f), alpha = 1f - t * 0.6f)
                    }
                }
            }
        }
        val (title, body) = when (celebration) {
            is Celebration.ListDone -> stringResource(R.string.celebrate_list_title) to
                stringResource(R.string.celebrate_list_body, celebration.listName)
            Celebration.DayDone -> stringResource(R.string.celebrate_day_title) to stringResource(R.string.celebrate_day_body)
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(32.dp).widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
}

private const val DURATION_MS = 3_500L

private class Confetti(val x: Float, val startY: Float, val speed: Float, val sway: Float, val phase: Float, val spin: Float) {
    companion object {
        fun random() = Confetti(
            x = Random.nextFloat(),
            startY = -Random.nextFloat() * 0.5f,
            speed = 0.9f + Random.nextFloat() * 0.8f,
            sway = 0.5f + Random.nextFloat(),
            phase = Random.nextFloat(),
            spin = Random.nextFloat() - 0.5f,
        )
    }
}
