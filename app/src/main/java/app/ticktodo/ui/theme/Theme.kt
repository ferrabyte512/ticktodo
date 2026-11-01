package app.ticktodo.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ticktodo.R
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.domain.model.Priority

// ---------------------------------------------------------------------------------------------
// Accent presets. Emerald is the default; the others are kept for people who chose them.
// ---------------------------------------------------------------------------------------------

val AppTheme.seed: Int
    get() = when (this) {
        AppTheme.EMERALD -> 0xFF079B48
        // Soft teal, between green, blue and cyan.
        AppTheme.LAGOON -> 0xFF4E9A9A
        AppTheme.APRICOT -> 0xFFE8743B
        AppTheme.SUNSET -> 0xFFD2553F
        AppTheme.HONEY -> 0xFFD39A2C
        AppTheme.ROSE -> 0xFFD9668F
        AppTheme.LAVENDER -> 0xFF8E7CC3
        AppTheme.OCEAN -> 0xFF4C8DD6
        AppTheme.JADE -> 0xFF5E9C7E
        AppTheme.GRAPHITE -> 0xFF5D6773
    }.toInt()

@get:StringRes
val AppTheme.label: Int
    get() = when (this) {
        AppTheme.EMERALD -> R.string.theme_emerald
        AppTheme.LAGOON -> R.string.theme_lagoon
        AppTheme.APRICOT -> R.string.theme_apricot
        AppTheme.SUNSET -> R.string.theme_terracotta
        AppTheme.HONEY -> R.string.theme_honey
        AppTheme.ROSE -> R.string.theme_rose
        AppTheme.LAVENDER -> R.string.theme_lavender
        AppTheme.OCEAN -> R.string.theme_ocean
        AppTheme.JADE -> R.string.theme_sage
        AppTheme.GRAPHITE -> R.string.theme_graphite
    }

/** Display order in the picker: the default first, then the main choices, then the rest. */
val AccentPresets = listOf(
    AppTheme.EMERALD, AppTheme.APRICOT, AppTheme.OCEAN, AppTheme.LAVENDER, AppTheme.ROSE,
    AppTheme.LAGOON, AppTheme.SUNSET, AppTheme.HONEY, AppTheme.JADE, AppTheme.GRAPHITE,
)

fun Appearance.seedColor(): Int = customAccent ?: accent.seed

// ---------------------------------------------------------------------------------------------
// Scheme construction
// ---------------------------------------------------------------------------------------------

/**
 * Every colour comes from the accent's hue. Dark mode is a deep shade of that
 * hue with a bright shade for buttons and the selected tab; light mode is the
 * same idea inverted: a pale tint for the page and a deep shade for buttons.
 * So a blue accent gives a navy page with light-blue buttons, a green accent a
 * forest page with mint buttons, and so on.
 */
private class Palette(seedArgb: Int) {
    private val hsv = ColorMath.toHsv(seedArgb)
    val hue: Float get() = hsv.hue

    /** Greyer accents (graphite) tint surfaces less; vivid ones a little more. */
    private val chroma = (hsv.saturation / 0.8f).coerceIn(0.25f, 1f)

