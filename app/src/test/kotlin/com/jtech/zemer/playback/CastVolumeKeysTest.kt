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
            CastVolumeKeys.decide(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, isCasting = true)
        )
    }

    @Test
    fun `volume keys are ignored when not casting`() {
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, isCasting = false)
        )
        assertEquals(
            CastVolumeKeyAction.Ignore,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, isCasting = false)
        )
    }

    @Test
    fun `volume up on ACTION_DOWN while casting adjusts up`() {
        assertEquals(
            CastVolumeKeyAction.AdjustUp,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, isCasting = true)
        )
    }

    @Test
    fun `volume down on ACTION_DOWN while casting adjusts down`() {
        assertEquals(
            CastVolumeKeyAction.AdjustDown,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, isCasting = true)
        )
    }

    @Test
    fun `ACTION_UP while casting is consumed, not ignored`() {
        assertEquals(
            CastVolumeKeyAction.Consume,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, isCasting = true)
        )
        assertEquals(
            CastVolumeKeyAction.Consume,
            CastVolumeKeys.decide(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, isCasting = true)
        )
    }
}
