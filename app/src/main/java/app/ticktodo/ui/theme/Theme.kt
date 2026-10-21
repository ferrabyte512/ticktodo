package app.ticktodo.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ticktodo.R
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.domain.model.Priority

/** Key colours of a built-in theme; the full Material scheme is derived from them. */
data class ThemeSpec(
    @StringRes val name: Int,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightAccent: Color,
    val darkAccent: Color,
)

val AppTheme.spec: ThemeSpec
    get() = when (this) {
        AppTheme.JADE -> ThemeSpec(R.string.theme_jade, Color(0xFF1E6B57), Color(0xFF8CD5BB), Color(0xFF8A5100), Color(0xFFFFB86F))
        AppTheme.OCEAN -> ThemeSpec(R.string.theme_ocean, Color(0xFF1F5FA8), Color(0xFFA6C8FF), Color(0xFF00696E), Color(0xFF4FD8DE))
        AppTheme.SUNSET -> ThemeSpec(R.string.theme_sunset, Color(0xFFB0401F), Color(0xFFFFB59F), Color(0xFF7C5800), Color(0xFFF5BF48))
        AppTheme.LAVENDER -> ThemeSpec(R.string.theme_lavender, Color(0xFF6750A4), Color(0xFFD0BCFF), Color(0xFF7D5260), Color(0xFFEFB8C8))
        AppTheme.ROSE -> ThemeSpec(R.string.theme_rose, Color(0xFFA3305F), Color(0xFFFFB0CC), Color(0xFF7A5900), Color(0xFFEFC048))
        AppTheme.GRAPHITE -> ThemeSpec(R.string.theme_graphite, Color(0xFF3F4A55), Color(0xFFBCC8D4), Color(0xFF5B6000), Color(0xFFC5CA6B))
    }

private val Ink = Color(0xFF1A1C1E)
private val Paper = Color(0xFFE2E2E5)
private val Night = Color(0xFF111316)

private fun lightSchemeFor(s: ThemeSpec): ColorScheme {
    val p = s.lightPrimary
    val secondary = lerp(p, Color(0xFF5E6368), 0.5f)
    fun tint(f: Float) = lerp(Color.White, p, f)
    return lightColorScheme(
        primary = p,
        onPrimary = Color.White,
        primaryContainer = lerp(p, Color.White, 0.80f),
        onPrimaryContainer = lerp(p, Color.Black, 0.65f),
        inversePrimary = s.darkPrimary,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = lerp(secondary, Color.White, 0.80f),
        onSecondaryContainer = lerp(secondary, Color.Black, 0.65f),
        tertiary = s.lightAccent,
        onTertiary = Color.White,
        tertiaryContainer = lerp(s.lightAccent, Color.White, 0.80f),
        onTertiaryContainer = lerp(s.lightAccent, Color.Black, 0.65f),
        background = tint(0.025f),
        onBackground = Ink,
        surface = tint(0.025f),
        onSurface = Ink,
        surfaceVariant = tint(0.12f),
        onSurfaceVariant = lerp(Color(0xFF44474B), p, 0.15f),
        surfaceTint = p,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tint(0.045f),
        surfaceContainer = tint(0.065f),
        surfaceContainerHigh = tint(0.09f),
        surfaceContainerHighest = tint(0.12f),
        outline = lerp(Color(0xFF74777B), p, 0.2f),
        outlineVariant = lerp(Color(0xFFC4C6CA), p, 0.15f),
    )
}

private fun darkSchemeFor(s: ThemeSpec): ColorScheme {
    val p = s.darkPrimary
    val secondary = lerp(p, Color(0xFFC4C7CB), 0.5f)
    fun tint(f: Float) = lerp(Night, s.lightPrimary, f)
    return darkColorScheme(
        primary = p,
        onPrimary = lerp(s.lightPrimary, Color.Black, 0.6f),
        primaryContainer = lerp(s.lightPrimary, Color.Black, 0.3f),
        onPrimaryContainer = lerp(p, Color.White, 0.6f),
        inversePrimary = s.lightPrimary,
        secondary = secondary,
        onSecondary = lerp(secondary, Color.Black, 0.75f),
        secondaryContainer = lerp(secondary, Night, 0.65f),
        onSecondaryContainer = lerp(secondary, Color.White, 0.6f),
        tertiary = s.darkAccent,
        onTertiary = lerp(s.darkAccent, Color.Black, 0.75f),
        tertiaryContainer = lerp(s.lightAccent, Color.Black, 0.35f),
        onTertiaryContainer = lerp(s.darkAccent, Color.White, 0.6f),
        background = tint(0.04f),
        onBackground = Paper,
        surface = tint(0.04f),
        onSurface = Paper,
        surfaceVariant = tint(0.2f),
        onSurfaceVariant = lerp(Color(0xFFC4C6CA), p, 0.15f),
        surfaceTint = p,
        surfaceContainerLowest = Color(0xFF0C0E10),
        surfaceContainerLow = tint(0.07f),
        surfaceContainer = tint(0.09f),
        surfaceContainerHigh = tint(0.13f),
        surfaceContainerHighest = tint(0.17f),
        outline = lerp(Color(0xFF8E9195), p, 0.2f),
        outlineVariant = lerp(Color(0xFF44474B), p, 0.15f),
    )
}

@Immutable
data class ExtraColors(
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val overdue: Color,
    val focus: Color,
    val breakTime: Color,
    val lists: List<Color>,
) {
    fun priority(p: Priority): Color? = when (p) {
        Priority.HIGH -> priorityHigh
        Priority.MEDIUM -> priorityMedium
        Priority.LOW -> priorityLow
        Priority.NONE -> null
    }

    fun list(index: Int): Color = lists[index.coerceIn(0, lists.lastIndex)]
}

private val LightExtra = ExtraColors(
    priorityHigh = Color(0xFFC0392B),
    priorityMedium = Color(0xFFB86E00),
    priorityLow = Color(0xFF3567A8),
    overdue = Color(0xFFB3261E),
    focus = Color(0xFFC0392B),
    breakTime = Color(0xFF2E7D32),
    lists = listOf(
        0xFF1E6B57, 0xFF3567A8, 0xFF7A4EB3, 0xFFB0447A, 0xFFC0392B, 0xFFB86E00,
        0xFF7C7A00, 0xFF3F7F2B, 0xFF00838F, 0xFF5C6B73, 0xFF8D6E63, 0xFF455A64,
    ).map(::Color),
)

private val DarkExtra = ExtraColors(
    priorityHigh = Color(0xFFFF8A7A),
    priorityMedium = Color(0xFFFFB86F),
    priorityLow = Color(0xFF9DC1FF),
    overdue = Color(0xFFFFB4AB),
    focus = Color(0xFFFF8A7A),
    breakTime = Color(0xFF8FD694),
    lists = listOf(
        0xFF8CD5BB, 0xFF9DC1FF, 0xFFD3B8FF, 0xFFFFAFD4, 0xFFFF8A7A, 0xFFFFB86F,
        0xFFDCD86A, 0xFFA5D98E, 0xFF7FD8E2, 0xFFB7C6CE, 0xFFD7BBB2, 0xFFA9BAC3,
    ).map(::Color),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** Rounder, friendlier shapes; task cards use `large`. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TickTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appTheme: AppTheme = AppTheme.JADE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkSchemeFor(appTheme.spec)
        else -> lightSchemeFor(appTheme.spec)
    }
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}