    /** A shade of the accent hue; [s] is scaled by the accent's own vividness. */
    fun shade(s: Float, v: Float): Int = ColorMath.fromHsv(ColorMath.Hsv(hue, (s * chroma).coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/**
 * Builds a full Material 3 scheme from one accent colour. Every text-bearing
 * role is checked with [ColorMath.ensureContrast], so even a very light or very
 * dark custom accent produces readable buttons, labels and highlights.
 */
fun schemeFromSeed(seedArgb: Int, dark: Boolean): ColorScheme {
    val seed = ColorMath.withAlpha(seedArgb, 1f)
    val p = Palette(seed)
    val accent2 = ColorMath.shiftHue(seed, 35f)
    val p2 = Palette(accent2)
    return if (!dark) {
        val bg = p.shade(0.02f, 1f)
        // The accent itself, deepened only if it is too light to read on the pale page.
        val primary = ColorMath.ensureContrast(seed, bg, 4.5)
        val primaryContainer = p.shade(0.16f, 1f)
        val secondary = ColorMath.ensureContrast(p.shade(0.45f, 0.5f), bg, 4.5)
        // Selected-tab pill, chips and the soft selection colour.
        val secondaryContainer = p.shade(0.12f, 0.98f)
        val tertiary = ColorMath.ensureContrast(accent2, bg, 3.0)
        val tertiaryContainer = p2.shade(0.16f, 1f)
        val inverseSurface = p.shade(0.5f, 0.17f)
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(ColorMath.onColor(primary)),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(ColorMath.ensureContrast(p.shade(0.9f, 0.4f), primaryContainer, 4.5)),
            // Snackbar action colour; checked against the dark snackbar surface.
            inversePrimary = Color(ColorMath.ensureContrast(p.shade(0.6f, 0.95f), inverseSurface, 4.5)),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(p.shade(0.03f, 0.97f)),
            secondary = Color(secondary),
            onSecondary = Color(ColorMath.onColor(secondary)),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(ColorMath.ensureContrast(p.shade(0.9f, 0.38f), secondaryContainer, 4.5)),
            tertiary = Color(tertiary),
            onTertiary = Color(ColorMath.onColor(tertiary)),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(ColorMath.ensureContrast(p2.shade(0.9f, 0.38f), tertiaryContainer, 4.5)),
            background = Color(bg),
            onBackground = Color(p.shade(0.3f, 0.13f)),
            surface = Color(bg),
            onSurface = Color(p.shade(0.3f, 0.13f)),
            surfaceVariant = Color(p.shade(0.08f, 0.96f)),
            onSurfaceVariant = Color(p.shade(0.15f, 0.45f)),
            surfaceTint = Color(primary),
            surfaceContainerLowest = Color(WHITE),
            surfaceContainerLow = Color(p.shade(0.03f, 0.995f)),
            surfaceContainer = Color(p.shade(0.05f, 0.98f)),
            surfaceContainerHigh = Color(p.shade(0.07f, 0.965f)),
            surfaceContainerHighest = Color(p.shade(0.1f, 0.94f)),
            outline = Color(p.shade(0.12f, 0.58f)),
            outlineVariant = Color(p.shade(0.1f, 0.9f)),
        )
    } else {
        val bg = p.shade(0.68f, 0.075f)
        // A bright, luminous shade of the accent for buttons and the selected tab.
        val primary = ColorMath.ensureContrast(p.shade(0.85f, 0.9f), bg, 4.5)
        val primaryContainer = p.shade(0.7f, 0.3f)
        val secondary = ColorMath.ensureContrast(p.shade(0.35f, 0.8f), bg, 4.5)
        // Selected surface: a lighter panel of the same hue.
        val secondaryContainer = p.shade(0.65f, 0.26f)
        val tertiary = ColorMath.ensureContrast(p2.shade(0.75f, 0.9f), bg, 3.0)
        val tertiaryContainer = p2.shade(0.65f, 0.3f)
        val inverseSurface = p.shade(0.1f, 0.92f)
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(ColorMath.onColor(primary)),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(ColorMath.ensureContrast(p.shade(0.3f, 0.95f), primaryContainer, 4.5)),
            // Snackbar action colour; checked against the light snackbar surface.
            inversePrimary = Color(ColorMath.ensureContrast(p.shade(0.9f, 0.5f), inverseSurface, 4.5)),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(p.shade(0.3f, 0.13f)),
            secondary = Color(secondary),
            onSecondary = Color(ColorMath.onColor(secondary)),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(ColorMath.ensureContrast(p.shade(0.25f, 0.97f), secondaryContainer, 4.5)),
            tertiary = Color(tertiary),
            onTertiary = Color(ColorMath.onColor(tertiary)),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(ColorMath.ensureContrast(p2.shade(0.25f, 0.97f), tertiaryContainer, 4.5)),
            background = Color(bg),
            onBackground = Color(p.shade(0.02f, 0.97f)),
            surface = Color(bg),
            onSurface = Color(p.shade(0.02f, 0.97f)),
            surfaceVariant = Color(p.shade(0.7f, 0.24f)),
            onSurfaceVariant = Color(p.shade(0.09f, 0.71f)),
            surfaceTint = Color(primary),
            surfaceContainerLowest = Color(p.shade(0.6f, 0.05f)),
            surfaceContainerLow = Color(p.shade(0.59f, 0.125f)),
            surfaceContainer = Color(p.shade(0.6f, 0.16f)),
            surfaceContainerHigh = Color(p.shade(0.58f, 0.19f)),
            surfaceContainerHighest = Color(p.shade(0.55f, 0.23f)),
            outline = Color(p.shade(0.18f, 0.52f)),
            outlineVariant = Color(p.shade(0.47f, 0.26f)),
        )
    }
}

/** Smoothly animates every role when the theme, accent or dark mode changes. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun Color.a(label: String) = animateColorAsState(this, tween(durationMillis = 450), label = label).value
    return copy(
        primary = primary.a("primary"),
        onPrimary = onPrimary.a("onPrimary"),
        primaryContainer = primaryContainer.a("primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.a("onPrimaryContainer"),
        secondary = secondary.a("secondary"),
        onSecondary = onSecondary.a("onSecondary"),
        secondaryContainer = secondaryContainer.a("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.a("onSecondaryContainer"),
        tertiary = tertiary.a("tertiary"),
        tertiaryContainer = tertiaryContainer.a("tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.a("onTertiaryContainer"),
        background = background.a("background"),
        onBackground = onBackground.a("onBackground"),
        surface = surface.a("surface"),
        onSurface = onSurface.a("onSurface"),
        surfaceVariant = surfaceVariant.a("surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.a("onSurfaceVariant"),
        surfaceContainerLowest = surfaceContainerLowest.a("lowest"),
        surfaceContainerLow = surfaceContainerLow.a("low"),
        surfaceContainer = surfaceContainer.a("container"),
        surfaceContainerHigh = surfaceContainerHigh.a("high"),
        surfaceContainerHighest = surfaceContainerHighest.a("highest"),
        outline = outline.a("outline"),
        outlineVariant = outlineVariant.a("outlineVariant"),
    )
}

// ---------------------------------------------------------------------------------------------
// Extra roles and appearance state
// ---------------------------------------------------------------------------------------------

@Immutable
data class ExtraColors(
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val overdue: Color,
    /** Completed counts and ticks. */
    val success: Color,
    /** Focus progress ring; follows the accent. */
    val focus: Color,
    /** Break progress ring; follows the second accent. */
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

private val LightLists = listOf(
    0xFFE5484D, 0xFF3285E8, 0xFF079B48, 0xFF1FA587, 0xFFB44FD6, 0xFFD8960F,
    0xFFE86C2C, 0xFF6B7280, 0xFF0F8FA8, 0xFF8B5CF6, 0xFFD6336C, 0xFF5B7F2B,
).map(::Color)

private val DarkLists = listOf(
    0xFFFF777D, 0xFF64B5FF, 0xFF45E88A, 0xFF5FD8BD, 0xFFD18BFF, 0xFFFFC45C,
    0xFFFF9A6A, 0xFFB0B8C4, 0xFF5CD0E8, 0xFFB8A6FF, 0xFFFF80AB, 0xFFA8D96A,
).map(::Color)

private fun extraColors(scheme: ColorScheme, dark: Boolean) = ExtraColors(
    priorityHigh = if (dark) Color(0xFFFF777D) else Color(0xFFE5484D),
    priorityMedium = if (dark) Color(0xFFFFC45C) else Color(0xFFD8960F),
    priorityLow = if (dark) Color(0xFF64B5FF) else Color(0xFF3285E8),
    overdue = if (dark) Color(0xFFFF777D) else Color(0xFFE5484D),
    success = if (dark) Color(0xFF45E88A) else Color(0xFF078C43),
    focus = scheme.primary,
    breakTime = scheme.tertiary,
    lists = if (dark) DarkLists else LightLists,
)

val LocalExtraColors = staticCompositionLocalOf { extraColors(schemeFromSeed(AppTheme.EMERALD.seed, false), false) }

/** What components need to know to draw themselves over a wallpaper. */
@Immutable
data class AppearanceState(
    val appearance: Appearance = Appearance(),
    val dark: Boolean = false,
    /** Opaque page colour; the real background when a wallpaper hides the scheme's one. */
    val pageColor: Color = Color.White,
    /** Final (non-animating) page colour, used for work that should not repeat during a theme fade. */
    val pageTarget: Color = pageColor,
) {
    val hasWallpaper: Boolean get() = appearance.hasWallpaper
    val glass: Boolean get() = appearance.hasWallpaper && appearance.glassCards
}

val LocalAppearance = staticCompositionLocalOf { AppearanceState() }

// ---------------------------------------------------------------------------------------------
// Typography and shapes
// ---------------------------------------------------------------------------------------------

private val Base = Typography()

/** Default font with firmer headings, in the spirit of a clean editorial layout. */
val AppTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ---------------------------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------------------------

@Composable
fun TickTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appearance: Appearance = Appearance(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val target: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> schemeFromSeed(appearance.seedColor(), dark)
    }
    val scheme = target.animated()
    val page = scheme.background

    // Status-bar and navigation-bar icons follow the app's own light/dark choice,
    // not the system's, so the clock stays readable when the two differ.
    val view = LocalView.current
    val activity = LocalActivity.current
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    // Over a wallpaper, screens and app bars become transparent. Dialogs, menus,
    // sheets and cards use the surfaceContainer roles, which stay opaque.
    val finalScheme = if (appearance.hasWallpaper) scheme.copy(background = Color.Transparent, surface = Color.Transparent) else scheme
    CompositionLocalProvider(
        LocalExtraColors provides extraColors(scheme, dark),
        LocalAppearance provides AppearanceState(appearance, dark, page, target.background),
    ) {
        MaterialTheme(colorScheme = finalScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
