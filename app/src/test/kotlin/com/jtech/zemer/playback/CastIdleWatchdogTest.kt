package com.jtech.zemer.playback

import org.fcast.sender_sdk.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the dead-cast-session auto-teardown: a session left paused-and-abandoned, or playing-but-cut-off
 * with its clock frozen, must eventually end itself instead of hanging indefinitely (holding the relay,
 * the foreground service, and wake locks). Pure logic — no player, FCast SDK, or Android runtime.
 */
class CastIdleWatchdogTest {

    private val pausedTimeout = CastIdleWatchdog.PAUSED_IDLE_TIMEOUT_MS
    private val stalledTimeout = CastIdleWatchdog.STALLED_IDLE_TIMEOUT_MS

    private fun shouldEnd(state: PlaybackState?, idleForMs: Long) =
        CastIdleWatchdog.shouldEndIdleSession(state, idleForMs)

    // --- paused: the deliberate-walk-away case ----------------------------------

    @Test
    fun `a briefly paused session is kept, a long-abandoned one is ended`() {
        assertFalse(shouldEnd(PlaybackState.PAUSED, 0L))
        assertFalse(shouldEnd(PlaybackState.PAUSED, pausedTimeout - 1))
        assertTrue(shouldEnd(PlaybackState.PAUSED, pausedTimeout))
        assertTrue(shouldEnd(PlaybackState.PAUSED, pausedTimeout + 60_000L))
    }

    // --- stalled while (supposedly) playing: the cut-off case -------------------

    @Test
    fun `a frozen playing clock is ended on the shorter stalled timeout`() {
        assertFalse(shouldEnd(PlaybackState.PLAYING, stalledTimeout - 1))
        assertTrue(shouldEnd(PlaybackState.PLAYING, stalledTimeout))
    }

    @Test
    fun `buffering and idle states use the stalled timeout, not the paused one`() {
        // Anything that isn't PAUSED is "meant to be progressing but isn't" — the short grace applies.
        assertTrue(shouldEnd(PlaybackState.BUFFERING, stalledTimeout))
        assertTrue(shouldEnd(PlaybackState.IDLE, stalledTimeout))
        assertTrue(shouldEnd(null, stalledTimeout))
    }

    @Test
    fun `a frozen playing receiver at the end of the last track is torn down, not left hanging`() {
        // Position is not considered: a queue that has run out leaves the receiver stuck near the end of
        // its last track with no next item for auto-advance to move to — the watchdog is the only exit.
        // (Healthy track boundaries never reach the timeout: the idle timer resets on every track load.)
        assertTrue(shouldEnd(PlaybackState.PLAYING, stalledTimeout))
        assertTrue(shouldEnd(PlaybackState.IDLE, stalledTimeout))
    }

    // --- the two timeouts are ordered as intended --------------------------------

    @Test
    fun `the stalled grace is much shorter than the paused grace`() {
        assertTrue(stalledTimeout < pausedTimeout)
        // A playing stall at the stalled timeout ends; a pause at the same age does not yet.
        assertTrue(shouldEnd(PlaybackState.PLAYING, stalledTimeout))
        assertFalse(shouldEnd(PlaybackState.PAUSED, stalledTimeout))
    }
}
