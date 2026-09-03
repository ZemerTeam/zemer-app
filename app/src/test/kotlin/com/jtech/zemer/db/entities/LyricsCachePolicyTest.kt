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
    fun `found body with nothing cached is stored with its provenance`() {
        assertEquals(LyricsEntity("v", "[00:01.00] new", "Zemer · jkaraoke"), LyricsEntity.resolved("v", null, "[00:01.00] new", "Zemer · jkaraoke"))
    }

    /** Regression: a legacy PLAIN body (possibly a pre-provider manual edit) was overwritten by any provider answer. */
    @Test
    fun `legacy plain body is kept even when a provider answers`() {
        val manual = LyricsEntity("v", "typed by the user", provider = null)
        val row = LyricsEntity.resolved("v", manual, "[00:01.00] new", "Zemer · jkaraoke")
        assertEquals(LyricsEntity("v", "typed by the user", PROVIDER_LEGACY), row)
        assertFalse(LyricsEntity.needsFetch(row))
    }

    /** A legacy SYNCED body is an old ungated LrcLib match (nobody types timestamps): replaced by the gated answer. */
    @Test
    fun `legacy synced body is replaced by a provider answer`() {
        val stale = LyricsEntity("v", "[00:01.00] こいこいこい", provider = null)
        assertEquals(LyricsEntity("v", "new words", "SimpMusic"), LyricsEntity.resolved("v", stale, "new words", "SimpMusic"))
        assertEquals(LyricsEntity("v", "[00:01.00] new", "Zemer · jkaraoke"), LyricsEntity.resolved("v", stale, "[00:01.00] new", "Zemer · jkaraoke"))
    }

    @Test
    fun `stamped rows are never re-resolved`() {
        val kept = LyricsEntity("v", "old words", PROVIDER_LEGACY)
        assertFalse(LyricsEntity.needsFetch(kept))
    }

    @Test
    fun `not found keeps a legacy body instead of overwriting it`() {
        for (body in listOf("typed by the user", "[00:01.00] synced legacy")) {
            val row = LyricsEntity.resolved("v", LyricsEntity("v", body, provider = null), LYRICS_NOT_FOUND, null)
            assertEquals(body, row.lyrics)
            assertEquals(PROVIDER_LEGACY, row.provider)
            assertFalse("stamped rows leave the re-fetch loop", LyricsEntity.needsFetch(row))
        }
    }

    @Test
    fun `not found with nothing cached stores the negative row`() {
        assertEquals(LyricsEntity("v", LYRICS_NOT_FOUND, null), LyricsEntity.resolved("v", null, LYRICS_NOT_FOUND, null))
        val stale = LyricsEntity("v", LYRICS_NOT_FOUND, null)
        assertEquals(stale, LyricsEntity.resolved("v", stale, LYRICS_NOT_FOUND, null))
    }
}
