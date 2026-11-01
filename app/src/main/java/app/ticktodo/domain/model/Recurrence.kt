package app.ticktodo.domain.model

import java.time.DayOfWeek

/**
 * A repeat rule. Stored as a small subset of the iCalendar RRULE syntax
 * (FREQ, INTERVAL, BYDAY) so it stays readable and could be exported later.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    /**
     * For monthly and yearly rules, the day of month the task started on
     * (BYMONTHDAY). A task due on the 31st falls on the 28th in February but
     * returns to the 31st in March; without the anchor it would drift to the 28th
     * for good. Null for rules saved by older versions, which keep stepping from
     * the previous date.
     */
    val dayOfMonth: Int? = null,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    init {
        require(interval in 1..MAX_INTERVAL) { "interval out of range" }
        require(daysOfWeek.isEmpty() || frequency == Frequency.WEEKLY) { "BYDAY only applies to weekly rules" }
        require(dayOfMonth == null || (dayOfMonth in 1..31 && (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY))) {
            "BYMONTHDAY only applies to monthly and yearly rules"
        }
    }

    /** The rule with its anchor day set from [date], if it needs one and has none. */
    fun anchoredTo(date: java.time.LocalDate): Recurrence =
        if (dayOfMonth == null && (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY)) copy(dayOfMonth = date.dayOfMonth) else this

    fun encode(): String = buildString {
        append("FREQ=").append(frequency.name)
        append(";INTERVAL=").append(interval)
        if (daysOfWeek.isNotEmpty()) {
            append(";BYDAY=")
            append(daysOfWeek.sorted().joinToString(",") { DAY_CODES.getValue(it) })
        }
        if (dayOfMonth != null) append(";BYMONTHDAY=").append(dayOfMonth)
    }

    val isWeekdays: Boolean
        get() = frequency == Frequency.WEEKLY && interval == 1 && daysOfWeek == WEEKDAYS

    companion object {
        const val MAX_INTERVAL = 365

        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )

        private val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_DAYS = DAY_CODES.entries.associate { (k, v) -> v to k }

        /** Parses an encoded rule. Returns null for blank or malformed input instead of throwing. */
        fun parse(value: String?): Recurrence? {
            if (value.isNullOrBlank() || value.length > 100) return null
            val parts = value.split(';').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }.toMap()
            val frequency = parts["FREQ"]?.let { f -> Frequency.entries.firstOrNull { it.name == f } } ?: return null
            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
            if (interval !in 1..MAX_INTERVAL) return null
            val days = parts["BYDAY"]?.split(',')?.filter { it.isNotBlank() }?.map { CODE_DAYS[it] ?: return null }?.toSet()
                ?: emptySet()
            if (days.isNotEmpty() && frequency != Frequency.WEEKLY) return null
            val dayOfMonth = parts["BYMONTHDAY"]?.let { it.toIntOrNull() ?: return null }
            if (dayOfMonth != null && (dayOfMonth !in 1..31 || (frequency != Frequency.MONTHLY && frequency != Frequency.YEARLY))) return null
            return Recurrence(frequency, interval, days, dayOfMonth)
        }
    }
}
