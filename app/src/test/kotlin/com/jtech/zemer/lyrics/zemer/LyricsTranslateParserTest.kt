package com.jtech.zemer.lyrics.zemer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslateParserTest {
    private val page = """
        <html><head><title>Yaakov Shwekey - רחם (Rachem) lyrics</title></head><body>
        <div class="song-node-info">ignored</div>
        <div class="translate__text ltf-hide-original" lang="he" id="song-body">
          <div class="par">
            <div>רחם בחסדך<br>על עמך צורנו</div>
          </div>
          <div class="par">
            <div>על ציון משכן כבודך<br>זבול בית תפארתנו</div>
          </div>
        </div>
        <div class="translate__bottom">also ignored</div>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts the original-language body from nested divs, breaks on br, and ignores siblings`() {
        val text = LyricsTranslateParser.parse(page)!!
        val lines = text.lines().filter { it.isNotEmpty() }
        assertEquals(listOf("רחם בחסדך", "על עמך צורנו", "על ציון משכן כבודך", "זבול בית תפארתנו"), lines)
        assertTrue(!text.contains("ignored"))
    }

    @Test
    fun `a page without song-body (Cloudflare challenge, 404 shell) parses to null`() {
        assertNull(LyricsTranslateParser.parse("<html><head><title>Just a moment...</title></head><body>challenge-platform</body></html>"))
        assertNull(LyricsTranslateParser.parse("<html><body><div>no lyrics here</div></body></html>"))
    }

    @Test
    fun `provider serves server-inlined lyricstranslate text without fetching, page-parses as fallback, skips when both die`() = runBlocking {
        // Normal path: the server inlines the audio-verified text (the site's Cloudflare wall blocks
        // on-device fetches), so no network call is made at all.
        val inlined = ZemerLyricsClient.Resolved(
            videoId = "GJOY8rXS9Yc", verified = true,
            sources = listOf(ZemerLyricsClient.Source(type = "lyricstranslate", url = "https://lyricstranslate.com/en/yaakov-shwekey-rachem-lyrics.html", plain = "רחם בחסדך\nעל עמך צורנו\nעל ציון משכן כבודך\nזבול בית תפארתנו")),
        )
        val fetched = ArrayList<String>()
        val bodies = ZemerLyricsProvider.bodies(inlined, fetch = { fetched += it; page })
        assertEquals(listOf("lyricstranslate"), bodies.map { it.first })
        assertEquals("רחם בחסדך", bodies[0].second.lines().first())
        assertEquals("inlined text is served without a network fetch", 0, fetched.size)
        // Pointer-only form (older server): falls back to fetching + parsing the page; a dead fetch skips.
        val pointer = ZemerLyricsClient.Resolved(
            videoId = "GJOY8rXS9Yc", verified = true,
            sources = listOf(ZemerLyricsClient.Source(type = "lyricstranslate", url = "https://lyricstranslate.com/en/yaakov-shwekey-rachem-lyrics.html")),
        )
        val parsed = ZemerLyricsProvider.bodies(pointer, fetch = { page })
        assertEquals("רחם בחסדך", parsed[0].second.lines().first())
        assertTrue(ZemerLyricsProvider.bodies(pointer, fetch = { null }).isEmpty())
    }
}
