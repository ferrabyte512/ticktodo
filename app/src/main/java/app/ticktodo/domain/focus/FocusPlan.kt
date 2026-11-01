package app.ticktodo.domain.focus

/**
 * A focus-session plan in the style of the Pomodoro technique: work for
 * [focusMinutes], take a [breakMinutes] break, and repeat until the user
 * stops the session.
 *
 * Tasks opt in individually: a task with no plan simply has no focus timer
 * shown on its card, though a session can still be started from the editor
 * with the default plan.
 */
data class FocusPlan(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val autoStartBreaks: Boolean = true,
    val autoStartFocus: Boolean = false,
) {
    init {
        require(focusMinutes in FOCUS_RANGE) { "focus out of range" }
        require(breakMinutes in BREAK_RANGE) { "break out of range" }
    }

    fun encode(): String = buildString {
        append("f=").append(focusMinutes)
        append(";s=").append(breakMinutes)
        append(";ab=").append(if (autoStartBreaks) 1 else 0)
        append(";af=").append(if (autoStartFocus) 1 else 0)
    }

    companion object {
        val FOCUS_RANGE = 1..180
        val BREAK_RANGE = 1..60
        const val MAX_ENCODED_LENGTH = 60

        val DEFAULT = FocusPlan()

        /**
         * Returns null for blank or malformed input instead of throwing. Keys
         * written by older versions (long break, rounds, session length) are ignored.
         */
        fun parse(value: String?): FocusPlan? {
            if (value.isNullOrBlank() || value.length > MAX_ENCODED_LENGTH) return null
            val parts = value.split(';').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1).toIntOrNull()
            }.toMap()
            return try {
                FocusPlan(
                    focusMinutes = parts["f"] ?: return null,
                    breakMinutes = parts["s"] ?: 5,
                    autoStartBreaks = (parts["ab"] ?: 1) == 1,
                    autoStartFocus = (parts["af"] ?: 0) == 1,
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
