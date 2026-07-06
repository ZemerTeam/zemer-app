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
    fun `isRemotePlaying mirrors the reported state once known`() {
        // Once the receiver has reported, the intent fallback must be ignored — a PAUSED report with a
        // stale shouldPlay=true still reads as paused, so the next tap sends play(), not pause().
        assertTrue(CastPlayback.isRemotePlaying(PlaybackState.PLAYING, shouldPlay = false))
        assertFalse(CastPlayback.isRemotePlaying(PlaybackState.PAUSED, shouldPlay = true))
        assertFalse(CastPlayback.isRemotePlaying(PlaybackState.BUFFERING, shouldPlay = true))
        assertFalse(CastPlayback.isRemotePlaying(PlaybackState.IDLE, shouldPlay = true))
    }

    @Test
    fun `isRemotePlaying falls back to the play intent before the first report`() {
        // Regression for the connect-window pause bug: right after connectTo the state is still null but
        // the button shows "pause" (shouldPlay=true) — a tap must decide "playing" and send pause(),
        // not re-assert play() and discard the user's pause until the first PLAYING report.
        assertTrue(CastPlayback.isRemotePlaying(state = null, shouldPlay = true))
        assertFalse(CastPlayback.isRemotePlaying(state = null, shouldPlay = false))
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

    @Test
    fun `shouldStartLocalPlayback never starts local audio while casting`() {
        // Regression for the dual-playback bug: a community/online playlist's songs resolve
        // asynchronously, and that completion must not be allowed to flip the local player's
        // playWhenReady while a receiver is connected, no matter what the caller requested.
        assertFalse(CastPlayback.shouldStartLocalPlayback(playWhenReady = true, isCasting = true))
        assertFalse(CastPlayback.shouldStartLocalPlayback(playWhenReady = false, isCasting = true))
    }

    @Test
    fun `shouldStartLocalPlayback follows the caller's intent when not casting`() {
        assertTrue(CastPlayback.shouldStartLocalPlayback(playWhenReady = true, isCasting = false))
        assertFalse(CastPlayback.shouldStartLocalPlayback(playWhenReady = false, isCasting = false))
    }

    @Test
    fun `shouldPauseCastForVideo pauses only an actively-playing receiver`() {
        // Opening a full-screen video plays audio on the phone; a playing receiver must be paused so
        // the two don't sound at once (the dual-audio bug this fixes).
        assertTrue(CastPlayback.shouldPauseCastForVideo(isCasting = true, remoteState = PlaybackState.PLAYING))
        // A receiver the user already paused is left alone — we must not later resume what we never paused.
        assertFalse(CastPlayback.shouldPauseCastForVideo(isCasting = true, remoteState = PlaybackState.PAUSED))
        assertFalse(CastPlayback.shouldPauseCastForVideo(isCasting = true, remoteState = PlaybackState.BUFFERING))
        assertFalse(CastPlayback.shouldPauseCastForVideo(isCasting = true, remoteState = null))
        // Not casting: nothing to pause, whatever the (stale) remote state says.
        assertFalse(CastPlayback.shouldPauseCastForVideo(isCasting = false, remoteState = PlaybackState.PLAYING))
    }

    @Test
    fun `shouldResumeCastAfterVideo resumes only what the video paused and only while still connected`() {
        assertTrue(CastPlayback.shouldResumeCastAfterVideo(pausedByVideo = true, isCasting = true))
        // We didn't pause it (it was already paused, or we weren't casting) — leave it.
        assertFalse(CastPlayback.shouldResumeCastAfterVideo(pausedByVideo = false, isCasting = true))
        // Session dropped/ended while the video was open — never revive it.
        assertFalse(CastPlayback.shouldResumeCastAfterVideo(pausedByVideo = true, isCasting = false))
        assertFalse(CastPlayback.shouldResumeCastAfterVideo(pausedByVideo = false, isCasting = false))
    }

    @Test
    fun `steppedVolume clamps at the floor`() {
        // A press down near 0 must not go negative — repeated presses at the bottom are no-ops.
        assertEquals(0.0, CastPlayback.steppedVolume(current = 0.03, direction = -1), 1e-9)
    }

    @Test
    fun `steppedVolume clamps at the ceiling`() {
        // A press up near 1.0 must not exceed full volume.
        assertEquals(1.0, CastPlayback.steppedVolume(current = 0.98, direction = 1), 1e-9)
    }

    @Test
    fun `steppedVolume steps up and down by VOLUME_STEP from mid-range`() {
        assertEquals(
            0.5 + CastPlayback.VOLUME_STEP,
            CastPlayback.steppedVolume(current = 0.5, direction = 1),
            1e-9
        )
        assertEquals(
            0.5 - CastPlayback.VOLUME_STEP,
            CastPlayback.steppedVolume(current = 0.5, direction = -1),
            1e-9
        )
    }
}
