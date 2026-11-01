package app.ticktodo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.repository.InvalidInputException
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.TaskDraft
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.testing.FakeClock
import app.ticktodo.testing.FakeSession
import app.ticktodo.testing.RecordingEffects
import app.ticktodo.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TaskRepositoryTest {
    private val db = inMemoryDb()
    private val clock = FakeClock()
    private val session = FakeSession("alice")
    private val effects = RecordingEffects()
    private val repo = TaskRepository(db, session, clock, effects)
    private val today = clock.today()

    @After fun close() = db.close()

    @Test fun `create validates and stores a dirty task`() = runBlocking<Unit> {
        val id = repo.create(TaskDraft("  Plan trip  ", tags = listOf("#Travel"), dueDate = today))
        val t = repo.observeTask(id).first()!!
        assertEquals("Plan trip", t.title)
        assertEquals(listOf("travel"), t.tags)
        assertTrue(t.pendingSync)
        assertTrue(id in effects.changed)
        assertFailsWith<InvalidInputException> { repo.create(TaskDraft("   ")) }
    }

    @Test fun `today view includes overdue tasks, upcoming does not`() = runBlocking<Unit> {
        repo.create(TaskDraft("overdue", dueDate = today.minusDays(1)))
        repo.create(TaskDraft("today", dueDate = today))
        repo.create(TaskDraft("later", dueDate = today.plusDays(3)))
        assertEquals(setOf("overdue", "today"), repo.observeScope(TaskScope.Today, today).first().map { it.task.title }.toSet())
        assertEquals(listOf("later"), repo.observeScope(TaskScope.Upcoming, today).first().map { it.task.title })
    }

    @Test fun `reminders and repeat are dropped without a due date`() = runBlocking<Unit> {
        val id = repo.create(TaskDraft("x", reminderOffsetMinutes = 10, recurrence = Recurrence(Recurrence.Frequency.DAILY)))
        val t = repo.observeTask(id).first()!!
        assertNull(t.reminderOffsetMinutes)
        assertNull(t.recurrence)
    }

    @Test fun `completing a repeating task creates the next one and undo removes it`() = runBlocking<Unit> {
        val id = repo.create(TaskDraft("Gym", dueDate = today, recurrence = Recurrence(Recurrence.Frequency.DAILY)))
        val change = repo.setCompleted(id, true)!!
        val next = repo.observeTask(change.nextOccurrenceId!!).first()
        assertNotNull(next)
        assertEquals(today.plusDays(1), next.dueDate)
        assertEquals(TaskRepository.nextOccurrenceId(id), next.id)

        repo.undoCompletion(change)
        assertEquals(false, repo.observeTask(id).first()!!.isCompleted)
        assertNull(repo.observeTask(change.nextOccurrenceId!!).first())
    }

    @Test fun `deleting a task deletes its subtasks and undo restores both`() = runBlocking<Unit> {
        val parent = repo.create(TaskDraft("Parent"))
        val child = repo.create(TaskDraft("Child", parentId = parent))
        val affected = repo.delete(listOf(parent))
        assertEquals(setOf(parent, child), affected.toSet())
        assertNull(repo.observeTask(child).first())
        repo.restore(affected)
        assertNotNull(repo.observeTask(child).first())
    }

    @Test fun `deleting a list moves its tasks to the inbox`() = runBlocking<Unit> {
        val list = repo.createList("Work", 2)
        val id = repo.create(TaskDraft("Report", listId = list))
        repo.deleteList(list)
        assertNull(repo.observeTask(id).first()!!.listId)
        assertEquals(listOf("Report"), repo.observeScope(TaskScope.Inbox, today).first().map { it.task.title })
    }

    @Test fun `finishing the last task of a list or of the day is celebrated`() = runBlocking<Unit> {
        val celebrations = app.ticktodo.ui.common.Celebrations(db, session, clock)
        val events = mutableListOf<app.ticktodo.ui.common.Celebration>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { celebrations.events.collect { events += it } }

        val list = repo.createList("Work", 2)
        val first = repo.create(TaskDraft("Report", listId = list))
        val second = repo.create(TaskDraft("Slides", listId = list))
        repo.setCompleted(first, true); celebrations.afterCompletion(first)
        assertTrue(events.isEmpty())
        repo.setCompleted(second, true); celebrations.afterCompletion(second)
        assertEquals(listOf<app.ticktodo.ui.common.Celebration>(app.ticktodo.ui.common.Celebration.ListDone("Work")), events)

        val today1 = repo.create(TaskDraft("Call", dueDate = today))
        repo.create(TaskDraft("Tomorrow", dueDate = today.plusDays(1)))
        repo.setCompleted(today1, true); celebrations.afterCompletion(today1)
        assertEquals(app.ticktodo.ui.common.Celebration.DayDone, events.last())
        job.cancel()
    }

    @Test fun `inbox differs from all tasks only while an open task is in a list`() = runBlocking<Unit> {
        repo.create(TaskDraft("Loose"))
        val list = repo.createList("Work", 2)
        assertEquals(false, repo.observeHasListedTasks().first())
        val id = repo.create(TaskDraft("Report", listId = list))
        assertEquals(true, repo.observeHasListedTasks().first())
        repo.setCompleted(id, true)
        assertEquals(false, repo.observeHasListedTasks().first())
    }

    @Test fun `search treats wildcards literally`() = runBlocking<Unit> {
        repo.create(TaskDraft("50% off sale"))
        repo.create(TaskDraft("500 emails"))
        assertEquals(listOf("50% off sale"), repo.search("50%").first().map { it.task.title })
    }

    @Test fun `another account's rows are invisible and writes require a session`() = runBlocking<Unit> {
        repo.create(TaskDraft("Alice task"))
        session.uid.value = "bob"
        assertEquals(emptyList(), repo.observeScope(TaskScope.All, today).first())
        session.uid.value = null
        assertFailsWith<IllegalStateException> { repo.create(TaskDraft("nobody")) }
    }

    @Test fun `upgrading database rows keeps focus plan support`() = runBlocking<Unit> {
        val plan = app.ticktodo.domain.focus.FocusPlan(15, 3)
        val id = repo.create(TaskDraft("Read", focusPlan = plan))
        val t = repo.observeTask(id).first()!!
        assertEquals(plan, t.focusPlan)
        repo.update(t.copy(focusPlan = null))
        assertNull(repo.observeTask(id).first()!!.focusPlan)
        assertEquals(LocalDate.ofEpochDay(today.toEpochDay()), today)
    }
}
