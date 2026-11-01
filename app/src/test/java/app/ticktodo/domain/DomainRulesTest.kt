package app.ticktodo.domain

import app.ticktodo.data.local.TaskEntity
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.Recurrence.Frequency
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.domain.model.TaskSort
import app.ticktodo.reminders.ReminderMath
import app.ticktodo.ui.tasks.QuickAddParser
import app.ticktodo.ui.tasks.SectionHeader
import app.ticktodo.ui.tasks.TaskGrouping
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurrenceTest {
    private val today = LocalDate.of(2026, 10, 21) // Wednesday

    @Test fun `daily, weekly, monthly and yearly steps`() {
        val d = LocalDate.of(2026, 10, 21)
        assertEquals(d.plusDays(1), RecurrenceCalculator.nextDueDate(d, Recurrence(Frequency.DAILY), today))
        assertEquals(d.plusWeeks(2), RecurrenceCalculator.nextDueDate(d, Recurrence(Frequency.WEEKLY, 2), today))
        assertEquals(d.plusMonths(1), RecurrenceCalculator.nextDueDate(d, Recurrence(Frequency.MONTHLY), today))
        assertEquals(d.plusYears(1), RecurrenceCalculator.nextDueDate(d, Recurrence(Frequency.YEARLY), today))
    }

    @Test fun `weekdays skip the weekend`() {
        val friday = LocalDate.of(2026, 10, 23)
        val rule = Recurrence(Frequency.WEEKLY, 1, Recurrence.WEEKDAYS)
        assertEquals(LocalDate.of(2026, 10, 26), RecurrenceCalculator.nextDueDate(friday, rule, friday))
    }

    @Test fun `specific days within the same week`() {
        val monday = LocalDate.of(2026, 10, 19)
        val rule = Recurrence(Frequency.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 10, 22), RecurrenceCalculator.nextDueDate(monday, rule, monday))
    }

    @Test fun `month end clamps`() {
        val jan31 = LocalDate.of(2027, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), RecurrenceCalculator.nextDueDate(jan31, Recurrence(Frequency.MONTHLY), jan31))
    }

    @Test fun `monthly and yearly rules return to their anchor day after a short month`() {
        val jan31 = LocalDate.of(2027, 1, 31)
        val monthly = Recurrence(Frequency.MONTHLY).anchoredTo(jan31)
        assertEquals(31, monthly.dayOfMonth)
        val feb = RecurrenceCalculator.nextDueDate(jan31, monthly, jan31)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), RecurrenceCalculator.nextDueDate(feb, monthly, feb))
        assertEquals(LocalDate.of(2027, 4, 30), RecurrenceCalculator.nextDueDate(LocalDate.of(2027, 3, 31), monthly, LocalDate.of(2027, 3, 31)))

        val leap = LocalDate.of(2028, 2, 29)
        val yearly = Recurrence(Frequency.YEARLY).anchoredTo(leap)
        val next = RecurrenceCalculator.nextDueDate(leap, yearly, leap)
        assertEquals(LocalDate.of(2029, 2, 28), next)
        assertEquals(LocalDate.of(2032, 2, 29), RecurrenceCalculator.nextDueDate(LocalDate.of(2031, 2, 28), yearly, LocalDate.of(2031, 2, 28)))

        // Rules saved by older versions have no anchor and keep the old stepping behaviour.
        assertEquals(LocalDate.of(2027, 3, 28), RecurrenceCalculator.nextDueDate(feb, Recurrence(Frequency.MONTHLY), feb))
        // Weekly rules never get an anchor.
        assertNull(Recurrence(Frequency.WEEKLY).anchoredTo(jan31).dayOfMonth)
    }

    @Test fun `anchor day round-trips through the encoded rule and is validated`() {
        val rule = Recurrence(Frequency.MONTHLY, 2, dayOfMonth = 31)
        assertEquals("FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=31", rule.encode())
        assertEquals(rule, Recurrence.parse(rule.encode()))
        assertNull(Recurrence.parse("FREQ=WEEKLY;INTERVAL=1;BYMONTHDAY=5"))
        assertNull(Recurrence.parse("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=32"))
    }

    @Test fun `overdue repeating task jumps to today or later, not into the past`() {
        val old = LocalDate.of(2026, 9, 1)
        assertEquals(today, RecurrenceCalculator.nextDueDate(old, Recurrence(Frequency.DAILY), today))
    }

    @Test fun `rule round-trips and rejects malformed input`() {
        val rule = Recurrence(Frequency.WEEKLY, 3, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(rule, Recurrence.parse(rule.encode()))
        assertNull(Recurrence.parse("FREQ=HOURLY"))
        assertNull(Recurrence.parse("FREQ=DAILY;BYDAY=MO"))
        assertNull(Recurrence.parse("FREQ=DAILY;INTERVAL=0"))
        assertNull(Recurrence.parse("FREQ=WEEKLY;BYDAY=XX"))
    }
}

