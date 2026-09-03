package com.zemer.simpmusic

import com.metrolist.simpmusic.SimpMusicLyrics
import com.metrolist.simpmusic.durationDelta
import com.metrolist.simpmusic.firstNonBlankLyrics
import com.metrolist.simpmusic.syncAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: SimpMusic returns syncedLyrics = "" (empty, not null) for plain-only tracks. The old
 * elvis on syncedLyrics took the empty string, so the lyrics pane went permanently blank for songs
 * that actually had plain lyrics. firstNonBlankLyrics skips blank entries.
 */
class SimpMusicLyricsTest {
    @Test
    fun `blank synced falls through to plain`() {
        assertEquals("plain words", firstNonBlankLyrics("", "plain words"))
    }

    @Test
    fun `non-blank synced wins over plain`() {
        assertEquals("[00:00] synced", firstNonBlankLyrics("[00:00] synced", "plain"))
    }

    @Test
    fun `null synced falls through to plain`() {
        assertEquals("plain", firstNonBlankLyrics(null, "plain"))
    }

    @Test
    fun `whitespace-only synced falls through to plain`() {
        assertEquals("plain", firstNonBlankLyrics("   \n", "plain"))
    }

    @Test
    fun `both blank or null gives null`() {
        assertNull(firstNonBlankLyrics("", ""))
        assertNull(firstNonBlankLyrics(null, null))
        assertNull(firstNonBlankLyrics("  ", null))
    }

    @Test
    fun `word-synced wins over line-synced, and blank word-synced falls through`() {
        assertEquals("[00:01.00] <00:01.00>rich", firstNonBlankLyrics("[00:01.00] <00:01.00>rich", "[00:01.00] synced", "plain"))
        assertEquals("[00:01.00] synced", firstNonBlankLyrics("", "[00:01.00] synced", "plain"))
        assertEquals("plain", firstNonBlankLyrics(null, "", "plain"))
    }

    @Test
    fun `sync tolerance is one second`() {
        assertEquals(1, SimpMusicLyrics.SYNC_TOLERANCE_SEC)
    }

    /**
     * Regression: a missing durationSeconds was treated as a 0 s track, so any song with a known player
     * duration failed the 1 s gate and silently lost its synced body (served plain, or nothing).
     */
    @Test
    fun `unknown source duration is accepted, not treated as zero`() {
        assertTrue(syncAllowed(null, 213))
        assertTrue(syncAllowed(213, 0))
        assertTrue(syncAllowed(null, 0))
    }

    @Test
    fun `known durations must agree within the tolerance`() {
        assertTrue(syncAllowed(213, 213))
        assertTrue(syncAllowed(214, 213))
        assertFalse(syncAllowed(215, 213))
        assertEquals(1, SimpMusicLyrics.SYNC_TOLERANCE_SEC)
    }

    @Test
    fun `unknown durations rank last when picking the best match`() {
        assertEquals(2, durationDelta(211, 213))
        assertEquals(Int.MAX_VALUE, durationDelta(null, 213))
    }
}
