package com.jtech.zemer.db.entities

import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.db.entities.LyricsEntity.Companion.PROVIDER_LEGACY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the lyrics cache gate. Not-found rows are stored with provider = null; the first gate
 * (`provider == null`) treated them as stale, so every play of a song without lyrics re-ran the whole
 * provider chain over the network. And the one-time purge deleted every pre-provider row, manual entries
 * included; those are now re-resolved once and kept when nothing answers.
 */
class LyricsCachePolicyTest {
    @Test
    fun `nothing cached fetches`() {
        assertTrue(LyricsEntity.needsFetch(null))
    }

    @Test
    fun `not found row is a negative cache and is not re-fetched`() {
        assertFalse(LyricsEntity.needsFetch(LyricsEntity("v", LYRICS_NOT_FOUND, provider = null)))
    }

    @Test
    fun `legacy row with a body is re-resolved once`() {
        assertTrue(LyricsEntity.needsFetch(LyricsEntity("v", "words", provider = null)))
    }

    @Test
    fun `rows with known provenance are not re-fetched`() {
        assertFalse(LyricsEntity.needsFetch(LyricsEntity("v", "words", provider = "SimpMusic")))
        assertFalse(LyricsEntity.needsFetch(LyricsEntity("v", "words", provider = "manual")))
        assertFalse(LyricsEntity.needsFetch(LyricsEntity("v", "words", provider = PROVIDER_LEGACY)))
    }

    @Test
    fun `found body replaces the row with its provenance`() {
        val row = LyricsEntity.resolved("v", LyricsEntity("v", "old words", null), "[00:01.00] new", "Zemer · jkaraoke ✓")
        assertEquals(LyricsEntity("v", "[00:01.00] new", "Zemer · jkaraoke ✓"), row)
    }

    @Test
    fun `not found keeps a legacy body instead of overwriting it`() {
        val manual = LyricsEntity("v", "typed by the user", provider = null)
        val row = LyricsEntity.resolved("v", manual, LYRICS_NOT_FOUND, null)
        assertEquals("typed by the user", row.lyrics)
        assertEquals(PROVIDER_LEGACY, row.provider)
        assertFalse("stamped rows leave the re-fetch loop", LyricsEntity.needsFetch(row))
    }

    @Test
    fun `not found with nothing cached stores the negative row`() {
        assertEquals(LyricsEntity("v", LYRICS_NOT_FOUND, null), LyricsEntity.resolved("v", null, LYRICS_NOT_FOUND, null))
        val stale = LyricsEntity("v", LYRICS_NOT_FOUND, null)
        assertEquals(stale, LyricsEntity.resolved("v", stale, LYRICS_NOT_FOUND, null))
    }
}
