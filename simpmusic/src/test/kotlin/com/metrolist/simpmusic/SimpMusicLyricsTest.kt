package com.metrolist.simpmusic

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
        assertEquals("plain words", firstNonBlankLyrics(synced = "", plain = "plain words"))
    }

    @Test
    fun `non-blank synced wins over plain`() {
        assertEquals("[00:00] synced", firstNonBlankLyrics(synced = "[00:00] synced", plain = "plain"))
    }

    @Test
    fun `null synced falls through to plain`() {
        assertEquals("plain", firstNonBlankLyrics(synced = null, plain = "plain"))
    }

    @Test
    fun `whitespace-only synced falls through to plain`() {
        assertEquals("plain", firstNonBlankLyrics(synced = "   \n", plain = "plain"))
    }

    @Test
    fun `both blank or null gives null`() {
        assertNull(firstNonBlankLyrics(synced = "", plain = ""))
        assertNull(firstNonBlankLyrics(synced = null, plain = null))
        assertNull(firstNonBlankLyrics(synced = "  ", plain = null))
    }
}
