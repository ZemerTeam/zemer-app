package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the seek-restart decision ([SabrSeekLogic]) shared by the audio and video streams. Encodes the
 * two live-review findings: an early landing must let the drain reach the target (never a self-cancelling
 * restart loop), and an unknown duration estimates 0 (a from-0 drain) rather than an endless restart.
 */
class SabrSeekLogicTest {

    private val DUR = 100_000L       // 100 s
    private val LEN = 1_000_000L     // 1 MB

    @Test
    fun `estimate is linear over the format, pulled back by the margin, clamped at 0`() {
        assertEquals(50_000L, SabrSeekLogic.estimateStartMs(500_000, DUR, LEN, 0))       // halfway
        assertEquals(45_000L, SabrSeekLogic.estimateStartMs(500_000, DUR, LEN, 5_000))   // minus 5s margin
        assertEquals(0L, SabrSeekLogic.estimateStartMs(10_000, DUR, LEN, 5_000))         // margin exceeds -> 0
    }

    @Test
    fun `unknown duration or zero position yields estimate 0 (a from-0 drain)`() {
        assertEquals(0L, SabrSeekLogic.estimateStartMs(500_000, 0L, LEN, 5_000))          // no duration
        assertEquals(0L, SabrSeekLogic.estimateStartMs(500_000, DUR, 0L, 5_000))          // no length
        assertEquals(0L, SabrSeekLogic.estimateStartMs(0L, DUR, LEN, 5_000))              // start of track
    }

    private fun decide(alive: Boolean, anchor: Long, position: Long, since: Long, attempts: Int, dur: Long = DUR, margin: Long = SabrSeekLogic.SEEK_MARGIN_MS) =
        SabrSeekLogic.decide(alive, anchor, position, since, attempts, dur, LEN, margin)

    @Test
    fun `no live session restarts at the estimated time`() {
        val a = decide(alive = false, anchor = -1, position = 500_000, since = 0, attempts = 0)
        assertTrue(a is SabrSeekLogic.Restart)
        assertEquals(45_000L, (a as SabrSeekLogic.Restart).startMs)
    }

    @Test
    fun `a just-started session that hasn't landed gets grace, then restarts`() {
        assertEquals(SabrSeekLogic.Grace, decide(alive = true, anchor = -1, position = 500_000, since = 1_000, attempts = 0))
        assertTrue(decide(alive = true, anchor = -1, position = 500_000, since = 10_000, attempts = 0) is SabrSeekLogic.Restart)
    }

    @Test
    fun `an early landing (anchor at or before the target) lets the drain reach it - never restarts`() {
        // Finding: even a landing far (many MB) before the target must LetDrain; restarting re-issues the
        // same estimate and cancels the only session making progress -> the old restart-loop-to-error bug.
        assertEquals(SabrSeekLogic.LetDrain, decide(alive = true, anchor = 0, position = 900_000, since = 30_000, attempts = 0))
        assertEquals(SabrSeekLogic.LetDrain, decide(alive = true, anchor = 500_000, position = 500_000, since = 30_000, attempts = 3))
    }

    @Test
    fun `a session that landed PAST the target re-aims with a widened margin`() {
        val a = decide(alive = true, anchor = 600_000, position = 500_000, since = 30_000, attempts = 1, margin = 5_000)
        assertTrue(a is SabrSeekLogic.Restart)
        assertEquals(10_000L, (a as SabrSeekLogic.Restart).marginMs) // margin doubled
    }

    @Test
    fun `the restart budget gives up into an error`() {
        assertEquals(SabrSeekLogic.GiveUp, decide(alive = false, anchor = -1, position = 500_000, since = 0, attempts = SabrSeekLogic.MAX_SEEK_RESTARTS))
    }

    @Test
    fun `unknown duration restarts at 0 once, then the from-0 session lets the drain run - no error loop`() {
        // First far seek with no duration -> Restart(startMs=0) (a from-0 drain), not a give-up.
        val first = decide(alive = false, anchor = -1, position = 900_000, since = 0, attempts = 0, dur = 0L)
        assertTrue(first is SabrSeekLogic.Restart)
        assertEquals(0L, (first as SabrSeekLogic.Restart).startMs)
        // That session then lands at/before the target and is left to drain forward (no restart storm).
        assertEquals(SabrSeekLogic.LetDrain, decide(alive = true, anchor = 0, position = 900_000, since = 30_000, attempts = 1, dur = 0L))
    }
}
