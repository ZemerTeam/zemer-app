package com.jtech.zemer.lyrics.musixmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression: the Content settings row rendered the stored English outcome text raw; it now stores a code that the UI localises. */
class MusixmatchStatusTest {
    @Test
    fun `every status round-trips through its stored code`() {
        val all = listOf(
            MusixmatchStatus.Hit, MusixmatchStatus.HitSynced, MusixmatchStatus.Unauthorized, MusixmatchStatus.Network,
            MusixmatchStatus.NoMatch, MusixmatchStatus.NoLyrics, MusixmatchStatus.NoToken,
            MusixmatchStatus.Rejected(MusixmatchStatus.REASON_ARTIST, "Biton.AI"),
            MusixmatchStatus.Rejected(MusixmatchStatus.REASON_LENGTH, "240s / 250s"),
        )
        for (s in all) assertEquals(s.code, s, MusixmatchStatus.parse(s.code))
    }

    @Test
    fun `codes are stable identifiers, not display text`() {
        assertEquals("hit_synced", MusixmatchStatus.HitSynced.code)
        assertEquals("rejected:title:Burn", MusixmatchStatus.Rejected(MusixmatchStatus.REASON_TITLE, "Burn").code)
    }

    @Test
    fun `pre-code free text and blanks parse to nothing`() {
        assertNull(MusixmatchStatus.parse(null))
        assertNull(MusixmatchStatus.parse(""))
        assertNull(MusixmatchStatus.parse("token quota reached (captcha)"))
        assertNull(MusixmatchStatus.parse("rejected: artist X"))
    }

    @Test
    fun `an outcome carries its status`() {
        assertEquals(MusixmatchStatus.Network, MusixmatchLyrics.Outcome.Network.status)
        assertEquals(MusixmatchStatus.Rejected("artist", "X"), MusixmatchLyrics.Outcome.Rejected("artist", "X").status)
    }
}
