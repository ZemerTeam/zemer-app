package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the receiver-playback-error recovery ladder: a cast fetch failure must escalate
 * reload -> fresh resolve -> advance (capped) -> give up, never silently die (the original bug: a
 * googlevideo 403 on the receiver was report-only and the cast session sat dead, indistinguishable
 * from auto-advance breaking). Pure logic — no player, FCast SDK, or Android runtime required.
 */
class CastErrorRecoveryTest {

    // --- actionForAttempt: the escalation ladder --------------------------------

    @Test
    fun `first error reloads the same URL, second re-resolves, third advances`() {
        assertEquals(CastErrorRecovery.Action.RELOAD, CastErrorRecovery.actionForAttempt(0, 0, canAdvance = true))
        assertEquals(CastErrorRecovery.Action.RESOLVE_FRESH, CastErrorRecovery.actionForAttempt(1, 0, canAdvance = true))
        assertEquals(CastErrorRecovery.Action.ADVANCE, CastErrorRecovery.actionForAttempt(2, 0, canAdvance = true))
        // any later error on the same track keeps advancing (the load after ADVANCE is a new track,
        // which resets the attempt count; this is only reachable if that load never happened)
        assertEquals(CastErrorRecovery.Action.ADVANCE, CastErrorRecovery.actionForAttempt(5, 0, canAdvance = true))
    }

    @Test
    fun `advance is capped by consecutive abandoned tracks so a dead network cannot machine-gun the queue`() {
        val cap = CastErrorRecovery.MAX_CONSECUTIVE_ERROR_ADVANCES
        assertEquals(CastErrorRecovery.Action.ADVANCE, CastErrorRecovery.actionForAttempt(2, cap - 1, canAdvance = true))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, CastErrorRecovery.actionForAttempt(2, cap, canAdvance = true))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, CastErrorRecovery.actionForAttempt(2, cap + 1, canAdvance = true))
    }

    @Test
    fun `when the queue cannot advance (repeat-one or last track) the ladder gives up instead of looping`() {
        // Reload and re-resolve are still worth trying…
        assertEquals(CastErrorRecovery.Action.RELOAD, CastErrorRecovery.actionForAttempt(0, 0, canAdvance = false))
        assertEquals(CastErrorRecovery.Action.RESOLVE_FRESH, CastErrorRecovery.actionForAttempt(1, 0, canAdvance = false))
        // …but abandoning the track has nowhere to go: never replay the same failing load forever.
        assertEquals(CastErrorRecovery.Action.GIVE_UP, CastErrorRecovery.actionForAttempt(2, 0, canAdvance = false))
    }

    // --- isNewFailure: burst dedupe ---------------------------------------------

    @Test
    fun `error reports inside the burst window are the same failure`() {
        val handled = 100_000L
        assertFalse(CastErrorRecovery.isNewFailure(handled + 1, handled))
        assertFalse(CastErrorRecovery.isNewFailure(handled + CastErrorRecovery.ERROR_BURST_WINDOW_MS, handled))
        assertTrue(CastErrorRecovery.isNewFailure(handled + CastErrorRecovery.ERROR_BURST_WINDOW_MS + 1, handled))
    }

    @Test
    fun `the first error ever is always a new failure`() {
        // lastHandledMs starts at 0; any realistic wall clock is far past the window.
        assertTrue(CastErrorRecovery.isNewFailure(System.currentTimeMillis(), 0L))
    }

    // --- progressResetsCounters ---------------------------------------------------

    @Test
    fun `real playback progress resets the counters, a failed load's near-zero clock does not`() {
        // The afternoon failure mode: a track streams for minutes, then a reconnect 403s. That error must
        // restart the ladder from RELOAD (with resume), not inherit attempts from an earlier failure.
        assertTrue(CastErrorRecovery.progressResetsCounters(CastErrorRecovery.PROGRESS_RESET_SEC))
        assertTrue(CastErrorRecovery.progressResetsCounters(195.0))
        // The morning failure mode: the load 403s immediately, the clock never leaves ~0 — no reset,
        // so repeated errors keep climbing the ladder instead of retrying the top rung forever.
        assertFalse(CastErrorRecovery.progressResetsCounters(0.0))
        assertFalse(CastErrorRecovery.progressResetsCounters(CastErrorRecovery.PROGRESS_RESET_SEC - 0.1))
    }

    // --- combined scenario: the two observed failures ----------------------------

    @Test
    fun `morning failure - immediate 403 on a fresh load escalates through the full ladder`() {
        // Track loads, receiver errors at ~0s. Attempt 0: reload. Still 403 -> attempt 1: fresh URL.
        // Still 403 -> attempt 2: abandon, advance. Each abandoned track increments the consecutive
        // count until the cap stops the skipping.
        var consecutive = 0
        val actions = (0..2).map { CastErrorRecovery.actionForAttempt(it, consecutive, canAdvance = true) }
        assertEquals(
            listOf(
                CastErrorRecovery.Action.RELOAD,
                CastErrorRecovery.Action.RESOLVE_FRESH,
                CastErrorRecovery.Action.ADVANCE,
            ),
            actions,
        )
        // Three tracks in a row abandoned -> the fourth track's third error gives up.
        consecutive = CastErrorRecovery.MAX_CONSECUTIVE_ERROR_ADVANCES
        assertEquals(CastErrorRecovery.Action.GIVE_UP, CastErrorRecovery.actionForAttempt(2, consecutive, canAdvance = true))
    }
}
