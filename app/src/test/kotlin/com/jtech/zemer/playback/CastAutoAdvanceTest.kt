package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FCast end-of-track auto-advance thresholds that decide when a cast track has finished and
 * the queue should skip forward. These are the timing-sensitive constants (end epsilons, the stall
 * window, the debounce) that otherwise silently regress into double-skips or stuck-at-end playback.
 * Pure logic — no player, FCast SDK, or Android runtime required.
 */
class CastAutoAdvanceTest {

    // --- nearEnd ---------------------------------------------------------------

    @Test
    fun `nearEnd is true exactly at the epsilon boundary and past the end`() {
        // duration 180s, epsilon 2.0s -> boundary is 178.0s
        assertTrue(CastAutoAdvance.nearEnd(180.0, 178.0, 2.0))
        assertTrue(CastAutoAdvance.nearEnd(180.0, 181.0, 2.0))
    }

    @Test
    fun `nearEnd is false before the epsilon boundary`() {
        assertFalse(CastAutoAdvance.nearEnd(180.0, 177.99, 2.0))
        // mid-track is never "near end"
        assertFalse(CastAutoAdvance.nearEnd(180.0, 90.0, CastAutoAdvance.STALL_END_EPSILON_SEC))
    }

    @Test
    fun `nearEnd is false when duration is unknown (zero or negative)`() {
        // Guards the "dur > 0" check: a 0 duration must never look like the end.
        assertFalse(CastAutoAdvance.nearEnd(0.0, 0.0, 2.0))
        assertFalse(CastAutoAdvance.nearEnd(-1.0, 0.0, CastAutoAdvance.STALL_END_EPSILON_SEC))
    }

    // --- finishedNearEnd (the IDLE-from-PLAYING detector) ----------------------

    @Test
    fun `finishedNearEnd accepts the generous tail so a coarse clock is not missed`() {
        // 180s track: window = max(10, 18) = 18s -> finished from 162s on (the receiver can stop reporting
        // the clock several seconds before the real end and still be treated as finished).
        assertTrue(CastAutoAdvance.finishedNearEnd(180.0, 162.0))
        assertTrue(CastAutoAdvance.finishedNearEnd(180.0, 180.0))
        assertFalse(CastAutoAdvance.finishedNearEnd(180.0, 161.9))
    }

    @Test
    fun `finishedNearEnd uses the 10s floor for short tracks`() {
        // 30s track: the proportional tail (3s) is below the 10s floor, so the floor applies (finished from 20s).
        assertTrue(CastAutoAdvance.finishedNearEnd(30.0, 20.0))
        assertFalse(CastAutoAdvance.finishedNearEnd(30.0, 19.9))
    }

    @Test
    fun `finishedNearEnd is false mid-track, for unknown duration, and at position zero`() {
        assertFalse(CastAutoAdvance.finishedNearEnd(180.0, 90.0))
        assertFalse(CastAutoAdvance.finishedNearEnd(0.0, 0.0))
        assertFalse(CastAutoAdvance.finishedNearEnd(-1.0, 0.0))
        // a freshly-loaded track at position 0 must never look finished
        assertFalse(CastAutoAdvance.finishedNearEnd(200.0, 0.0))
    }

    // --- endEdgePositionSec (Chromecast resets the clock to 0 right before IDLE) -

    @Test
    fun `chromecast end-of-track advances - clock reset to 0 just before IDLE falls back to the last progress report`() {
        // Regression for Chromecast receivers freezing the queue at end-of-track. Observed on a
        // Chromecast-with-Google-TV: PLAYING at 240.537/241.641, then `timeChanged -> 0.0` arrives a few
        // ms BEFORE the IDLE report — so judging the raw reported position (0.0) made finishedNearEnd
        // false and the queue never auto-advanced. The end-edge position falls back to the last real
        // progress report, which is near the end, so the IDLE detector fires.
        val pos = CastAutoAdvance.endEdgePositionSec(reportedSec = 0.0, lastProgressSec = 240.537117)
        assertTrue(CastAutoAdvance.finishedNearEnd(241.641, pos))
        // The raw reported 0 was the bug: never finished.
        assertFalse(CastAutoAdvance.finishedNearEnd(241.641, 0.0))
    }

    @Test
    fun `endEdgePositionSec prefers a genuine report and only falls back on a fresh 0`() {
        // A real (> 0) report always wins — an FCast receiver that goes IDLE with the clock still near
        // the end keeps its own position.
        assertTrue(CastAutoAdvance.endEdgePositionSec(239.5, 120.0) == 239.5)
        // Only a reset-to-0 clock consults the last progress report.
        assertTrue(CastAutoAdvance.endEdgePositionSec(0.0, 239.5) == 239.5)
    }

    @Test
    fun `a mid-track stop on the receiver does not advance even with the clock reset to 0`() {
        // User stops the receiver mid-track: the clock may also reset to 0, but the last progress report
        // is mid-track, so the IDLE edge is not "finished" and the queue must not skip forward.
        val pos = CastAutoAdvance.endEdgePositionSec(reportedSec = 0.0, lastProgressSec = 90.0)
        assertFalse(CastAutoAdvance.finishedNearEnd(241.641, pos))
    }

