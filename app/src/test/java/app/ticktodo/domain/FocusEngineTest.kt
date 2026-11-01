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
    private val plan = FocusPlan(focusMinutes = 25, breakMinutes = 5)

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

    @Test fun `focus and break alternate until the user stops`() {
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
        repeat(10) {
            t = FocusEngine.advance(s, s.phaseEndsAt!!, ranToEnd = true)
            assertEquals(FocusPhase.SHORT_BREAK, t.state!!.phase)
            assertFalse(t.sessionFinished)
            s = FocusEngine.resume(FocusEngine.advance(t.state!!, t.state!!.phaseEndsAt!!, ranToEnd = true).state!!, 0)
        }
        assertEquals(12, s.round)
    }

    @Test fun `plans saved by older versions still parse and ignore long-break settings`() {
        assertEquals(FocusPlan(50, 10, autoStartBreaks = true, autoStartFocus = false), FocusPlan.parse("f=50;s=10;l=20;r=2;n=4;ab=1;af=0"))
    }

    @Test fun `a long break saved by an older version lasts as long as a break`() {
        val s = FocusEngine.start("t", "x", plan, 0).copy(phase = FocusPhase.LONG_BREAK)
        assertEquals(5 * min, s.phaseLengthMs())
    }

    @Test fun `skipping focus earns only the time actually spent`() {
        val s = FocusEngine.start("t", "x", plan, 0)
        val t = FocusEngine.advance(s, 7 * min, ranToEnd = false)
        assertEquals(7 * min, t.focusMsEarned)
        assertEquals(0, t.state!!.completedRounds)
    }

    @Test fun `extend adds time to the running or paused phase and resets on the next phase`() {
        val s = FocusEngine.start("t", "x", plan, 0)
        val longer = FocusEngine.extend(s, FocusEngine.EXTEND_MS)
        assertEquals(30 * min, longer.phaseEndsAt)
        assertEquals(30 * min, longer.phaseLengthMs())
        // Extended focus time counts in full.
        assertEquals(30 * min, FocusEngine.advance(longer, 30 * min, ranToEnd = true).focusMsEarned)
        val paused = FocusEngine.extend(FocusEngine.pause(s, 10 * min), FocusEngine.EXTEND_MS)
        assertEquals(20 * min, paused.remainingMs(99 * min))
        val brk = FocusEngine.advance(longer, 30 * min, ranToEnd = true).state!!
        assertEquals(0, brk.extraMs)
        assertEquals(5 * min, brk.phaseLengthMs())
    }

    @Test fun `extend stops at the per-phase limit`() {
        var s = FocusEngine.start("t", "x", plan, 0)
        repeat(100) { s = FocusEngine.extend(s, FocusEngine.EXTEND_MS) }
        assertEquals(FocusEngine.MAX_EXTRA_MS, s.extraMs)
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
        val auto = plan.copy(autoStartFocus = true)
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
        val p = FocusPlan(50, 10, autoStartBreaks = false, autoStartFocus = true)
        assertEquals(p, FocusPlan.parse(p.encode()))
        assertNull(FocusPlan.parse("garbage"))
        assertNull(FocusPlan.parse("f=0"))
        assertNull(FocusPlan.parse("f=999"))
    }
}