class ValidationTest {
    @Test fun `titles are trimmed, single line and free of control characters`() {
        assertEquals("Buy milk now", TaskValidator.cleanTitle("  Buy\u0000 milk\nnow  "))
        assertEquals(TaskValidator.Error.EmptyTitle, TaskValidator.validateTitle("   "))
        assertEquals(TaskValidator.Error.TitleTooLong, TaskValidator.validateTitle("x".repeat(501)))
    }

    @Test fun `tags are normalised and invalid ones dropped`() {
        assertEquals(listOf("work", "home"), TaskValidator.cleanTags(listOf("#Work", "home", "WORK", "bad tag", "", "a,b")))
        assertEquals(TaskValidator.MAX_TAGS, TaskValidator.cleanTags((1..40).map { "t$it" }).size)
    }

    @Test fun `LIKE wildcards in search text are escaped`() {
        assertEquals("%50\\%\\_off%", TaskValidator.likePattern("50%_off"))
    }

    @Test fun `quick add extracts hashtags`() {
        val p = QuickAddParser.parse("Call mum #family #Phone tomorrow")
        assertEquals("Call mum tomorrow", p.title)
        assertEquals(listOf("family", "phone"), p.tags)
        assertEquals("email#1 stays", QuickAddParser.parse("email#1 stays").title)
    }
}

class ConflictResolverTest {
    private fun meta(t: Long, dirty: Boolean) = ConflictResolver.Meta(t, dirty)

    @Test fun `clean local rows always take the server version`() {
        assertEquals(ConflictResolver.Decision.TAKE_REMOTE, ConflictResolver.resolve(meta(500, false), 100))
        assertEquals(ConflictResolver.Decision.TAKE_REMOTE, ConflictResolver.resolve(null, 100))
    }

    @Test fun `newer unsynced local edit wins, older or equal loses`() {
        assertEquals(ConflictResolver.Decision.KEEP_LOCAL, ConflictResolver.resolve(meta(200, true), 100))
        assertEquals(ConflictResolver.Decision.TAKE_REMOTE, ConflictResolver.resolve(meta(100, true), 200))
        assertEquals(ConflictResolver.Decision.TAKE_REMOTE, ConflictResolver.resolve(meta(100, true), 100))
    }

    @Test fun `edit time always moves past the version being edited, even with a slow clock`() {
        assertEquals(1_001, ConflictResolver.nextEditTime(now = 500, previous = 1_000))
        assertEquals(2_000, ConflictResolver.nextEditTime(now = 2_000, previous = 1_000))
    }
}

class ReminderMathTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 21)
    private fun entity(minute: Int?, offset: Int?, notified: Long? = null, snooze: Long? = null) = TaskEntity(
        id = "t", ownerUid = "u", listId = null, parentId = null, title = "x", notes = "", priority = 0,
        dueEpochDay = day.toEpochDay(), dueMinuteOfDay = minute, reminderOffsetMinutes = offset, recurrenceRule = null,
        tags = "", completed = false, completedAt = null, createdAt = 0, updatedAt = 0, deleted = false, dirty = false,
        localRevision = 0, serverUpdatedAt = null, snoozedUntil = snooze, lastNotifiedTrigger = notified,
    )
    private val at9 = day.atTime(9, 0).toInstant(zone).toEpochMilli()

    @Test fun `reminder time is due time minus offset, all-day tasks use the default time`() {
        assertEquals(at9 - 15 * 60_000, ReminderMath.baseTrigger(entity(9 * 60, 15), zone, 8 * 60))
        assertEquals(day.atTime(8, 0).toInstant(zone).toEpochMilli(), ReminderMath.baseTrigger(entity(null, 0), zone, 8 * 60))
        assertNull(ReminderMath.baseTrigger(entity(9 * 60, null), zone, 540))
    }

    @Test fun `future reminders are scheduled, already shown ones are not`() {
        assertEquals(ReminderMath.Plan.At(at9, at9), ReminderMath.plan(entity(540, 0), at9 - 1, zone, 540, false))
        assertEquals(ReminderMath.Plan.None, ReminderMath.plan(entity(540, 0, notified = at9), at9 - 1, zone, 540, false))
    }

    @Test fun `missed reminders are only caught up after a reboot and within the grace period`() {
        val now = at9 + 60 * 60_000
        assertEquals(ReminderMath.Plan.None, ReminderMath.plan(entity(540, 0), now, zone, 540, catchUpMissed = false))
        assertEquals(ReminderMath.Plan.At(now + 1_000, at9), ReminderMath.plan(entity(540, 0), now, zone, 540, catchUpMissed = true))
        assertEquals(ReminderMath.Plan.None, ReminderMath.plan(entity(540, 0), at9 + 7 * 60 * 60_000, zone, 540, catchUpMissed = true))
    }

    @Test fun `snooze overrides the regular time`() {
        val snooze = at9 + 15 * 60_000
        assertEquals(ReminderMath.Plan.At(snooze, snooze), ReminderMath.plan(entity(540, 0, notified = at9, snooze = snooze), at9 + 1, zone, 540, false))
    }
}

