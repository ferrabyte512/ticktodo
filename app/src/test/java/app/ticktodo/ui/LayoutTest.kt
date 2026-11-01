package app.ticktodo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.settings.Appearance
import app.ticktodo.data.settings.ThemeMode
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.ui.common.ListBadge
import app.ticktodo.ui.common.TaskRow
import app.ticktodo.ui.focus.PlanEditor
import app.ticktodo.ui.tasks.DateStrip
import app.ticktodo.ui.tasks.TodayDashboard
import app.ticktodo.ui.tasks.TodayFilter
import app.ticktodo.ui.theme.TickTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The main components laid out at the sizes Play's device catalogue covers:
 * a small phone, a tablet in landscape, and a phone with the largest font
 * setting. Each must compose without error and keep its key controls on
 * screen; the dashboard's counts and the row's title must remain visible.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LayoutTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 1)

    private fun item(id: String, title: String, p: Priority, tags: List<String>) = TaskItem(
        Task(id, null, null, title, "Notes line that is fairly long so it must truncate", p, today, null, 15, null, tags, FocusPlan.DEFAULT, false, null, 0, 0, false),
        3, 1,
    )

    @Composable
    private fun Screen(fontScale: Float = 1f, dark: Boolean = false) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            TickTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, appearance = Appearance()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    TodayDashboard(done = 2, remaining = 3, overdue = 1, filter = TodayFilter.ALL, onFilter = {})
                    DateStrip(today, setOf(today.plusDays(2)), null, {})
                    TaskRow(
                        item("1", "Write the quarterly report for the whole leadership team", Priority.HIGH, listOf("work", "important", "q4")),
                        today, ListBadge("Work projects", 3), selectionMode = false, selected = false,
                        onToggleComplete = {}, onClick = {}, onLongClick = {}, onDelete = {}, onStartFocus = {},
                    )
                    PlanEditor(FocusPlan.DEFAULT, {})
                }
            }
        }
    }

    private fun assertCoreVisible() {
        compose.onNodeWithText("2/6").assertIsDisplayed()
        compose.onNodeWithText("3 Remaining").assertIsDisplayed()
        compose.onNodeWithText("Write the quarterly report for the whole leadership team").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start focus: Write the quarterly report for the whole leadership team").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h570dp-mdpi")
    fun small_phone() {
        compose.setContent { Screen() }
        assertCoreVisible()
    }

    @Test
    @Config(qualifiers = "w960dp-h600dp-land-xhdpi")
    fun tablet_landscape() {
        compose.setContent { Screen(dark = true) }
        assertCoreVisible()
    }

    @Test
    @Config(qualifiers = "w400dp-h820dp-xhdpi")
    fun largest_font_setting() {
        compose.setContent { Screen(fontScale = 2.0f) }
        assertCoreVisible()
        compose.onNodeWithText("Focus").assertIsDisplayed()
        compose.onNodeWithText("25 min").assertIsDisplayed()
    }
}
