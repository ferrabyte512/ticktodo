package app.ticktodo.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.SettingsRepository
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.data.settings.Wallpaper
import app.ticktodo.ui.appearance.AppearanceContent
import app.ticktodo.ui.appearance.ColorPickerContent
import app.ticktodo.ui.appearance.ColorPickerState
import app.ticktodo.ui.appearance.isValidHex
import app.ticktodo.ui.theme.ColorMath
import app.ticktodo.ui.theme.TickTheme
import app.ticktodo.ui.theme.WallpaperStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppearancePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = SettingsRepository(context)

    @Test fun `appearance choices persist, clamp and reset`() = runBlocking<Unit> {
        repo.resetAppearance()
        assertEquals(Appearance(), repo.current().appearance)

        repo.setCustomAccent(0x3366CC)
        repo.setWallpaper(Wallpaper.MOUNTAINS)
        repo.setOverlay(5f)
        repo.setBlur(-3f)
        repo.setGlassCards(false)
        repo.setCardOpacity(0.1f)
        var a = repo.current().appearance
        assertEquals(0xFF3366CC.toInt(), a.customAccent, "custom accent is stored opaque")
        assertEquals(Wallpaper.MOUNTAINS, a.wallpaper)
        assertEquals(Appearance.OVERLAY_RANGE.endInclusive, a.overlay)
        assertEquals(0f, a.blurDp)
        assertFalse(a.glassCards)
        assertEquals(Appearance.CARD_OPACITY_RANGE.start, a.cardOpacity)

        repo.setGradient(0x80FF0000.toInt(), 0xFF00FF00.toInt())
        a = repo.current().appearance
        assertEquals(Wallpaper.GRADIENT, a.wallpaper)
        assertEquals(0x80FF0000.toInt(), a.gradientStart, "gradient keeps its alpha")

        val before = a.photoVersion
        repo.photoImported()
        a = repo.current().appearance
        assertEquals(Wallpaper.PHOTO, a.wallpaper)
        assertEquals(before + 1, a.photoVersion)

        repo.setAppTheme(AppTheme.OCEAN)
        a = repo.current().appearance
        assertEquals(AppTheme.OCEAN, a.accent)
        assertNull(a.customAccent, "choosing a preset clears the custom accent")

        repo.setThemeMode(ThemeMode.DARK)
        repo.resetAppearance()
        val after = repo.settings.first()
        assertEquals(Appearance(photoVersion = after.appearance.photoVersion), after.appearance)
        assertEquals(ThemeMode.DARK, after.themeMode, "reset keeps light/dark choice")
    }

    @Test fun `missing or corrupt photos decode to null instead of crashing`() {
        assertNull(WallpaperStore.decode(File(context.filesDir, "nope.jpg"), 1024))
        val junk = File(context.filesDir, "junk.jpg").apply { writeText("not an image") }
        assertNull(WallpaperStore.decode(junk, 1024))
    }

    @Test fun `large photos are downsampled`() {
        assertEquals(1, WallpaperStore.sampleSize(1000, 800, 2048))
        assertEquals(2, WallpaperStore.sampleSize(4096, 3072, 2048))
        assertEquals(4, WallpaperStore.sampleSize(9000, 6000, 2048))
        val file = File(context.filesDir, "big.jpg")
        Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE8743B.toInt()) }
            .let { bmp -> file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val decoded = assertNotNull(WallpaperStore.decode(file, 512))
        assertTrue(decoded.width <= 1200 && decoded.width >= 300)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ColorPickerTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: ColorPickerState, alpha: Boolean) = compose.setContent {
        TickTheme { ColorPickerContent(state, original = 0xFFE8743B.toInt(), showAlpha = alpha, presets = emptyList()) }
    }

    @Test fun `typing a valid HEX updates the colour, invalid input shows an error and keeps it`() {
        val state = ColorPickerState(0xFFE8743B.toInt())
        show(state, alpha = false)
        compose.onNodeWithTag("hexField").performTextClearance()
        compose.onNodeWithTag("hexField").performTextInput("#3366CC")
        compose.waitForIdle()
        assertEquals(0xFF3366CC.toInt(), state.argb)

        compose.onNodeWithTag("hexField").performTextClearance()
        compose.onNodeWithTag("hexField").performTextInput("#12")
        compose.waitForIdle()
        compose.onNodeWithText("Enter 3, 6 or 8 hex digits", useUnmergedTree = true).assertExists()
        assertEquals(0xFF3366CC.toInt(), state.argb)
    }

    @Test fun `opacity slider and eight-digit HEX control alpha`() {
        val state = ColorPickerState(0xFFE8743B.toInt())
        show(state, alpha = true)
        compose.onNodeWithTag("opacitySlider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.waitForIdle()
        assertEquals(0x80, ColorMath.alpha(state.argb))
        assertEquals(0xE8743B, state.argb and 0xFFFFFF, "changing opacity keeps the colour")

        compose.onNodeWithTag("hexField").performTextClearance()
        compose.onNodeWithTag("hexField").performTextInput("#4000FF00")
        compose.waitForIdle()
        assertEquals(0x4000FF00, state.argb)
    }

    @Test fun `hue ring is adjustable through accessibility and keeps saturation and brightness`() {
        val state = ColorPickerState(0xFFFF0000.toInt())
        show(state, alpha = false)
        compose.onNodeWithTag("colorWheel").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.waitForIdle()
        assertEquals(0xFF00FF00.toInt(), state.argb)
    }

    @Test fun `grey keeps the previous hue so dragging saturation back restores the colour`() {
        val state = ColorPickerState(0xFF2266DD.toInt())
        val hue = state.hue
        state.saturation = 0f
        state.set(state.argb)
        assertEquals(hue, state.hue)
    }

    @Test fun `hex validation rules`() {
        assertTrue(isValidHex("#ABC", false))
        assertTrue(isValidHex("ABCDEF", false))
        assertFalse(isValidHex("#80ABCDEF", false))
        assertTrue(isValidHex("#80ABCDEF", true))
        assertFalse(isValidHex("#ABCD", true))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppearanceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `selecting a wallpaper and resetting call through, overlay controls appear with a wallpaper`() {
        var picked: Wallpaper? = null
        var reset = false
        var settings = AppSettings()
        compose.setContent {
            TickTheme(appearance = settings.appearance) {
                AppearanceContent(
                    settings = settings, photoAvailable = false, importing = false,
                    onThemeMode = {}, onAccent = {}, onCustomAccent = {},
                    onWallpaper = { picked = it }, onEditGradientStart = {}, onEditGradientEnd = {},
                    onOverlay = {}, onBlur = {}, onGlass = {}, onCardOpacity = {}, onDynamic = {},
                    onReset = { reset = true },
                )
            }
        }
        compose.onNodeWithTag("livePreview").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper_MOUNTAINS").performScrollTo().performClick()
        assertEquals(Wallpaper.MOUNTAINS, picked)
        compose.onNodeWithText("Reset to default look").performScrollTo().performClick()
        assertTrue(reset)
    }

    @Test fun `overlay slider is shown only when a wallpaper is set`() {
        compose.setContent {
            val s = AppSettings(appearance = Appearance(wallpaper = Wallpaper.FOREST))
            TickTheme(appearance = s.appearance) {
                AppearanceContent(
                    settings = s, photoAvailable = false, importing = false,
                    onThemeMode = {}, onAccent = {}, onCustomAccent = {}, onWallpaper = {},
                    onEditGradientStart = {}, onEditGradientEnd = {}, onOverlay = {}, onBlur = {},
                    onGlass = {}, onCardOpacity = {}, onDynamic = {}, onReset = {},
                )
            }
        }
        compose.onNodeWithTag("overlaySlider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cardOpacitySlider").performScrollTo().assertIsDisplayed()
    }
}
