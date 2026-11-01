package app.ticktodo.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ticktodo.ui.theme.LocalAppearance

/** Card colour for the current appearance: translucent over a wallpaper when glass cards are on. */
@Composable
fun glassColor(base: Color = MaterialTheme.colorScheme.surfaceContainerLow): Color {
    val state = LocalAppearance.current
    return if (state.glass) base.copy(alpha = state.appearance.cardOpacity) else base
}

/**
 * Card used across the app. Over a wallpaper with glass cards on, it is a
 * translucent panel with a fine light edge; otherwise a plain tonal card.
 * Compose has no backdrop blur, so the frosted look comes from the (optionally
 * blurred) wallpaper plus translucency.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val state = LocalAppearance.current
    // Cards are outlined with a fine border rather than lifted with shadows.
    val border = if (state.glass) {
        BorderStroke(1.dp, if (state.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.65f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = glassColor(color),
        tonalElevation = if (state.glass) tonalElevation else 0.dp,
        border = border,
        content = content,
    )
}
