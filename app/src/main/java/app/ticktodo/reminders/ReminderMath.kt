package app.ticktodo.reminders

import app.ticktodo.data.local.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure reminder timing rules, kept separate from AlarmManager so they can be
 * unit tested.
 *
 * Due dates and times are "floating": 09:00 means 09:00 wherever the device
 * is, which is what people expect from a personal to-do list when travelling.
 */
object ReminderMath {

    /** A reminder missed while the device was off is still shown if it is at most this old. */
    const val MISSED_GRACE_MS = 6L * 60 * 60 * 1000

    sealed interface Plan {
        data object None : Plan
        data class At(val triggerAt: Long, val reminderTime: Long) : Plan
    }

    fun baseTrigger(task: TaskEntity, zone: ZoneId, defaultMinute: Int): Long? {
        val day = task.dueEpochDay ?: return null
        val offset = task.reminderOffsetMinutes ?: return null
        val minute = task.dueMinuteOfDay ?: defaultMinute
        val at = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(zone)
        return at.toInstant().toEpochMilli() - offset * 60_000L
    }

    /** The time this task should remind next: a snooze overrides the regular time. */
    fun reminderTime(task: TaskEntity, zone: ZoneId, defaultMinute: Int): Long? =
        task.snoozedUntil ?: baseTrigger(task, zone, defaultMinute)

    fun plan(task: TaskEntity, now: Long, zone: ZoneId, defaultMinute: Int, catchUpMissed: Boolean): Plan {
        if (task.deleted || task.completed) return Plan.None
        val time = reminderTime(task, zone, defaultMinute) ?: return Plan.None
        if (time == task.lastNotifiedTrigger) return Plan.None
        if (time > now) return Plan.At(time, time)
        val lastShown = task.lastNotifiedTrigger ?: Long.MIN_VALUE
        if (catchUpMissed && now - time <= MISSED_GRACE_MS && time > lastShown) return Plan.At(now + 1_000, time)
        return Plan.None
    }
}
