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
import androidx.compose.ui.graphics.toArgb
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
// Accent presets: rich but light colours that sit well on warm off-white.
// ---------------------------------------------------------------------------------------------

val AppTheme.seed: Int
    get() = when (this) {
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
        AppTheme.APRICOT -> R.string.theme_apricot
        AppTheme.SUNSET -> R.string.theme_terracotta
        AppTheme.HONEY -> R.string.theme_honey
        AppTheme.ROSE -> R.string.theme_rose
        AppTheme.LAVENDER -> R.string.theme_lavender
        AppTheme.OCEAN -> R.string.theme_ocean
        AppTheme.JADE -> R.string.theme_sage
        AppTheme.GRAPHITE -> R.string.theme_graphite
    }

/** Display order in the picker: warm first. */
val AccentPresets = listOf(
    AppTheme.APRICOT, AppTheme.SUNSET, AppTheme.HONEY, AppTheme.ROSE,
    AppTheme.LAVENDER, AppTheme.OCEAN, AppTheme.JADE, AppTheme.GRAPHITE,
)

fun Appearance.seedColor(): Int = customAccent ?: accent.seed

// ---------------------------------------------------------------------------------------------
// Scheme construction
// ---------------------------------------------------------------------------------------------

private object Neutrals {
    // Warm off-white and beige for light mode.
    const val L_BG = 0xFFFBF8F4.toInt()
    const val L_LOWEST = 0xFFFFFFFF.toInt()
    const val L_LOW = 0xFFF8F3ED.toInt()
    const val L_MID = 0xFFF3EDE5.toInt()
    const val L_HIGH = 0xFFEEE7DE.toInt()
    const val L_HIGHEST = 0xFFE8E0D6.toInt()
    const val L_VARIANT = 0xFFEFE6DC.toInt()
    const val L_ON = 0xFF24201C.toInt()
    const val L_ON_VARIANT = 0xFF5B534B.toInt()
    const val L_OUTLINE = 0xFF877D73.toInt()
    const val L_OUTLINE_VARIANT = 0xFFDCD2C7.toInt()

