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
        assertEquals("Zemer's own certified text is just Zemer", "Zemer", ZemerLyricsProvider.label("zemer", verified = true))
    }

    @Test
    fun `zemer certified word sync outranks jkaraoke and prefers richSync over syncedLrc over plain`() = runBlocking {
        val resolved = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-zemer-richsync.json"))
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch = { res("jkaraoke-page28.json") })
        assertEquals("zemer", bodies[0].first)
        assertTrue(bodies[0].second.lines().first().startsWith("[00:17.45] <00:17.45>"))
        val zemer = resolved.sources[0]
        assertEquals(zemer.syncedLrc, ZemerLyricsProvider.bodies(resolved.copy(sources = listOf(zemer.copy(richSync = null))), fetch = { null })[0].second)
        assertEquals(zemer.plain, ZemerLyricsProvider.bodies(resolved.copy(sources = listOf(zemer.copy(richSync = "", syncedLrc = null))), fetch = { null })[0].second)
    }

    @Test
    fun `manual rows are labelled by origin, community and unknown types rank last or are skipped`() = runBlocking {
        val resolved = ZemerLyricsClient.Resolved(videoId = "m", sources = listOf(
            ZemerLyricsClient.Source(type = "community", plain = "c\nc\nc\nc", ref = "x"),
            ZemerLyricsClient.Source(type = "manual", origin = "telegram", plain = "t\nt\nt\nt"),
            ZemerLyricsClient.Source(type = "manual", origin = "asrverified", plain = "v\nv\nv\nv"),
            ZemerLyricsClient.Source(type = "manual", origin = "forum", plain = "f\nf\nf\nf"),
            ZemerLyricsClient.Source(type = "manual", plain = "m\nm\nm\nm"),
            ZemerLyricsClient.Source(type = "future-type", plain = "z\nz\nz\nz"),
        ))
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch = { null })
        // community and manual share rank 4, so the server's order breaks the tie; an unknown type is skipped
        assertEquals(listOf("community", "Telegram", "verified", "forum", "manual"), bodies.map { it.first })
        assertEquals("Zemer · Telegram", ZemerLyricsProvider.label(bodies[1].first, verified = true))
        assertEquals("Apple Music", ZemerLyricsProvider.originName("apple"))
        assertEquals("YouTube", ZemerLyricsProvider.originName("youtube"))
    }

    @Test
    fun `apple row fetches the paxsenix TTML by catalogId and converts it to line-synced LRC`() = runBlocking {
        val resolved = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-apple-linetimes.json"))
        val asked = ArrayList<String>()
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch = { url -> asked += url; if (url == AppleTtmlLrc.url("1571752969")) res("apple-1571752969.json") else res("shironet-0.html") })
        assertEquals(listOf("apple", "shironet"), bodies.map { it.first })
        assertEquals(res("apple-1571752969.expected.lrc").trimEnd(), bodies[0].second)
        assertEquals(AppleTtmlLrc.url("1571752969"), asked[0])
        // an unsynced reply serves its plain text; a reply with nothing usable, a thin one, or a blank id yields nothing
        assertEquals("לפעמים", ZemerLyricsProvider.bodies(resolved.copy(sources = resolved.sources.take(1)), fetch = { res("apple-unsynced-reply.json") })[0].second.lines().first())
        assertTrue(ZemerLyricsProvider.bodies(resolved.copy(sources = resolved.sources.take(1)), fetch = { """{"type":"Line","lrc":"x"}""" }).isEmpty())
        assertTrue(ZemerLyricsProvider.bodies(resolved.copy(sources = resolved.sources.take(1)), fetch = { """{"ttmlContent":"<p begin=\"1.0\">a</p><p begin=\"2.0\">b</p>"}""" }).isEmpty())
        assertTrue(ZemerLyricsProvider.bodies(resolved.copy(sources = listOf(resolved.sources[0].copy(catalogId = ""))), fetch = { res("apple-1571752969.json") }).isEmpty())
    }

    @Test
    fun `sources walk synced first then by rank, the server order breaking ties, and a throwing source is skipped`() = runBlocking {
        val tab = "first line\nsecond line\nthird line\nfourth line"
        val times = ZemerLyricsClient.LineTimes("youtube", 4, listOf(1.0, 2.0, 3.0, 4.0), tab.lines().map(LineTimesLrc::lineKey))
        val resolved = ZemerLyricsClient.Resolved(videoId = "o", lineTimes = times, sources = listOf(
            ZemerLyricsClient.Source(type = "shironet", url = "https://shironet.mako.co.il/x"),               // plain pointer, rank 3
            ZemerLyricsClient.Source(type = "manual", origin = "telegram", syncedLrc = "[00:01.00] t\n[00:02.00] t\n[00:03.00] t\n[00:04.00] t"), // synced inline, rank 4
            ZemerLyricsClient.Source(type = "youtube", browseId = "MPLYt_x"),                                  // synced via lineTimes, rank 2
            ZemerLyricsClient.Source(type = "apple", catalogId = "1571752969", synced = true),                  // synced, rank 1
            ZemerLyricsClient.Source(type = "booklet", plain = "b\nb\nb\nb"),                                   // plain inline, rank 4
            ZemerLyricsClient.Source(type = "jyrics", url = "https://www.jyrics.com/lyrics/avraham/"),          // plain pointer, rank 3
        ))
        assertEquals(listOf("apple", "youtube", "manual", "shironet", "jyrics", "booklet"), ZemerLyricsProvider.order(resolved).map { it.type })
        val fetch: suspend (String) -> String? = { url -> when {
            url.contains("paxsenix") -> res("apple-1571752969.json")
            url.contains("shironet") -> throw IllegalStateException("network down")
            else -> res("jyrics-0.html")
        } }
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch, youtube = { tab })
        assertEquals(listOf("apple", "youtube", "Telegram", "jyrics", "booklet"), bodies.map { it.first })
        assertTrue(bodies[1].second.startsWith("[00:01.00] first line"))
        // firstOnly under the same order returns the synced apple body
        assertEquals(listOf("apple"), ZemerLyricsProvider.bodies(resolved, fetch, firstOnly = true, youtube = { tab }).map { it.first })
        // a cancelled walk stops at the cancelled source, never falling through to the next
        val cancelled = runCatching { ZemerLyricsProvider.bodies(resolved, fetch = { throw kotlinx.coroutines.CancellationException("gone") }, youtube = { tab }) }
        assertTrue(cancelled.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `youtube tab is fetched by browseId and lineTimes sync only the source they were measured against`() = runBlocking {
        val tab = "first line\nsecond line\nthird line\nfourth line"
        val times = ZemerLyricsClient.LineTimes("youtube", 4, listOf(1.0, 2.0, 3.0, 4.0), tab.lines().map(LineTimesLrc::lineKey))
        val resolved = ZemerLyricsClient.Resolved(videoId = "y", lineTimes = times, sources = listOf(
            ZemerLyricsClient.Source(type = "youtube", browseId = "MPLYt_x"),
            ZemerLyricsClient.Source(type = "shironet", url = "https://shironet.mako.co.il/x"),
        ))
        val asked = ArrayList<String>()
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch = { res("shironet-0.html") }, youtube = { asked += it; tab })
        assertEquals(listOf("MPLYt_x"), asked)
        assertEquals(listOf("youtube", "shironet"), bodies.map { it.first })
        assertEquals("[00:01.00] first line\n[00:02.00] second line\n[00:03.00] third line\n[00:04.00] fourth line", bodies[0].second)
        assertTrue("another pointer's body stays plain", !bodies[1].second.contains("[00:"))
        // timings that do not cover the tab leave it plain; a thin tab yields nothing
        assertEquals(tab, ZemerLyricsProvider.bodies(resolved.copy(lineTimes = times.copy(keys = List(4) { "00000000" })), fetch = { null }, youtube = { tab })[0].second)
        assertTrue(ZemerLyricsProvider.bodies(resolved.copy(lineTimes = null), fetch = { null }, youtube = { "one\ntwo" }).isEmpty())
    }

    @Test
    fun `tab4u and zemirotdb pages are fetched and parsed through the golden ports`() = runBlocking {
        val resolved = ZemerLyricsClient.Resolved(videoId = "t", sources = listOf(
            ZemerLyricsClient.Source(type = "tab4u", songId = 72017, url = "https://www.tab4u.com/tabs/songs/72017.html"),
            ZemerLyricsClient.Source(type = "zemirotdb", songId = 186, url = "https://www.zemirotdatabase.org/view_song.php?id=186"),
        ))
        val bodies = ZemerLyricsProvider.bodies(resolved, fetch = { url -> if (url.contains("tab4u")) res("tab4u-72017.html") else res("zemirotdb-186.html") })
        assertEquals(listOf("tab4u", "zemirotdb"), bodies.map { it.first })
        assertEquals(res("tab4u-72017.expected.txt").trimEnd(), bodies[0].second)
        assertEquals(res("zemirotdb-186.expected.txt").trimEnd(), bodies[1].second)
    }

    @Test
    fun `jkaraoke offsetSec is applied whether measured for this song or the fleet default`() = runBlocking {
        val src = ZemerLyricsClient.Source(type = "jkaraoke", songId = 1971, feedPage = 28, feedUrl = "https://jkaraoke.com/api/songs?page=28", synced = true)
        val fetch: suspend (String) -> String? = { res("jkaraoke-page28.json") }
        val raw = JkaraokeLrc.fromFeedPage(res("jkaraoke-page28.json"), 1971)!!.synced
        val body = { s: ZemerLyricsClient.Source -> runBlocking { ZemerLyricsProvider.bodies(ZemerLyricsClient.Resolved(videoId = "k", sources = listOf(s)), fetch)[0].second } }
        assertEquals(raw, body(src))
        val shifted = JkaraokeLrc.fromFeedPage(res("jkaraoke-page28.json"), 1971, 0.37)!!.synced
        assertEquals(shifted, body(src.copy(offsetSec = 0.37, offsetFrom = "default")))
        assertEquals(shifted, body(src.copy(offsetSec = 0.37, offsetFrom = null)))
        assertEquals(JkaraokeLrc.fromFeedPage(res("jkaraoke-page28.json"), 1971, 0.31)!!.synced, body(src.copy(offsetSec = 0.31, offsetFrom = "measured")))
        assertEquals(0.37, ZemerLyricsProvider.jkaraokeOffset(src.copy(offsetSec = 0.37, offsetFrom = "default")), 0.0)
        assertEquals(-0.2, ZemerLyricsProvider.jkaraokeOffset(src.copy(offsetSec = -0.2, offsetFrom = "measured")), 0.0)
        assertEquals(0.0, ZemerLyricsProvider.jkaraokeOffset(src), 0.0)
    }

    /** A `simpmusic` pointer is the verified entry of the track's own catalog, fetched by (videoId, entryId) with the server's synced flag. */
    @Test
    fun `simpmusic pointer fetches the exact entry under the track videoId, ranks with the timed pointers, and is skipped when the entry is gone`() = runBlocking {
        val lrc = "[00:01.00] one\n[00:02.00] two\n[00:03.00] three\n[00:04.00] four"
        val resolved = ZemerLyricsClient.Resolved(
            videoId = "aPnt1xqBv5c", verified = true, hasSynced = true,
            sources = listOf(
                ZemerLyricsClient.Source(type = "simpmusic", entryId = "6937f8a70012d3ecbb81", synced = true),
                ZemerLyricsClient.Source(type = "youtube", browseId = "MPLYt_x"),
            ),
        )
        val asked = ArrayList<Triple<String, String, Boolean>>()
        val bodies = ZemerLyricsProvider.bodies(
            resolved, fetch = { null }, youtube = { "plain\nyoutube\ntab\ntext" },
            simpmusic = { v, e, synced -> asked += Triple(v, e, synced); lrc },
        )
        assertEquals(listOf(Triple("aPnt1xqBv5c", "6937f8a70012d3ecbb81", true)), asked)
        assertEquals(listOf("simpmusic", "youtube"), bodies.map { it.first })
        assertEquals(lrc, bodies[0].second)
        assertEquals(2, ZemerLyricsProvider.rank(resolved.sources[0]))
        // the entry vanished upstream: the pointer yields nothing and the walk continues
        val gone = ZemerLyricsProvider.bodies(resolved, fetch = { null }, youtube = { "plain\nyoutube\ntab\ntext" }, simpmusic = { _, _, _ -> null })
        assertEquals(listOf("youtube"), gone.map { it.first })
        // a pointer naming the entry's own catalog (additive videoId) is fetched there, not under the track id
        val filed = ZemerLyricsClient.Resolved(videoId = "trackId", sources = listOf(ZemerLyricsClient.Source(type = "simpmusic", videoId = "otherCatalog", entryId = "e9", synced = false)))
        asked.clear()
        ZemerLyricsProvider.bodies(filed, fetch = { null }, simpmusic = { v, e, synced -> asked += Triple(v, e, synced); "a\nb\nc\nd" })
        assertEquals(listOf(Triple("otherCatalog", "e9", false)), asked)
        // no entryId (an older server) never fetches
        val noId = ZemerLyricsClient.Resolved(videoId = "x", sources = listOf(ZemerLyricsClient.Source(type = "simpmusic", synced = true)))
        assertTrue(ZemerLyricsProvider.bodies(noId, fetch = { null }, simpmusic = { _, _, _ -> error("must not be called") }).isEmpty())
    }
}
