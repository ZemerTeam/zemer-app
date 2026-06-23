package com.jtech.zemer.playback

import org.fcast.sender_sdk.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the FCast remote-state and clock-unit mappings. These replaced a stringly-typed
 * `state.toString().contains("Playing")` check and five hand-written `* 1000` / `/ 1000.0`
 * conversions — exactly the kind of thing that silently desyncs the seek bar or makes the
 * play/pause button lie if someone fat-fingers a constant or the SDK enum is renamed.
 * Pure logic; no player, SDK runtime, or Android needed.
 */
class CastPlaybackTest {

    @Test
    fun `isPlaying is true only for PLAYING`() {
        assertTrue(CastPlayback.isPlaying(PlaybackState.PLAYING))
        assertFalse(CastPlayback.isPlaying(PlaybackState.PAUSED))
        assertFalse(CastPlayback.isPlaying(PlaybackState.BUFFERING))
        assertFalse(CastPlayback.isPlaying(PlaybackState.IDLE))
        assertFalse("null (no remote state yet) must read as not playing", CastPlayback.isPlaying(null))
    }

    @Test
    fun `isPaused is true only for PAUSED`() {
        // Gates the stall-based auto-advance: a deliberately PAUSED track freezes the remote clock the
        // same way a stall does, and must never be treated as "finished" and auto-skipped.
        assertTrue(CastPlayback.isPaused(PlaybackState.PAUSED))
        assertFalse(CastPlayback.isPaused(PlaybackState.PLAYING))
        assertFalse(CastPlayback.isPaused(PlaybackState.BUFFERING))
        assertFalse(CastPlayback.isPaused(PlaybackState.IDLE))
        assertFalse("null must not read as paused", CastPlayback.isPaused(null))
    }

    @Test
    fun `playIntentForState maps PLAYING and PAUSED, ignores transient states`() {
        assertEquals(true, CastPlayback.playIntentForState(PlaybackState.PLAYING))
        assertEquals(false, CastPlayback.playIntentForState(PlaybackState.PAUSED))
        assertNull("buffering must not change the play intent", CastPlayback.playIntentForState(PlaybackState.BUFFERING))
        assertNull("idle must not change the play intent", CastPlayback.playIntentForState(PlaybackState.IDLE))
        assertNull("null must not change the play intent", CastPlayback.playIntentForState(null))
    }

    @Test
    fun `remoteSecondsToMs scales seconds to milliseconds`() {
        assertEquals(0L, CastPlayback.remoteSecondsToMs(0.0))
        assertEquals(1_500L, CastPlayback.remoteSecondsToMs(1.5))
        assertEquals(180_000L, CastPlayback.remoteSecondsToMs(180.0))
    }

    @Test
    fun `msToRemoteSeconds scales milliseconds to seconds`() {
        assertEquals(0.0, CastPlayback.msToRemoteSeconds(0), 0.0)
        assertEquals(1.5, CastPlayback.msToRemoteSeconds(1_500), 0.0)
        assertEquals(180.0, CastPlayback.msToRemoteSeconds(180_000), 0.0)
    }

    @Test
    fun `seconds and milliseconds round-trip`() {
        assertEquals(1_500L, CastPlayback.remoteSecondsToMs(CastPlayback.msToRemoteSeconds(1_500)))
        assertEquals(42.0, CastPlayback.msToRemoteSeconds(CastPlayback.remoteSecondsToMs(42.0)), 0.0)
    }
}
