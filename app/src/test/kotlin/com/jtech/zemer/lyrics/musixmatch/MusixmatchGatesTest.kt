package com.jtech.zemer.lyrics.musixmatch

import com.jtech.zemer.lyrics.musixmatch.MusixmatchLyrics.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors zemer-search `harvester/lyrics-musixmatch.test.mjs`: the on-device gates must reject what the server rejects. */
class MusixmatchGatesTest {
    private val lyrics = "l1\nl2\nl3\nl4\nl5"
    private val sub = "[00:01.00] l1\n[00:02.00] l2\n[00:03.00] l3\n[00:04.00] l4\n[00:05.00] l5"
    private fun tr(name: String = "Candles on the Sill", artist: String = "Maccabeats", len: Int = 249) = Track(name, artist, len, 7, 9)
    private fun judge(t: Track?, body: String? = lyrics, instrumental: Boolean = false, s: String? = sub) =
        MusixmatchLyrics.judge(t, body, instrumental, false, s, "Candles on the Sill", null, "Maccabeats", null, 250)

    @Test
    fun `title and artist cleaning and identity`() {
        assertEquals("Rachem", MusixmatchLyrics.cleanTitle("Rachem - רחם (Live)"))
        assertEquals("שקט", MusixmatchLyrics.cleanTitle("שקט (Live)"))
        assertEquals("Avraham Fried", MusixmatchLyrics.cleanArtist("Avraham Fried feat. צמאה"))
        assertEquals("Yerachmiel Begun", MusixmatchLyrics.cleanArtist("Yerachmiel Begun & The Miami Boys Choir"))
        assertTrue(MusixmatchLyrics.artistMatches("Haim Israel", listOf("Chaim Israel", null)))
        assertTrue(MusixmatchLyrics.artistMatches("Hanan Ben Ari", listOf("Hanan Ben Ari")))
        assertFalse(MusixmatchLyrics.artistMatches("Biton.AI", listOf("Ishay Ribo", "ישי ריבו")))
        assertFalse(MusixmatchLyrics.artistMatches("Ari", listOf("Ari Goldwag")))
        assertTrue(MusixmatchLyrics.titleMatches("Vehi Sheamda", listOf("V'hi She'amda")))
        assertFalse(MusixmatchLyrics.titleMatches("Burn", listOf("Candles on the Sill")))
    }

    /** Regression: cleanLrc formatted with the default locale, so de-DE wrote "[00:01,20]" and ar wrote Arabic digits: no LRC parser reads either. */
    @Test
    fun `LRC timestamps are locale-independent`() {
        val saved = java.util.Locale.getDefault()
        try {
            for (locale in listOf(java.util.Locale.GERMANY, java.util.Locale.FRANCE, java.util.Locale("ar"), java.util.Locale("tr", "TR"))) {
                java.util.Locale.setDefault(locale)
                assertEquals(locale.toString(), "[00:01.20] a\n[00:02.50] b\n[00:03.00] c\n[00:04.00] d", MusixmatchLyrics.cleanLrc("[00:01.20] a\n[00:02.5] b\n[00:03.00] c\n[00:04.00] d"))
            }
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun `footer stripped, LRC kept only when well-formed and monotonic`() {
        assertEquals("a\nb\n\nc", MusixmatchLyrics.cleanLyrics("a\nb\n\n\n\nc\n******* This Lyrics is NOT for Commercial use *******\n(1409623)\n"))
        assertEquals("[00:01.20] a\n[00:02.50] b\n[00:03.00] c\n[00:04.00] d\n[00:05.00] ", MusixmatchLyrics.cleanLrc("[00:01.20] a\n[00:02.5] b\n[00:03.00] c\n[00:04.00] d\n[00:05.00] "))
        assertNull(MusixmatchLyrics.cleanLrc("[00:05.00] a\n[00:02.00] b\n[00:03.00] c\n[00:04.00] d"))
        assertNull(MusixmatchLyrics.cleanLrc("[00:01.00] a\n[00:02.00] b"))
    }

    @Test
    fun `judge gates artist, title and duration, sync only within 1 s, unreported length is text-only`() {
        val ok = judge(tr()); assertNotNull(ok); assertEquals("mxm:7@9", ok!!.ref); assertNotNull(ok.synced)
        assertNull(judge(tr(len = 252))!!.synced)          // 2 s: text only
        assertNull(judge(tr(len = 253)))                    // 3 s: another recording
        assertNull(judge(tr(len = 0))!!.synced); assertNotNull(judge(tr(len = 0)))
        assertNull(judge(tr(artist = "Six13")))
        assertNull(judge(tr(name = "Burn")))
        assertNull(judge(tr(), instrumental = true))
        assertNull(judge(null))
        assertNull(judge(tr(), body = "one\ntwo"))
    }
}

class MusixmatchParseTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `a matched track without a lyrics body yields nothing`() {
        // Real replies (2026-09-02): Musixmatch knows both tracks from Spotify metadata but holds no lyrics for them.
        for ((file, want) in listOf("musixmatch-0.json" to "Thank You Hashem", "musixmatch-1.json" to "Lehodos")) {
            val (track, ly, sub) = MusixmatchLyrics.parseMacro(res(file))!!
            assertEquals(want, track!!.trackName)
            assertNull(ly.body); assertNull(sub)
            assertNull(MusixmatchLyrics.judge(track, ly.body, ly.instrumental, ly.restricted, sub, want, null, track.artistName, null, track.trackLength))
        }
        assertNull(MusixmatchLyrics.parseMacro("{\"message\":{\"header\":{\"status_code\":401,\"hint\":\"captcha\"}}}"))
    }
}
