package com.jtech.zemer.utils

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.lyrics.LyricsHttp
import com.jtech.zemer.offline.SubsetSyncClient
import com.jtech.zemer.search.ZemerSearchClient
import com.jtech.zemer.statuses.openStatusConnection
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `X-Zemer-App` marker reaches every Zemer API request the app makes - checked on the REAL clients by
 * capturing the outgoing request (no network: the probe path is answered/aborted before sending).
 */
class ZemerAppMarkerTest {

    private val expected = BuildConfig.VERSION_CODE.toString()

    private class Probed(val marker: String?) : RuntimeException()

    // What the client would send to [url]: intercepted after every plugin ran, aborted before the wire.
    private fun HttpClient.markerSentTo(url: String): String? = runBlocking {
        plugin(HttpSend).intercept { request ->
            if (request.url.buildString().endsWith(PROBE)) throw Probed(request.headers[ZemerAppMarker.HEADER])
            execute(request)
        }
        try {
            get(url)
            error("probe was not intercepted")
        } catch (p: Probed) {
            p.marker
        }
    }

    @Test
    fun `the marker is the plain versionCode`() {
        assertEquals(expected, ZemerAppMarker.value)
        assertTrue(expected.all(Char::isDigit))
    }

    @Test
    fun `every Zemer Ktor client sends the marker to search and content`() {
        assertTrue(ZemerSearchClient().client.hasZemerAppMarker())
        assertTrue(ZemerContentClient.client.hasZemerAppMarker())
        assertTrue(SubsetSyncClient().client.hasZemerAppMarker())
        assertTrue(LyricsHttp.client.hasZemerAppMarker())

        assertEquals(expected, ZemerSearchClient().client.markerSentTo("https://search.zemer.io/search$PROBE"))
        assertEquals(expected, SubsetSyncClient().client.markerSentTo("https://search.zemer.io/subset$PROBE"))
        assertEquals(expected, ZemerContentClient.client.markerSentTo("https://content.zemer.io/whitelist$PROBE"))
        assertEquals(expected, LyricsHttp.client.markerSentTo("https://search.zemer.io/lyrics$PROBE"))
    }

    @Test
    fun `the shared lyrics client never sends it to a third party`() {
        assertNull(LyricsHttp.client.markerSentTo("https://lrclib.net/api$PROBE"))
    }

    @Test
    fun `the status HttpURLConnection path sends it to Zemer hosts only`() {
        val own = openStatusConnection("https://content.zemer.io/status-own/zemer/rest/v1/public_posts", "zemer")
        assertEquals(expected, own.getRequestProperty(ZemerAppMarker.HEADER))
        assertEquals("zemer", own.getRequestProperty("apikey"))
        val thirdParty = openStatusConnection("https://example.supabase.co/rest/v1/public_posts", "k")
        assertNull(thirdParty.getRequestProperty(ZemerAppMarker.HEADER))
    }

    @Test
    fun `the OkHttp interceptor (YidStatus feed via content) is host-gated`() {
        var seen: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(ZemerAppMarkerInterceptor)
            .addInterceptor { chain ->
                seen = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("".toResponseBody()).build()
            }
            .build()
        client.newCall(Request.Builder().url("https://content.zemer.io/status-proxy/yid-status/functions/v1/feed").build()).execute().close()
        assertEquals(expected, seen!!.header(ZemerAppMarker.HEADER))
        client.newCall(Request.Builder().url("https://yidstatus.com/feed").build()).execute().close()
        assertNull(seen!!.header(ZemerAppMarker.HEADER))
    }

    @Test
    fun `media, tracking, stream and update hosts are exempt`() {
        listOf("tracking.zemer.io", "stream.zemer.io", "ghtrack.zemer.io", "nightly.zemer.io", "i.ytimg.com", null)
            .forEach { assertTrue(it.toString(), !ZemerAppMarker.appliesTo(it)) }
        assertTrue(ZemerAppMarker.appliesTo("SEARCH.zemer.io"))
    }

    private companion object {
        const val PROBE = "/__marker_probe__"
    }
}
