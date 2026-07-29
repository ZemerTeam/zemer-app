package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The issue #109 reboot gate: the post-boot SystemUI media-resumption scan must NOT trigger the
 * persisted-queue restore (an eager restore resurrects the media notification on a phone nobody
 * touched), while every real controller keeps restoring exactly as before.
 */
class QueueRestoreGateTest {

    @Test
    fun `the boot-time SystemUI resumption scanner never restores`() {
        assertFalse(shouldRestorePersistedQueue("com.android.systemui"))
    }

    @Test
    fun `an anonymous caller never restores`() {
        assertFalse(shouldRestorePersistedQueue(null))
    }

    @Test
    fun `every real controller restores - own app, Auto, Bluetooth, third parties`() {
        assertTrue(shouldRestorePersistedQueue("com.jtech.zemer"))
        assertTrue(shouldRestorePersistedQueue("com.google.android.projection.gearhead"))
        assertTrue(shouldRestorePersistedQueue("com.android.bluetooth"))
        assertTrue(shouldRestorePersistedQueue("com.example.some.controller"))
    }
}
