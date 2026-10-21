package app.ticktodo.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.ui.common.TaskRow
import app.ticktodo.ui.theme.TickTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TaskRowTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 21)

    private fun item(plan: FocusPlan? = null) = TaskItem(
        Task("id-1", null, null, "Write report", "Draft section 2", Priority.HIGH, today, null, null, null, listOf("work"), plan, false, null, 0, 0, false),
        subtaskTotal = 3,
        subtaskDone = 1,
    )

    @Test fun `card shows title, notes preview and meta, and the check circle completes`() {
        var completed = 0
        var opened = 0
        compose.setContent {
            TickTheme {
                TaskRow(item(), today, null, false, false, { completed++ }, { opened++ }, {}, {})
            }
        }
        compose.onNodeWithText("Write report").assertIsDisplayed()
        compose.onNodeWithText("Draft section 2").assertIsDisplayed()
        compose.onNodeWithText("1/3").assertIsDisplayed()
        compose.onNodeWithText("#work").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark complete: Write report").performClick()
        assertEquals(1, completed)
    }

    @Test fun `focus button only appears for tasks with a focus plan`() {
        var started = 0
        compose.setContent {
            TickTheme {
                TaskRow(item(FocusPlan.DEFAULT), today, null, false, false, {}, {}, {}, {}, onStartFocus = { started++ })
            }
        }
        compose.onNodeWithContentDescription("Start focus: Write report").performClick()
        assertEquals(1, started)
    }

    @Test fun `swipe actions are offered to screen readers as custom actions`() {
        compose.setContent {
            TickTheme { TaskRow(item(), today, null, false, false, {}, {}, {}, {}) }
        }
        val actions = compose.onNodeWithText("Write report", useUnmergedTree = false)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Mark complete", "Delete"), actions)
    }
}
