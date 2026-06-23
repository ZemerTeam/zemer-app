package com.jtech.zemer.playback

import org.fcast.sender_sdk.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