    @Test
    fun `a freshly loaded track with no progress yet never looks finished on the IDLE edge`() {
        // Both the reported clock and the progress tracker are reset on every content (re)load, so an
        // IDLE right after a load (before any progress report) cannot consume a stale near-end position.
        val pos = CastAutoAdvance.endEdgePositionSec(reportedSec = 0.0, lastProgressSec = 0.0)
        assertFalse(CastAutoAdvance.finishedNearEnd(241.641, pos))
    }

    // --- end-as-PAUSED (some receivers auto-pause at pos==duration) -------------

    @Test
    fun `a PAUSED report at the duration counts as finished, a mid-track pause does not`() {
        // Real FCast receiver behaviour: at end-of-track it reports PAUSED at pos == duration (no IDLE / END).
        // The TIGHT epsilon treats that as finished, but a deliberate mid-track user pause must not advance.
        assertTrue(CastAutoAdvance.nearEnd(201.961, 201.961, CastAutoAdvance.PAUSED_END_EPSILON_SEC))
        assertTrue(CastAutoAdvance.nearEnd(202.0, 200.5, CastAutoAdvance.PAUSED_END_EPSILON_SEC)) // within 2s
        assertFalse(CastAutoAdvance.nearEnd(202.0, 195.0, CastAutoAdvance.PAUSED_END_EPSILON_SEC)) // paused 7s out
        assertFalse(CastAutoAdvance.nearEnd(202.0, 90.0, CastAutoAdvance.PAUSED_END_EPSILON_SEC)) // mid-track pause
    }

    // --- debouncePassed --------------------------------------------------------

    @Test
    fun `debouncePassed requires strictly more than the debounce window`() {
        val last = 100_000L
        assertTrue(CastAutoAdvance.debouncePassed(last + CastAutoAdvance.ADVANCE_DEBOUNCE_MS + 1, last))
        // exactly at the window is not yet passed (strict >)
        assertFalse(CastAutoAdvance.debouncePassed(last + CastAutoAdvance.ADVANCE_DEBOUNCE_MS, last))
        // a fresh transition suppresses an immediate second advance
        assertFalse(CastAutoAdvance.debouncePassed(last + 500, last))
    }

    // --- stalled ---------------------------------------------------------------

    @Test
    fun `stalled requires strictly more than the silence window`() {
        assertTrue(CastAutoAdvance.stalled(CastAutoAdvance.STALL_SILENCE_MS + 1))
        assertFalse(CastAutoAdvance.stalled(CastAutoAdvance.STALL_SILENCE_MS))
        assertFalse(CastAutoAdvance.stalled(1000))
    }

    // --- combined scenarios (document the real call-site conditions) -----------

    @Test
    fun `idle near end advances when debounced but not within the debounce window`() {
        val now = 1_000_000L
        val finished = CastAutoAdvance.finishedNearEnd(200.0, 199.0)
        assertTrue(finished && CastAutoAdvance.debouncePassed(now, now - 9_000))
        assertFalse(finished && CastAutoAdvance.debouncePassed(now, now - 3_000))
    }

    @Test
    fun `stall fires only when near end, silent past the window, and debounced`() {
        val now = 1_000_000L
        val lastTransition = now - 9_000
        assertTrue(
            CastAutoAdvance.nearEnd(200.0, 198.0, CastAutoAdvance.STALL_END_EPSILON_SEC) &&
                CastAutoAdvance.stalled(5_000) &&
                CastAutoAdvance.debouncePassed(now, lastTransition),
        )
        // brief 2s silence (still updating) must not be treated as a stall
        assertFalse(
            CastAutoAdvance.nearEnd(200.0, 198.0, CastAutoAdvance.STALL_END_EPSILON_SEC) &&
                CastAutoAdvance.stalled(2_000) &&
                CastAutoAdvance.debouncePassed(now, lastTransition),
        )
    }

    @Test
    fun `resetting last position to zero clears a stale near-end so a fresh track is not auto-skipped`() {
        // Regression for the connect/device-switch spurious auto-skip: PlayerConnection's remoteTime
        // collector now records position 0 unconditionally, so a new track (remoteTime reset to 0)
        // clears the previous track's near-end position. With the old guard the position stayed stale
        // and nearEnd stayed true against the new duration -> the fresh track could be auto-skipped.
        val staleNearEndPos = 198.0 // left over from a ~200s previous track
        assertTrue(
            "stale near-end position would (wrongly) look finished against a new track's duration",
            CastAutoAdvance.nearEnd(200.0, staleNearEndPos, CastAutoAdvance.STALL_END_EPSILON_SEC),
        )
        // After the reset (position 0) the new track of any real length is not near end.
        assertFalse(
            "a freshly-connected/loaded track at position 0 must never look finished",
            CastAutoAdvance.nearEnd(200.0, 0.0, CastAutoAdvance.STALL_END_EPSILON_SEC),
        )
        // Even a very short track is safe at position 0 (0 >= dur - eps only when dur <= eps).
        assertFalse(CastAutoAdvance.nearEnd(10.0, 0.0, CastAutoAdvance.STALL_END_EPSILON_SEC))
    }
}
