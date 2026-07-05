package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the unknown-until-reported stepping rule: a hardware-key step must never act on the 1.0
 * placeholder — a receiver that never reported its volume (it might be sitting at 20%) would be SET
 * to ~93% by the first volume-DOWN press. Steps unlock only once the real level is known, via the
 * receiver's own report or an absolute slider set.
 */
class RemoteVolumeTrackerTest {

    @Test
    fun `step is refused while the receiver's level is unknown`() {
        val tracker = RemoteVolumeTracker()
        assertNull(tracker.step(-1))
        assertNull(tracker.step(+1))
        // The refused steps must not have disturbed the placeholder either.
        assertEquals(1.0, tracker.volume.value, 1e-9)
    }

    @Test
    fun `a receiver report unlocks stepping from the reported level`() {
        val tracker = RemoteVolumeTracker()
        tracker.onReceiverReport(0.2)
        assertEquals(0.2, tracker.volume.value, 1e-9)
        assertEquals(0.2 - CastPlayback.VOLUME_STEP, tracker.step(-1)!!, 1e-9)
        assertEquals(tracker.volume.value, 0.2 - CastPlayback.VOLUME_STEP, 1e-9)
    }

    @Test
    fun `an absolute slider set unlocks stepping from the set level`() {
        val tracker = RemoteVolumeTracker()
        assertEquals(0.5, tracker.setAbsolute(0.5), 1e-9)
        assertEquals(0.5 + CastPlayback.VOLUME_STEP, tracker.step(+1)!!, 1e-9)
    }

    @Test
    fun `reports and sets are clamped to the valid range`() {
        val tracker = RemoteVolumeTracker()
        tracker.onReceiverReport(1.7)
        assertEquals(1.0, tracker.volume.value, 1e-9)
        assertEquals(0.0, tracker.setAbsolute(-0.3), 1e-9)
        assertEquals(0.0, tracker.volume.value, 1e-9)
    }

    @Test
    fun `reset returns to the unknown placeholder for a fresh connection`() {
        val tracker = RemoteVolumeTracker()
        tracker.onReceiverReport(0.4)
        tracker.reset()
        assertEquals(1.0, tracker.volume.value, 1e-9)
        assertNull(tracker.step(-1))
    }
}
