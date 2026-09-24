package com.jtech.zemer.lyrics.simpmusic

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
    fun `an unknown duration on either side never syncs and is not this recording`() {
        assertFalse(syncAllowed(null, 213))
        assertFalse(syncAllowed(213, 0))
        assertFalse(syncAllowed(null, 0))
        assertFalse(sameRecording(null, 213))
        assertFalse(sameRecording(213, 0))
    }

    @Test
    fun `identity is a known duration within five seconds`() {
        assertTrue(sameRecording(213, 213))
        assertTrue(sameRecording(218, 213))
        assertTrue(sameRecording(208, 213))
        assertFalse(sameRecording(219, 213))
        assertFalse(sameRecording(207, 213))
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

    /** A Zemer-vetted entry serves its timings only when the server's synced flag says they were verified. */
    @Test
    fun `a vetted entry serves synced bodies only under the server's synced flag, plain otherwise`() {
        val entry = SimpMusicLyricsData(id = "e1", duration = 299, syncedLyrics = "[00:01.00] synced", plainLyrics = "plain words", richSyncLyrics = "[00:01.00] <00:01.00>rich")
        assertEquals("[00:01.00] <00:01.00>rich", entryBody(entry, synced = true))
        assertEquals("plain words", entryBody(entry, synced = false))
        // synced flag but the entry carries no synced body: the words still serve
        assertEquals("plain words", entryBody(entry.copy(syncedLyrics = "", richSyncLyrics = null), synced = true))
        // an unsynced pointer never serves the entry's timings, even when plain text is missing
        assertNull(entryBody(entry.copy(plainLyrics = null), synced = false))
        assertNull(entryBody(null, synced = true))
    }
}
