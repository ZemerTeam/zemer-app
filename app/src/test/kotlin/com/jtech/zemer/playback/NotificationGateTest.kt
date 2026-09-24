package com.jtech.zemer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #109: the media notification must never appear for a queue that was merely restored by a
 * service creation nobody asked for (the post-boot media-resumption scan), must always appear once
 * the user is in the loop or playback runs, and must never be re-posted while the service is
 * stopping for a task clear.
 */
class NotificationGateTest {

    @Test
    fun `a restored, paused queue with no user intent posts nothing - the reboot case`() {
        assertFalse(NotificationGate.shouldPost(userIntentSeen = false, playWhenReady = false, startInForegroundRequired = false))
    }

    @Test
    fun `playback starting posts even without prior intent`() {
        // A headset or Auto play command on the restored queue: the play IS the intent.
        assertTrue(NotificationGate.shouldPost(userIntentSeen = false, playWhenReady = true, startInForegroundRequired = false))
    }

    @Test
    fun `media3 requiring the foreground is never vetoed while running`() {
        assertTrue(NotificationGate.shouldPost(userIntentSeen = false, playWhenReady = false, startInForegroundRequired = true))
    }

    @Test
    fun `once the user is in the loop the paused notification shows as before`() {
        assertTrue(NotificationGate.shouldPost(userIntentSeen = true, playWhenReady = false, startInForegroundRequired = false))
        assertTrue(NotificationGate.shouldPost(userIntentSeen = true, playWhenReady = true, startInForegroundRequired = true))
    }

    @Test
    fun `nothing is scheduled while the service stops for a task clear - the zombie case`() {
        // The pause issued by the task clear must not re-post a paused notification onto a dead service.
        assertFalse(NotificationGate.shouldPost(userIntentSeen = true, playWhenReady = false, startInForegroundRequired = false, stoppingOnTaskClear = true))
        assertFalse(NotificationGate.shouldPost(userIntentSeen = true, playWhenReady = true, startInForegroundRequired = true, stoppingOnTaskClear = true))
    }
}
