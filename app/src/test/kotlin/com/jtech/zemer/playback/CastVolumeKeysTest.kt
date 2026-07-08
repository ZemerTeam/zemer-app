package com.jtech.zemer.playback

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the hardware-volume-key routing rule while casting: buttons must drive the receiver, not the
 * phone's own volume, and the receiver-bound ACTION_UP must be swallowed (not just ignored) so the
 * system's volume UI doesn't pop for a key that didn't move the phone's volume. Pure logic; no
 * Activity/dispatch runtime needed.
 */
class CastVolumeKeysTest {

    @Test
    fun `non-volume key is always ignored`() {
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, isCasting = true, videoPlaybackActive = false)
        )
    }

    @Test
    fun `volume keys are ignored when not casting`() {
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, isCasting = false, videoPlaybackActive = false)
        )
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, isCasting = false, videoPlaybackActive = false)
        )
    }

    @Test
    fun `volume up on ACTION_DOWN while casting adjusts up`() {
        assertEquals(
            CastVolumeKeyAction.AdjustUp,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, isCasting = true, videoPlaybackActive = false)
        )
    }

    @Test
    fun `volume down on ACTION_DOWN while casting adjusts down`() {
        assertEquals(
            CastVolumeKeyAction.AdjustDown,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, isCasting = true, videoPlaybackActive = false)
        )
    }

    @Test
    fun `volume keys fall through to system volume while a local video is active`() {
        // The full-screen video drives the phone's own audio, so its volume must be adjusted locally
        // even though the cast session is still connected (just paused underneath the video).
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN,
                isCasting = true, videoPlaybackActive = true,
            )
        )
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP,
                isCasting = true, videoPlaybackActive = true,
            )
        )
    }

    @Test
    fun `still routes to receiver while casting when no local video is active`() {
        assertEquals(
            CastVolumeKeyAction.AdjustUp,
            CastVolumeKeys.decide(
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN,
                isCasting = true, videoPlaybackActive = false,
            )
        )
    }

    @Test
    fun `ACTION_UP while casting is consumed, not ignored`() {
        assertEquals(
            CastVolumeKeyAction.Consume,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, isCasting = true, videoPlaybackActive = false)
        )
        assertEquals(
            CastVolumeKeyAction.Consume,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, isCasting = true, videoPlaybackActive = false)
        )
    }
}
