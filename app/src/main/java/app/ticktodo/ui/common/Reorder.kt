package app.ticktodo.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a LazyColumn without extra libraries. The caller keeps
 * the on-screen order: [onMove] moves the dragged key to the target key's
 * place (returning false to refuse, e.g. across sections) and the list
 * re-lays out; this state only tracks the finger offset.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val edgePx: Float,
    private val onMove: (from: Any, to: Any) -> Boolean,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    /** Layout offset of the dragged item when the last move was made; until the list re-lays out, no new move. */
    private var offsetAtMove: Int? = null

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
        offsetAtMove = null
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        offset += dy
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        if (offsetAtMove == current.offset) return
        offsetAtMove = null

        val center = current.offset + offset + current.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != key && center >= it.offset && center <= it.offset + it.size
        }
        if (target != null && onMove(key, target.key)) {
            offset += current.offset - target.offset
            offsetAtMove = current.offset
            return
        }

        // Scroll when the dragged card reaches the top or bottom edge.
        val top = current.offset + offset
        val bottom = top + current.size
        val scroll = when {
            top < info.viewportStartOffset + edgePx && dy < 0 -> dy
            bottom > info.viewportEndOffset - edgePx && dy > 0 -> dy
            else -> 0f
        }
        if (scroll != 0f) offset += listState.dispatchRawDelta(scroll)
    }

    /** Returns the key that was dragged, if any. */
    fun end(): Any? {
        val key = draggingKey
        draggingKey = null
        offset = 0f
        offsetAtMove = null
        return key
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (from: Any, to: Any) -> Boolean): ReorderState {
    val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
    val latestOnMove by rememberUpdatedState(onMove)
    return remember(listState) { ReorderState(listState, edgePx) { from, to -> latestOnMove(from, to) } }
}

/** Put on the drag handle: dragging it moves the item with [key]. */
fun Modifier.reorderHandle(state: ReorderState, key: Any, onDrop: (Any) -> Unit): Modifier = pointerInput(key) {
    detectDragGestures(
        onDragStart = { state.start(key) },
        onDragEnd = { state.end()?.let(onDrop) },
        onDragCancel = { state.end()?.let(onDrop) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}

/** Lifts the dragged item above its neighbours and moves it with the finger. */
fun Modifier.reorderItem(state: ReorderState, key: Any): Modifier =
    if (state.draggingKey == key) {
        zIndex(1f).graphicsLayer {
            translationY = state.offset
            scaleX = 1.02f
            scaleY = 1.02f
            shadowElevation = 12f
        }
    } else {
        this
    }
