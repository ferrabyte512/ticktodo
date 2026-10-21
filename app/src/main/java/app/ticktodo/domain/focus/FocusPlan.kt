package app.ticktodo.domain.focus

/**
 * A focus-session plan in the style of the Pomodoro technique: work for
 * [focusMinutes], take a short break, and after [roundsBeforeLongBreak] focus
 * rounds take a long break. [totalRounds] limits the session; null runs until
 * the user stops it.
 *
 * Tasks opt in individually: a task with no plan simply has no focus timer
 * shown on its card, though a session can still be started from the editor
 * with the default plan.
 */
data class FocusPlan(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val roundsBeforeLongBreak: Int = 4,
    val totalRounds: Int? = 4,
    val autoStartBreaks: Boolean = true,
    val autoStartFocus: Boolean = false,
) {
    init {
        require(focusMinutes in FOCUS_RANGE) { "focus out of range" }
        require(shortBreakMinutes in BREAK_RANGE) { "short break out of range" }
        require(longBreakMinutes in BREAK_RANGE) { "long break out of range" }
        require(roundsBeforeLongBreak in 1..12) { "rounds out of range" }
        require(totalRounds == null || totalRounds in 1..24) { "total out of range" }
    }

    fun encode(): String = buildString {
        append("f=").append(focusMinutes)
        append(";s=").append(shortBreakMinutes)
        append(";l=").append(longBreakMinutes)
        append(";r=").append(roundsBeforeLongBreak)
        append(";n=").append(totalRounds ?: 0)
        append(";ab=").append(if (autoStartBreaks) 1 else 0)
        append(";af=").append(if (autoStartFocus) 1 else 0)
    }

    companion object {
        val FOCUS_RANGE = 1..180
        val BREAK_RANGE = 1..60
        const val MAX_ENCODED_LENGTH = 60

        val DEFAULT = FocusPlan()

        /** Ready-made plans offered in the editor. */
        val PRESETS = listOf(
            FocusPlan(25, 5, 15, 4, 4),
            FocusPlan(50, 10, 20, 2, 4),
            FocusPlan(15, 3, 10, 4, 4),
            FocusPlan(90, 15, 30, 1, 2),
        )

        /** Returns null for blank or malformed input instead of throwing. */
        fun parse(value: String?): FocusPlan? {
            if (value.isNullOrBlank() || value.length > MAX_ENCODED_LENGTH) return null
            val parts = value.split(';').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1).toIntOrNull()
            }.toMap()
            return try {
                FocusPlan(
                    focusMinutes = parts["f"] ?: return null,
                    shortBreakMinutes = parts["s"] ?: 5,
                    longBreakMinutes = parts["l"] ?: 15,
                    roundsBeforeLongBreak = parts["r"] ?: 4,
                    totalRounds = (parts["n"] ?: 0).takeIf { it > 0 },
                    autoStartBreaks = (parts["ab"] ?: 1) == 1,
                    autoStartFocus = (parts["af"] ?: 0) == 1,
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
