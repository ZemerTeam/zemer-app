package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #508: the launcher shortcut must resume whatever the persisted queue holds, however long
 * the asynchronous restore takes, and must only say "nothing to resume" when that is the truth.
 */
class ResumeShortcutTest {

    @Test
    fun `a command during the restore is parked, whatever the player holds so far`() {
        assertEquals(ResumeShortcut.Action.WAIT_FOR_RESTORE, ResumeShortcut.decide(restoreInFlight = true, mediaItemCount = 0))
        // Items already landing does not end the wait: the restore still pins playWhenReady=false last.
        assertEquals(ResumeShortcut.Action.WAIT_FOR_RESTORE, ResumeShortcut.decide(restoreInFlight = true, mediaItemCount = 12))
    }

    @Test
    fun `with nothing in flight the player's queue decides`() {
        assertEquals(ResumeShortcut.Action.PLAY, ResumeShortcut.decide(restoreInFlight = false, mediaItemCount = 1))
        assertEquals(ResumeShortcut.Action.PLAY, ResumeShortcut.decide(restoreInFlight = false, mediaItemCount = 250))
        // Persistent Queue off, never played, or a restore that produced no items.
        assertEquals(ResumeShortcut.Action.NOTHING_TO_RESUME, ResumeShortcut.decide(restoreInFlight = false, mediaItemCount = 0))
    }
}
