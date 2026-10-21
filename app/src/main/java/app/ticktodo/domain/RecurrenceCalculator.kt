package app.ticktodo.domain

import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.Recurrence.Frequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Works out the next due date of a repeating task.
 *
 * Rules:
 *  - The next date is always computed from the previous due date, not from the
 *    completion date, so a weekly Monday task stays on Mondays.
 *  - If the computed date is still before [today] (the task was overdue for
 *    several periods) we keep stepping forward until it is today or later, so
 *    completing an old daily task does not create a pile of overdue copies.
 *  - Monthly rules on the 29th–31st clamp to the last day of shorter months.
 */
object RecurrenceCalculator {

    private const val MAX_STEPS = 5_000

    fun nextDueDate(current: LocalDate, rule: Recurrence, today: LocalDate): LocalDate {
        var next = step(current, rule)
        var steps = 0
        while (next.isBefore(today) && steps < MAX_STEPS) {
            next = step(next, rule)
            steps++
        }
        return next
    }

    private fun step(from: LocalDate, rule: Recurrence): LocalDate = when (rule.frequency) {
        Frequency.DAILY -> from.plusDays(rule.interval.toLong())
        Frequency.MONTHLY -> from.plusMonths(rule.interval.toLong())
        Frequency.YEARLY -> from.plusYears(rule.interval.toLong())
        Frequency.WEEKLY ->
            if (rule.daysOfWeek.isEmpty()) from.plusWeeks(rule.interval.toLong())
            else nextWeeklyByDay(from, rule.interval, rule.daysOfWeek)
    }

    private fun nextWeeklyByDay(from: LocalDate, interval: Int, days: Set<DayOfWeek>): LocalDate {
        val anchorWeek = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var candidate = from.plusDays(1)
        // Worst case: interval weeks of gap plus one full week of scanning.
        repeat(7 * (interval + 1)) {
            val week = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksApart = ChronoUnit.WEEKS.between(anchorWeek, week)
            if (weeksApart % interval == 0L && candidate.dayOfWeek in days) return candidate
            candidate = candidate.plusDays(1)
        }
        return from.plusWeeks(interval.toLong())
    }
}