    // Warm, soft dark mode.
    const val D_BG = 0xFF161311.toInt()
    const val D_LOWEST = 0xFF100E0C.toInt()
    const val D_LOW = 0xFF1E1A17.toInt()
    const val D_MID = 0xFF231F1B.toInt()
    const val D_HIGH = 0xFF2D2824.toInt()
    const val D_HIGHEST = 0xFF38322D.toInt()
    const val D_VARIANT = 0xFF3A342E.toInt()
    const val D_ON = 0xFFEEE7E0.toInt()
    const val D_ON_VARIANT = 0xFFD0C6BC.toInt()
    const val D_OUTLINE = 0xFF9B9188.toInt()
    const val D_OUTLINE_VARIANT = 0xFF4A433D.toInt()
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
    val n = Neutrals
    fun tint(base: Int, amount: Float = 0.035f) = Color(ColorMath.blend(base, seed, amount))
    val accent2 = ColorMath.shiftHue(seed, 35f)
    return if (!dark) {
        val bg = ColorMath.blend(n.L_BG, seed, 0.03f)
        val primary = ColorMath.ensureContrast(seed, bg, 4.5)
        val primaryContainer = ColorMath.blend(seed, WHITE, 0.78f)
        val secondaryBase = ColorMath.blend(seed, 0xFF7C726A.toInt(), 0.45f)
        val secondary = ColorMath.ensureContrast(secondaryBase, bg, 4.5)
        val secondaryContainer = ColorMath.blend(seed, bg, 0.84f)
        val tertiary = ColorMath.ensureContrast(accent2, bg, 3.0)
        val tertiaryContainer = ColorMath.blend(accent2, WHITE, 0.8f)
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(ColorMath.onColor(primary)),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(seed, BLACK, 0.55f), primaryContainer, 4.5)),
            inversePrimary = Color(ColorMath.blend(seed, WHITE, 0.4f)),
            secondary = Color(secondary),
            onSecondary = Color(ColorMath.onColor(secondary)),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(seed, BLACK, 0.6f), secondaryContainer, 4.5)),
            tertiary = Color(tertiary),
            onTertiary = Color(ColorMath.onColor(tertiary)),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(accent2, BLACK, 0.6f), tertiaryContainer, 4.5)),
            background = Color(bg),
            onBackground = Color(n.L_ON),
            surface = Color(bg),
            onSurface = Color(n.L_ON),
            surfaceVariant = tint(n.L_VARIANT),
            onSurfaceVariant = Color(n.L_ON_VARIANT),
            surfaceTint = Color(primary),
            surfaceContainerLowest = Color(n.L_LOWEST),
            surfaceContainerLow = tint(n.L_LOW),
            surfaceContainer = tint(n.L_MID),
            surfaceContainerHigh = tint(n.L_HIGH),
            surfaceContainerHighest = tint(n.L_HIGHEST),
            outline = Color(n.L_OUTLINE),
            outlineVariant = tint(n.L_OUTLINE_VARIANT, 0.06f),
        )
    } else {
        val bg = ColorMath.blend(n.D_BG, seed, 0.03f)
        val primary = ColorMath.ensureContrast(ColorMath.blend(seed, WHITE, 0.2f), bg, 4.5)
        val primaryContainer = ColorMath.blend(seed, bg, 0.62f)
        val secondaryBase = ColorMath.blend(seed, 0xFFC9BFB6.toInt(), 0.5f)
        val secondary = ColorMath.ensureContrast(secondaryBase, bg, 4.5)
        val secondaryContainer = ColorMath.blend(seed, bg, 0.75f)
        val tertiary = ColorMath.ensureContrast(ColorMath.blend(accent2, WHITE, 0.2f), bg, 3.0)
        val tertiaryContainer = ColorMath.blend(accent2, bg, 0.65f)
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(ColorMath.onColor(primary)),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(seed, WHITE, 0.6f), primaryContainer, 4.5)),
            inversePrimary = Color(ColorMath.blend(seed, BLACK, 0.3f)),
            secondary = Color(secondary),
            onSecondary = Color(ColorMath.onColor(secondary)),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(seed, WHITE, 0.65f), secondaryContainer, 4.5)),
            tertiary = Color(tertiary),
            onTertiary = Color(ColorMath.onColor(tertiary)),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(ColorMath.ensureContrast(ColorMath.blend(accent2, WHITE, 0.65f), tertiaryContainer, 4.5)),
            background = Color(bg),
            onBackground = Color(n.D_ON),
            surface = Color(bg),
            onSurface = Color(n.D_ON),
            surfaceVariant = tint(n.D_VARIANT, 0.05f),
            onSurfaceVariant = Color(n.D_ON_VARIANT),
            surfaceTint = Color(primary),
            surfaceContainerLowest = Color(n.D_LOWEST),
            surfaceContainerLow = tint(n.D_LOW, 0.05f),
            surfaceContainer = tint(n.D_MID, 0.05f),
            surfaceContainerHigh = tint(n.D_HIGH, 0.05f),
            surfaceContainerHighest = tint(n.D_HIGHEST, 0.05f),
            outline = Color(n.D_OUTLINE),
            outlineVariant = Color(n.D_OUTLINE_VARIANT),
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
    0xFFE8743B, 0xFF3567A8, 0xFF7A4EB3, 0xFFB0447A, 0xFFC0392B, 0xFFB86E00,
    0xFF7C7A00, 0xFF3F7F2B, 0xFF00838F, 0xFF5C6B73, 0xFF8D6E63, 0xFF1E6B57,
).map(::Color)

private val DarkLists = listOf(
    0xFFFFB38A, 0xFF9DC1FF, 0xFFD3B8FF, 0xFFFFAFD4, 0xFFFF8A7A, 0xFFFFB86F,
    0xFFDCD86A, 0xFFA5D98E, 0xFF7FD8E2, 0xFFB7C6CE, 0xFFD7BBB2, 0xFF8CD5BB,
).map(::Color)

private fun extraColors(scheme: ColorScheme, dark: Boolean) = ExtraColors(
    priorityHigh = if (dark) Color(0xFFFF8A7A) else Color(0xFFC0392B),
    priorityMedium = if (dark) Color(0xFFFFB86F) else Color(0xFFB86E00),
    priorityLow = if (dark) Color(0xFF9DC1FF) else Color(0xFF3567A8),
    overdue = if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E),
    focus = scheme.primary,
    breakTime = scheme.tertiary,
    lists = if (dark) DarkLists else LightLists,
)

val LocalExtraColors = staticCompositionLocalOf { extraColors(schemeFromSeed(AppTheme.APRICOT.seed, false), false) }

/** What components need to know to draw themselves over a wallpaper. */
@Immutable
data class AppearanceState(
    val appearance: Appearance = Appearance(),
    val dark: Boolean = false,
    /** Opaque page colour; the real background when a wallpaper hides the scheme's one. */
    val pageColor: Color = Color(Neutrals.L_BG),
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
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
    // Over a wallpaper, screens and app bars become transparent. Dialogs, menus,
    // sheets and cards use the surfaceContainer roles, which stay opaque.
    val finalScheme = if (appearance.hasWallpaper) scheme.copy(background = Color.Transparent, surface = Color.Transparent) else scheme
    CompositionLocalProvider(
        LocalExtraColors provides extraColors(scheme, dark),
        LocalAppearance provides AppearanceState(appearance, dark, page),
    ) {
        MaterialTheme(colorScheme = finalScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** For callers that only need the accent colour as an ARGB int (e.g. previews). */
fun Color.argb(): Int = toArgb()
