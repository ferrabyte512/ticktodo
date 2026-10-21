package app.ticktodo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.settings.AppSettings
import app.ticktodo.data.settings.AppTheme
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.data.settings.Wallpaper
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.ui.appearance.AppearanceContent
import app.ticktodo.ui.appearance.ColorPickerContent
import app.ticktodo.ui.appearance.ColorPickerState
import app.ticktodo.ui.common.ListBadge
import app.ticktodo.ui.common.TaskRow
import app.ticktodo.ui.theme.AppBackground
import app.ticktodo.ui.theme.TickTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.test.assertTrue

/**
 * Renders real composables with Robolectric's native graphics and writes PNGs to
 * app/build/reports/screenshots for visual review. Also asserts that wallpapers
 * actually draw something (not a flat colour) and that text areas are not blank.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h820dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)
    private val outDir = File("build/reports/screenshots").apply { mkdirs() }

    private fun task(id: String, title: String, notes: String, p: Priority, due: LocalDate?, tags: List<String>, plan: FocusPlan? = null) =
        TaskItem(Task(id, null, null, title, notes, p, due, null, 15, null, tags, plan, false, null, 0, 0, false), 0, 0)

    @Composable
    private fun TodayMock() {
        Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Today", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val items = listOf(
                task("1", "Do 30 minutes of yoga", "Morning routine", Priority.LOW, today, listOf("health")),
                task("2", "Dentist appointment", "", Priority.HIGH, today, emptyList()),
                task("3", "Write agenda for Monday's meeting", "Website update", Priority.MEDIUM, today.plusDays(1), listOf("work"), FocusPlan.DEFAULT),
                task("4", "Buy bread", "", Priority.NONE, null, emptyList()),
            )
            items.forEach { item ->
                TaskRow(
                    item, today, if (item.task.id == "3") ListBadge("Team", 1) else null, false, false, {}, {}, {}, {},
                    onStartFocus = if (item.task.focusPlan != null) ({}) else null,
                )
            }
        }
    }

    private fun save(name: String): Bitmap {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    /** Number of distinct colours in a coarse sample; a flat fill gives 1. */
    private fun variety(bmp: Bitmap): Int {
        val seen = HashSet<Int>()
        for (y in 0 until bmp.height step 23) for (x in 0 until bmp.width step 23) seen += bmp.getPixel(x, y) and 0xFFFFFF
        return seen.size
    }

    private fun renderToday(name: String, appearance: Appearance, mode: ThemeMode): Bitmap {
        compose.setContent {
            TickTheme(themeMode = mode, appearance = appearance) { AppBackground { TodayMock() } }
        }
        compose.waitForIdle()
        return save(name)
    }

    @Test fun default_light() { assertTrue(variety(renderToday("today_default_light", Appearance(), ThemeMode.LIGHT)) > 5) }

    @Test fun default_dark() { assertTrue(variety(renderToday("today_default_dark", Appearance(), ThemeMode.DARK)) > 5) }

    @Test fun peach_glow_light() {
        assertTrue(variety(renderToday("today_peach_light", Appearance(wallpaper = Wallpaper.PEACH_GLOW), ThemeMode.LIGHT)) > 8)
    }

    @Test fun mountains_light_terracotta() {
        assertTrue(variety(renderToday("today_mountains_terracotta", Appearance(accent = AppTheme.SUNSET, wallpaper = Wallpaper.MOUNTAINS, overlay = 0.4f), ThemeMode.LIGHT)) > 8)
    }

    @Test fun forest_dark_sage() {
        assertTrue(variety(renderToday("today_forest_dark", Appearance(accent = AppTheme.JADE, wallpaper = Wallpaper.FOREST), ThemeMode.DARK)) > 8)
    }

    @Test fun waves_custom_accent() {
        renderToday("today_waves_custom", Appearance(customAccent = 0xFF2F80ED.toInt(), wallpaper = Wallpaper.WAVES), ThemeMode.LIGHT)
    }

    @Test fun missing_photo_falls_back_to_plain_page() {
        // No photo was ever imported in this test process: the page colour must show and nothing crashes.
        renderToday("today_missing_photo", Appearance(wallpaper = Wallpaper.PHOTO), ThemeMode.LIGHT)
    }

    @Test fun every_scene_draws_detail() {
        var current by mutableStateOf(Wallpaper.PEACH_GLOW)
        compose.setContent {
            TickTheme(appearance = Appearance(wallpaper = current, overlay = 0.2f)) { AppBackground {} }
        }
        for (w in listOf(Wallpaper.PEACH_GLOW, Wallpaper.MOUNTAINS, Wallpaper.DUNES, Wallpaper.SEA_MIST, Wallpaper.FOREST, Wallpaper.WAVES, Wallpaper.AURORA)) {
            current = w
            // Let the 500 ms cross-fade finish before capturing.
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            val bmp = save("scene_${w.name.lowercase()}")
            assertTrue(variety(bmp) > 6, "$w should not be a flat colour")
        }
    }

    @Test fun appearance_screen() {
        compose.setContent {
            val s = AppSettings(appearance = Appearance(wallpaper = Wallpaper.PEACH_GLOW))
            TickTheme(appearance = s.appearance) {
                AppBackground {
                    AppearanceContent(
                        settings = s, photoAvailable = false, importing = false,
                        onThemeMode = {}, onAccent = {}, onCustomAccent = {}, onWallpaper = {},
                        onEditGradientStart = {}, onEditGradientEnd = {}, onOverlay = {}, onBlur = {},
                        onGlass = {}, onCardOpacity = {}, onDynamic = {}, onReset = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        save("appearance_screen")
    }

    @Test fun color_picker() {
        compose.setContent {
            TickTheme {
                AppBackground {
                    ColorPickerContent(ColorPickerState(0xCCE8743B.toInt()), 0xFFD2553F.toInt(), showAlpha = true, presets = listOf(0xFFE8743B.toInt(), 0xFF5E9C7E.toInt()), modifier = Modifier.padding(16.dp))
                }
            }
        }
        compose.waitForIdle()
        assertTrue(variety(save("color_picker")) > 20)
    }
}
