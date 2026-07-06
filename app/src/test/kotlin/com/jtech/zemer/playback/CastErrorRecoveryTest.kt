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

    private fun action(attempt: Int, consecutive: Int = 0, canAdvance: Boolean = true, canTryDirect: Boolean = false) =
        CastErrorRecovery.actionForAttempt(attempt, consecutive, canAdvance, canTryDirect)

    @Test
    fun `first error reloads the same URL, second re-resolves, third advances`() {
        assertEquals(CastErrorRecovery.Action.RELOAD, action(0))
        assertEquals(CastErrorRecovery.Action.RESOLVE_FRESH, action(1))
        assertEquals(CastErrorRecovery.Action.ADVANCE, action(2))
        // any later error on the same track keeps advancing (the load after ADVANCE is a new track,
        // which resets the attempt count; this is only reachable if that load never happened)
        assertEquals(CastErrorRecovery.Action.ADVANCE, action(5))
    }

    @Test
    fun `a failing relay URL gets a de-relayed direct attempt before the track is abandoned`() {
        // The error callback can't say WHY the receiver failed; a receiver that can't fetch from the
        // phone relay at all (cleartext-http policy, unreachable phone) must not burn the whole ladder
        // on relay URLs. The rung sits after RESOLVE_FRESH so the direct URL is freshly minted.
        assertEquals(CastErrorRecovery.Action.RELOAD, action(0, canTryDirect = true))
        assertEquals(CastErrorRecovery.Action.RESOLVE_FRESH, action(1, canTryDirect = true))
        assertEquals(CastErrorRecovery.Action.DIRECT_URL, action(2, canTryDirect = true))
        assertEquals(CastErrorRecovery.Action.ADVANCE, action(3, canTryDirect = true))
        // A load that was already direct gets no de-relay rung — it would just re-send the same thing.
        assertEquals(CastErrorRecovery.Action.ADVANCE, action(2, canTryDirect = false))
    }

    @Test
    fun `advance is capped by consecutive abandoned tracks so a dead network cannot machine-gun the queue`() {
        val cap = CastErrorRecovery.MAX_CONSECUTIVE_ERROR_ADVANCES
        assertEquals(CastErrorRecovery.Action.ADVANCE, action(2, cap - 1))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, action(2, cap))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, action(2, cap + 1))
        // The de-relay rung is positional (attempt 2), not advance-budgeted — still offered at the cap.
        assertEquals(CastErrorRecovery.Action.DIRECT_URL, action(2, cap, canTryDirect = true))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, action(3, cap, canTryDirect = true))
    }

    @Test
    fun `when the queue cannot advance (repeat-one or last track) the ladder gives up instead of looping`() {
        // Reload, re-resolve, and the de-relay attempt are still worth trying…
        assertEquals(CastErrorRecovery.Action.RELOAD, action(0, canAdvance = false))
        assertEquals(CastErrorRecovery.Action.RESOLVE_FRESH, action(1, canAdvance = false))
        assertEquals(CastErrorRecovery.Action.DIRECT_URL, action(2, canAdvance = false, canTryDirect = true))
        // …but abandoning the track has nowhere to go: never replay the same failing load forever.
        assertEquals(CastErrorRecovery.Action.GIVE_UP, action(2, canAdvance = false))
        assertEquals(CastErrorRecovery.Action.GIVE_UP, action(3, canAdvance = false, canTryDirect = true))
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
        // Relay-served loads (the normal Stage-2 case) get the de-relay attempt before abandonment.
        val actions = (0..3).map { action(it, canTryDirect = true) }
        assertEquals(
            listOf(
                CastErrorRecovery.Action.RELOAD,
                CastErrorRecovery.Action.RESOLVE_FRESH,
                CastErrorRecovery.Action.DIRECT_URL,
                CastErrorRecovery.Action.ADVANCE,
            ),
            actions,
        )
        // Three tracks in a row abandoned -> the fourth track's post-rungs error gives up.
        assertEquals(
            CastErrorRecovery.Action.GIVE_UP,
            action(3, CastErrorRecovery.MAX_CONSECUTIVE_ERROR_ADVANCES, canTryDirect = true),
        )
    }
}
