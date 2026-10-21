package app.ticktodo.domain

import app.ticktodo.domain.focus.FocusEngine
import app.ticktodo.domain.focus.FocusPhase
import app.ticktodo.domain.focus.FocusPlan
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusEngineTest {
    private val min = 60_000L
    private val plan = FocusPlan(focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, roundsBeforeLongBreak = 2, totalRounds = 3)

    @Test fun `start runs a focus phase that ends after the focus length`() {
        val s = FocusEngine.start("t", "Write report", plan, 1_000)
        assertEquals(FocusPhase.FOCUS, s.phase)
        assertEquals(1_000 + 25 * min, s.phaseEndsAt)
        assertEquals(25 * min, s.remainingMs(1_000))
        assertTrue(s.running)
    }

    @Test fun `pause freezes the remaining time and resume continues it`() {
        val s = FocusEngine.start("t", "x", plan, 0)
        val paused = FocusEngine.pause(s, 10 * min)
        assertFalse(paused.running)
        assertEquals(15 * min, paused.remainingMs(99 * min))
        val resumed = FocusEngine.resume(paused, 20 * min)
        assertEquals(35 * min, resumed.phaseEndsAt)
    }

    @Test fun `finished focus round goes to a short break, then a long break after N rounds`() {
        var s = FocusEngine.start("t", "x", plan, 0)
        var t = FocusEngine.advance(s, s.phaseEndsAt!!, ranToEnd = true)
        assertEquals(FocusPhase.SHORT_BREAK, t.state!!.phase)
        assertEquals(25 * min, t.focusMsEarned)
        assertEquals(1, t.state!!.completedRounds)

        s = t.state!!
        t = FocusEngine.advance(s, s.phaseEndsAt!!, ranToEnd = true) // break over -> round 2
        assertEquals(FocusPhase.FOCUS, t.state!!.phase)
        assertEquals(2, t.state!!.round)
        // autoStartFocus is off by default: the next round waits for the user.
        assertFalse(t.state!!.running)

        s = FocusEngine.resume(t.state!!, 100 * min)
        t = FocusEngine.advance(s, s.phaseEndsAt!!, ranToEnd = true)
        assertEquals(FocusPhase.LONG_BREAK, t.state!!.phase)
    }

    @Test fun `session ends after the last round`() {
        val one = plan.copy(totalRounds = 1)
        val s = FocusEngine.start("t", "x", one, 0)
        val t = FocusEngine.advance(s, s.phaseEndsAt!!, ranToEnd = true)
        assertNull(t.state)
        assertTrue(t.sessionFinished)
        assertEquals(25 * min, t.focusMsEarned)
    }

    @Test fun `skipping focus earns only the time actually spent`() {
        val s = FocusEngine.start("t", "x", plan, 0)
        val t = FocusEngine.advance(s, 7 * min, ranToEnd = false)
        assertEquals(7 * min, t.focusMsEarned)
        assertEquals(0, t.state!!.completedRounds)
    }

    @Test fun `focus time counts across pauses`() {
        var s = FocusEngine.start("t", "x", plan, 0)
        s = FocusEngine.pause(s, 5 * min)
        s = FocusEngine.resume(s, 50 * min)
        assertEquals(8 * min, FocusEngine.stop(s, 53 * min))
    }

    @Test fun `breaks earn no focus time`() {
        val s = FocusEngine.start("t", "x", plan, 0)
        val brk = FocusEngine.advance(s, s.phaseEndsAt!!, true).state!!
        assertEquals(0, FocusEngine.stop(brk, brk.phaseStartedAt + 3 * min))
    }

    @Test fun `catch up applies every phase end that passed while the app was closed`() {
        val auto = plan.copy(autoStartFocus = true, totalRounds = null)
        val s = FocusEngine.start("t", "x", auto, 0)
        // 25 focus + 5 break + 10 into round 2
        val transitions = FocusEngine.catchUp(s, 40 * min)
        assertEquals(2, transitions.size)
        val now = transitions.last().state
        assertNotNull(now)
        assertEquals(FocusPhase.FOCUS, now.phase)
        assertEquals(2, now.round)
        assertEquals(15 * min, now.remainingMs(40 * min))
    }

    @Test fun `plan encodes and parses`() {
        val p = FocusPlan(50, 10, 20, 3, null, autoStartBreaks = false, autoStartFocus = true)
        assertEquals(p, FocusPlan.parse(p.encode()))
        assertNull(FocusPlan.parse("garbage"))
        assertNull(FocusPlan.parse("f=0"))
        assertNull(FocusPlan.parse("f=999"))
    }
}
