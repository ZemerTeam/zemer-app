package com.zemer.simpmusic

import com.metrolist.simpmusic.firstNonBlankLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