class TaskGroupingTest {
    private val today = LocalDate.of(2026, 10, 21)
    private fun item(id: String, due: LocalDate?, priority: Priority = Priority.NONE, created: Long = 0, order: Double? = null) = TaskItem(
        Task(id, null, null, id, "", priority, due, null, null, null, emptyList(), null, false, null, created, created, false, sortOrder = order), 0, 0,
    )

    @Test fun `today view splits overdue from today`() {
        val sections = TaskGrouping.group(TaskScope.Today, listOf(item("a", today), item("b", today.minusDays(2))), today, TaskSort.DUE_DATE)
        assertEquals(listOf(SectionHeader.Overdue, SectionHeader.Today), sections.map { it.header })
        assertEquals("b", sections[0].items.single().task.id)
    }

    @Test fun `upcoming groups by day in date order`() {
        val sections = TaskGrouping.group(
            TaskScope.Upcoming,
            listOf(item("a", today.plusDays(3)), item("b", today.plusDays(1)), item("c", today.plusDays(3))),
            today,
            TaskSort.DUE_DATE,
        )
        assertEquals(listOf(today.plusDays(1), today.plusDays(3)), sections.map { (it.header as SectionHeader.Day).date })
    }

    @Test fun `my order puts never-moved tasks newest first, moved tasks by position`() {
        val sorted = TaskGrouping.sort(
            listOf(item("old", null, created = 1_000), item("new", null, created = 2_000), item("pinned", null, created = 0, order = -5_000.0)),
            TaskSort.MANUAL,
        )
        assertEquals(listOf("pinned", "new", "old"), sorted.map { it.task.id })
    }

    @Test fun `dropping between two tasks changes only the moved task`() {
        val a = item("a", null, order = 0.0); val b = item("b", null, order = 100.0); val c = item("c", null, order = 200.0)
        // c dragged between a and b.
        assertEquals(mapOf("c" to 50.0), TaskGrouping.manualOrderUpdates(listOf(a, c, b), "c"))
        // a dragged to the bottom, b to the top.
        assertEquals(60_200.0, TaskGrouping.manualOrderUpdates(listOf(b, c, a), "a")["a"]!!, 0.0)
        assertEquals(listOf("b"), TaskGrouping.manualOrderUpdates(listOf(b, a, c), "b").keys.toList())
        assert(TaskGrouping.manualOrderUpdates(listOf(b, a, c), "b")["b"]!! < 0.0)
    }

    @Test fun `neighbours with no room between them renumber the section in its new order`() {
        val a = item("a", null, order = 1.0); val b = item("b", null, order = 1.0); val c = item("c", null, order = 1.0)
        val updates = TaskGrouping.manualOrderUpdates(listOf(a, c, b), "c")
        val reordered = TaskGrouping.sort(listOf(a, b, c).map { it.copy(task = it.task.copy(sortOrder = updates[it.task.id])) }, TaskSort.MANUAL)
        assertEquals(listOf("a", "c", "b"), reordered.map { it.task.id })
    }

    @Test fun `priority sort puts high first`() {
        val sorted = TaskGrouping.sort(listOf(item("low", null, Priority.LOW), item("high", null, Priority.HIGH)), TaskSort.PRIORITY)
        assertEquals("high", sorted.first().task.id)
    }
}
