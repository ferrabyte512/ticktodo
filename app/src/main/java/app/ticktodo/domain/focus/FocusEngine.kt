package app.ticktodo.domain.focus

import kotlinx.serialization.Serializable

/** LONG_BREAK is no longer entered; it stays so sessions saved by older versions still load. */
@Serializable
enum class FocusPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

/**
 * The whole state of a running focus session. Time is stored as absolute
 * timestamps, never as a ticking counter, so the session stays correct across
 * process death, screen-off and reboots: the remaining time is always
 * `phaseEndsAt - now` while running, or [pausedRemainingMs] while paused.
 */
@Serializable
data class FocusState(
    val taskId: String,
    val taskTitle: String,
    val plan: String,
    val phase: FocusPhase,
    /** 1-based number of the current (or last) focus round. */
    val round: Int,
    /** Focus rounds finished in this session. */
    val completedRounds: Int,
    val sessionStartedAt: Long,
    val phaseStartedAt: Long,
    /** Set while running. */
    val phaseEndsAt: Long?,
    /** Set while paused, or while waiting for the user to start the next phase. */
    val pausedRemainingMs: Long?,
    /** Focus time spent in the current phase before the last pause, for statistics. */
    val focusMsBeforePause: Long = 0,
    /** Time added to the current phase with "extend"; reset when the next phase starts. */
    val extraMs: Long = 0,
) {
    val running: Boolean get() = phaseEndsAt != null
    val decodedPlan: FocusPlan get() = FocusPlan.parse(plan) ?: FocusPlan.DEFAULT

    fun remainingMs(now: Long): Long = phaseEndsAt?.let { (it - now).coerceAtLeast(0) } ?: (pausedRemainingMs ?: 0)

    fun phaseLengthMs(): Long = FocusEngine.lengthMs(decodedPlan, phase) + extraMs
}

/** What a transition produced, so the caller can record statistics and notify. */
data class FocusTransition(
    val state: FocusState?,
    /** Focus time to record for the task, in ms (0 if none). */
    val focusMsEarned: Long,
    val finishedPhase: FocusPhase?,
    val sessionFinished: Boolean,
)

/** Pure focus-timer state machine. Unit tested in FocusEngineTest. */
object FocusEngine {

    const val EXTEND_MS = 5 * 60_000L
    const val MAX_EXTRA_MS = 120 * 60_000L

    fun lengthMs(plan: FocusPlan, phase: FocusPhase): Long = 60_000L * when (phase) {
        FocusPhase.FOCUS -> plan.focusMinutes
        FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> plan.breakMinutes
    }

    fun start(taskId: String, taskTitle: String, plan: FocusPlan, now: Long): FocusState = FocusState(
        taskId = taskId,
        taskTitle = taskTitle.take(200),
        plan = plan.encode(),
        phase = FocusPhase.FOCUS,
        round = 1,
        completedRounds = 0,
        sessionStartedAt = now,
        phaseStartedAt = now,
        phaseEndsAt = now + lengthMs(plan, FocusPhase.FOCUS),
        pausedRemainingMs = null,
    )

    fun pause(state: FocusState, now: Long): FocusState {
        val endsAt = state.phaseEndsAt ?: return state
        val spent = if (state.phase == FocusPhase.FOCUS) (now - state.phaseStartedAt).coerceAtLeast(0) else 0
        return state.copy(
            phaseEndsAt = null,
            pausedRemainingMs = (endsAt - now).coerceAtLeast(0),
            focusMsBeforePause = state.focusMsBeforePause + spent,
        )
    }

    /** Adds [ms] to the current phase, running or paused, up to [MAX_EXTRA_MS] per phase. */
    fun extend(state: FocusState, ms: Long): FocusState {
        val add = ms.coerceAtMost(MAX_EXTRA_MS - state.extraMs)
        if (add <= 0) return state
        return state.copy(
            phaseEndsAt = state.phaseEndsAt?.plus(add),
            pausedRemainingMs = if (state.running) null else (state.pausedRemainingMs ?: state.phaseLengthMs()) + add,
            extraMs = state.extraMs + add,
        )
    }

    fun resume(state: FocusState, now: Long): FocusState {
        if (state.running) return state
        val remaining = state.pausedRemainingMs ?: state.phaseLengthMs()
        return state.copy(phaseStartedAt = now, phaseEndsAt = now + remaining, pausedRemainingMs = null)
    }

    /**
     * Ends the current phase (time ran out, or the user skipped) and moves to
     * the next one. A finished focus phase counts as a completed round only if
     * it ran to the end; a skipped one still earns the time actually spent.
     */
    fun advance(state: FocusState, now: Long, ranToEnd: Boolean): FocusTransition {
        val plan = state.decodedPlan
        val earned = focusTimeEarned(state, now)
        return when (state.phase) {
            FocusPhase.FOCUS -> {
                val completed = state.completedRounds + if (ranToEnd) 1 else 0
                // Sessions repeat focus and break until the user stops them.
                FocusTransition(
                    enter(state.copy(completedRounds = completed), FocusPhase.SHORT_BREAK, now, plan.autoStartBreaks),
                    earned,
                    FocusPhase.FOCUS,
                    sessionFinished = false,
                )
            }
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> FocusTransition(
                enter(state.copy(round = state.round + 1), FocusPhase.FOCUS, now, plan.autoStartFocus),
                0,
                state.phase,
                sessionFinished = false,
            )
        }
    }

    /** Stops the session; returns the focus time to record. */
    fun stop(state: FocusState, now: Long): Long = focusTimeEarned(state, now)

    /** Applies every phase end that passed while the app was not running. */
    fun catchUp(state: FocusState, now: Long): List<FocusTransition> {
        val out = mutableListOf<FocusTransition>()
        var current: FocusState? = state
        while (current != null && current.phaseEndsAt != null && current.phaseEndsAt!! <= now && out.size < 100) {
            val t = advance(current, current.phaseEndsAt!!, ranToEnd = true)
            out += t
            current = t.state
        }
        return out
    }

    private fun enter(state: FocusState, phase: FocusPhase, now: Long, autoStart: Boolean): FocusState {
        val length = lengthMs(state.decodedPlan, phase)
        return state.copy(
            phase = phase,
            phaseStartedAt = now,
            phaseEndsAt = if (autoStart) now + length else null,
            pausedRemainingMs = if (autoStart) null else length,
            focusMsBeforePause = 0,
            extraMs = 0,
        )
    }

    private fun focusTimeEarned(state: FocusState, now: Long): Long {
        if (state.phase != FocusPhase.FOCUS) return 0
        val running = if (state.running) {
            val end = minOf(now, state.phaseEndsAt!!)
            (end - state.phaseStartedAt).coerceAtLeast(0)
        } else 0
        return (state.focusMsBeforePause + running).coerceAtMost(state.phaseLengthMs())
    }
}
