package com.jtech.zemer.lyrics.zemer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZemerLyricsProviderTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `prefers synced jkaraoke, then jyrics, then hosted text, and skips sources that fail`() = runBlocking {
        val resolved = ZemerLyricsClient.Resolved(
            videoId = "sTCPij7kSwA", verified = true, hasSynced = true,
            sources = listOf(
                ZemerLyricsClient.Source(type = "jyrics", url = "https://www.jyrics.com/lyrics/avraham/"),
                ZemerLyricsClient.Source(type = "jkaraoke", songId = 1971, feedPage = 28, feedUrl = "https://jkaraoke.com/api/songs?page=28", synced = true),
                ZemerLyricsClient.Source(type = "booklet", plain = "hosted\ntext\nhere\nnow"),
            ),
        )
        val fetch: suspend (String) -> String? = { url -> when { url.contains("jkaraoke") -> res("jkaraoke-page28.json"); url.contains("jyrics") -> res("jyrics-0.html"); else -> null } }
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch)
        assertEquals(listOf("jkaraoke", "jyrics", "booklet"), bodies.map { it.first })
        assertTrue(bodies[0].second.startsWith("["))                     // LRC first
        assertEquals("שדוד עכו חולון", bodies[1].second.lines().first())  // parsed Jyrics plain
        // a dead source is skipped, not fatal
        val dead = ZemerLyricsProvider.bodies(resolved, fetch = { null })
        assertEquals(listOf("booklet"), dead.map { it.first })
    }

    @Test
    fun `firstOnly stops fetching after the first source that yields a body`() = runBlocking {
        val resolved = ZemerLyricsClient.Resolved(
            videoId = "sTCPij7kSwA", verified = false, hasSynced = true,
            sources = listOf(
                ZemerLyricsClient.Source(type = "jyrics", url = "https://www.jyrics.com/lyrics/avraham/"),
                ZemerLyricsClient.Source(type = "jkaraoke", songId = 1971, feedPage = 28, feedUrl = "https://jkaraoke.com/api/songs?page=28", synced = true),
            ),
        )
        val fetched = ArrayList<String>()
        val fetch: suspend (String) -> String? = { url -> fetched += url; if (url.contains("jkaraoke")) res("jkaraoke-page28.json") else res("jyrics-0.html") }
        val first = ZemerLyricsProvider.bodies(resolved, fetch, firstOnly = true)
        assertEquals(listOf("jkaraoke"), first.map { it.first })
        assertEquals("only the preferred source is downloaded", 1, fetched.size)
        // a dead preferred source still falls through to the next one
        fetched.clear()
        val fallback = ZemerLyricsProvider.bodies(resolved, { url -> fetched += url; if (url.contains("jyrics")) res("jyrics-0.html") else null }, firstOnly = true)
        assertEquals(listOf("jyrics"), fallback.map { it.first })
        assertEquals(2, fetched.size)
    }

    @Test
    fun `lrclib source fetches the record by id and prefers its LRC while instrumental and thin records yield nothing`() = runBlocking {
        val resolved = ZemerLyricsClient.Resolved(videoId = "lr1", hasSynced = true, sources = listOf(ZemerLyricsClient.Source(type = "lrclib", trackId = 1234, synced = true)))
        val synced = ZemerLyricsProvider.bodies(resolved, fetch = { url -> if (url == "https://lrclib.net/api/get/1234") """{"id":1234,"trackName":"Miracle","plainLyrics":"a\nb\nc\nd","syncedLyrics":"[00:01.00] a\n[00:02.00] b","instrumental":false}""" else null })
        assertEquals(listOf("lrclib"), synced.map { it.first })
        assertTrue(synced[0].second.startsWith("[00:01.00]"))
        val plainOnly = ZemerLyricsProvider.bodies(resolved, fetch = { """{"id":1234,"plainLyrics":"a\nb\nc\nd","syncedLyrics":null}""" })
        assertEquals("a\nb\nc\nd", plainOnly[0].second)
        assertTrue(ZemerLyricsProvider.bodies(resolved, fetch = { """{"id":1234,"instrumental":true,"plainLyrics":"a\nb\nc\nd"}""" }).isEmpty())
        assertTrue(ZemerLyricsProvider.bodies(resolved, fetch = { """{"id":1234,"plainLyrics":"too\nshort"}""" }).isEmpty())
        assertTrue(ZemerLyricsProvider.bodies(resolved, fetch = { "not json" }).isEmpty())
    }

    @Test
    fun `kugou source re-runs the krcs search by hash, takes only the vetted id, and decodes the LRC`() = runBlocking {
        val lrcB64 = java.util.Base64.getEncoder().encodeToString("[ti:x]\n[00:01.00]one\n[00:02.00]two\n[00:03.00]three\n[00:04.00]four".toByteArray())
        val hash = "959d52326c6c8b2919e80f8a40db48fc"
        val resolved = ZemerLyricsClient.Resolved(videoId = "kg1", hasSynced = true, sources = listOf(ZemerLyricsClient.Source(type = "kugou", hash = hash, krcId = 439665416, synced = true)))
        val fetch: suspend (String) -> String? = { url ->
            when {
                url == KugouLrc.searchUrl(hash) -> """{"candidates":[{"id":1,"accesskey":"OTHER"},{"id":439665416,"accesskey":"KEY"}]}"""
                url == KugouLrc.downloadUrl(439665416, "KEY") -> """{"content":"$lrcB64"}"""
                else -> null
            }
        }
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch)
        assertEquals(listOf("kugou"), bodies.map { it.first })
        assertEquals("[00:01.00]one", bodies[0].second.lines().first())      // metadata tag dropped, timed lines kept
        // the vetted id missing from the candidates = no body (never another candidate's accesskey)
        assertTrue(ZemerLyricsProvider.bodies(resolved, fetch = { url -> if (url == KugouLrc.searchUrl(hash)) """{"candidates":[{"id":1,"accesskey":"OTHER"}]}""" else null }).isEmpty())
        // fewer than 4 timed lines = no body
        val thinB64 = java.util.Base64.getEncoder().encodeToString("[00:01.00]one\n[00:02.00]two".toByteArray())
        assertTrue(ZemerLyricsProvider.bodies(resolved, fetch = { url -> if (url == KugouLrc.searchUrl(hash)) """{"candidates":[{"id":439665416,"accesskey":"KEY"}]}""" else """{"content":"$thinB64"}""" }).isEmpty())
    }

    @Test
    fun `label is one string for both the auto-fetch and picker paths`() {
        assertEquals("Zemer · jkaraoke", ZemerLyricsProvider.label("jkaraoke", verified = true))
        assertEquals("Zemer · jyrics", ZemerLyricsProvider.label("jyrics", verified = false))
    }
}
