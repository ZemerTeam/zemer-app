package com.jtech.zemer.net

import com.jtech.zemer.net.ZemerHeaders.withZemerHeaders
import com.jtech.zemer.sig.SigCanonical
import com.jtech.zemer.sig.ZemerSig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * The request signature over real HTTP, through both hooks: a test DNS sends Zemer hosts to a local server,
 * which checks the signature exactly as the real server does - over the request line it RECEIVED - so the
 * hooks are proven to sign the final on-the-wire URL. Non-Zemer hosts get nothing; a build without a key
 * sends no signature; the server clock is taken from Date.
 */
class ZemerHeadersTest {
    private class Seen(val method: String, val target: String, val headers: Map<String, String>, val sigCount: Int)

    private lateinit var server: ServerSocket
    private val seen = mutableListOf<Seen>()
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val local = InetAddress.getByName("127.0.0.1")
    private val dns = Dns { host -> if (host.endsWith("zemer.io") || host == "example.org") listOf(local) else Dns.SYSTEM.lookup(host) }
    @Volatile private var dateHeader = "Mon, 26 Oct 2026 19:00:00 GMT"

    private fun hmac(message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

    /** What the server does: recompute over the received method + request target with the header's t. */
    private fun verify(s: Seen): Boolean {
        val h = s.headers["x-zemer-sig"] ?: return false
        val f = h.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
        val q = s.target.indexOf('?')
        val path = if (q < 0) s.target else s.target.substring(0, q)
        val query = if (q < 0) null else s.target.substring(q + 1)
        val want = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(SigCanonical.message(s.method, path, query, f.getValue("t").toLong())))
        return f["k"] == ZemerSig.keyId && f["s"] == want
    }

    @Before
    fun setUp() {
        ZemerSig.signer = ::hmac
        ZemerSig.clockOffsetSec = 0
        server = ServerSocket(0, 50, local)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader()
                        val (method, target) = input.readLine().split(" ")
                        val lines = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                        val headers = lines.associate { it.substringBefore(":").lowercase() to it.substringAfter(":").trim() }
                        val sigCount = lines.count { it.substringBefore(":").equals("x-zemer-sig", ignoreCase = true) }
                        val len = headers["content-length"]?.toInt() ?: 0
                        repeat(len) { input.read() }
                        synchronized(seen) { seen += Seen(method, target, headers, sigCount) }
                        // redirects: same host (/hop-same -> /final?x=1) and off to a non-Zemer host (/hop-away)
                        val location = when {
                            target.startsWith("/hop-same") -> "/final?x=1"
                            target.startsWith("/hop-away") -> "http://example.org:${server.localPort}/away"
                            else -> null
                        }
                        val status = if (location != null) "302 Found\r\nLocation: $location" else "200 OK"
                        s.getOutputStream().apply {
                            write("HTTP/1.1 $status\r\nDate: $dateHeader\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".encodeToByteArray())
                            flush()
                        }
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        ZemerSig.signer = null
        ZemerSig.clockOffsetSec = 0
        server.close()
    }

    private fun okhttp() = OkHttpClient.Builder().dns(dns).withZemerHeaders().build()
    private fun okGet(url: String) = okhttp().newCall(Request.Builder().url(url).build()).execute().close()
    private fun seenSnapshot() = synchronized(seen) { seen.toList() }

    @Test
    fun `okhttp signs Zemer-host requests, verified over the received request line`() {
        val port = server.localPort
        okGet("http://search.zemer.io:$port/search?q=%D7%A9%D7%9C%D7%95%D7%9D&kidZone=0&allowFemale=0")
        okGet("http://stream.zemer.io:$port/stream?v=OxcRcrGRVpM&kind=video")
        okGet("http://content.zemer.io:$port/whitelist/version")
        seenSnapshot().forEach { s ->
            assertTrue("${s.target} ${s.headers["x-zemer-sig"]}", verify(s))
        }
        assertEquals(3, seenSnapshot().size)
    }

    @Test
    fun `nothing reaches non-Zemer hosts`() {
        val port = server.localPort
        okGet("http://example.org:$port/x")
        okGet("http://nightly.zemer.io:$port/api")
        assertEquals(2, seenSnapshot().size)
        seenSnapshot().forEach { s ->
            assertNull(s.headers["x-zemer-sig"])
        }
    }

    @Test
    fun `tracking is signed for a client that installs the hook (the crash reporter)`() {
        okGet("http://tracking.zemer.io:${server.localPort}/v1/crash")
        assertTrue(verify(seenSnapshot().single()))
    }

    @Test
    fun `a build without a key sends no signature, and a failing signer only drops the header`() {
        ZemerSig.signer = null
        okGet("http://search.zemer.io:${server.localPort}/search?q=a")
        ZemerSig.signer = { throw IllegalStateException("native failure") }
        okGet("http://search.zemer.io:${server.localPort}/search?q=b")
        ZemerSig.signer = { ByteArray(5) } // a wrong-sized MAC is never sent
        okGet("http://search.zemer.io:${server.localPort}/search?q=c")
        assertEquals(3, seenSnapshot().size)
        seenSnapshot().forEach { assertNull(it.headers["x-zemer-sig"]) }
        assertNull(ZemerSig.headerFor("GET", "/x", null))
    }

    @Test
    fun `ktor signs the final wire URL, parameters added in the builder included, and never a non-Zemer host`() = runBlocking {
        val port = server.localPort
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client ->
            client.get("http://search.zemer.io:$port/search") {
                parameter("q", "a b+c שלום")
                parameter("allowFemale", "0")
            }
            client.put("http://content.zemer.io:$port/prefs") { setBody("{}") }
            client.get("http://example.org:$port/page")
        }
        val all = seenSnapshot()
        assertEquals(3, all.size)
        all.filter { !it.headers["host"]!!.startsWith("example.org") }.forEach { s ->
            assertTrue("${s.method} ${s.target}", verify(s))
        }
        val third = all.single { it.headers["host"]!!.startsWith("example.org") }
        assertNull(third.headers["x-zemer-sig"])
    }

    @Test
    fun `the signature time follows the server clock from Date`() {
        dateHeader = "Mon, 26 Oct 2026 19:00:00 GMT" // epoch 1793041200
        okGet("http://content.zemer.io:${server.localPort}/whitelist/version")
        val offset = ZemerSig.clockOffsetSec
        assertTrue(offset != 0L)
        val header = ZemerSig.headerFor("GET", "/x", null, nowMs = 1_000_000L)!!
        assertEquals("t=${1_000L + offset}", header.split(';')[1])
        // an absent or garbage Date leaves the offset alone
        ZemerSig.onServerDate(null)
        ZemerSig.onServerDate("not a date")
        assertEquals(offset, ZemerSig.clockOffsetSec)
        // a non-Zemer response never moves the clock
        dateHeader = "Thu, 01 Jan 2026 00:00:00 GMT"
        okGet("http://example.org:${server.localPort}/x")
        assertEquals(offset, ZemerSig.clockOffsetSec)
    }

    @Test
    fun `the Zemer host gate`() {
        listOf("search.zemer.io", "content.zemer.io", "stream.zemer.io", "tracking.zemer.io", "SEARCH.ZEMER.IO")
            .forEach { assertTrue(it, ZemerHosts.isZemerHost(it)) }
        listOf("nightly.zemer.io", "zemer.io", "lrclib.net", "search.zemer.io.evil.com", "music.youtube.com")
            .forEach { assertFalse(it, ZemerHosts.isZemerHost(it)) }
    }

    private fun assertRedirects(get: (String) -> Unit) {
        val port = server.localPort
        get("http://search.zemer.io:$port/hop-same?q=1")
        get("http://search.zemer.io:$port/hop-away")
        val all = seenSnapshot()
        val same = all.single { it.target == "/final?x=1" }
        assertEquals("exactly one signature on the redirected hop", 1, same.sigCount)
        assertTrue("re-signed for the new path", verify(same))
        val away = all.single { it.target == "/away" }
        assertEquals("a hop to a non-Zemer host carries no signature", 0, away.sigCount)
        all.filter { it.target.startsWith("/hop-") }.forEach { assertTrue(it.target, verify(it)) }
    }

    @Test
    fun `okhttp signs every redirect hop for its own path and never one that leaves the Zemer hosts`() =
        assertRedirects { url -> okGet(url) }

    @Test
    fun `ktor signs every redirect hop once for its own path and never one that leaves the Zemer hosts`() = runBlocking {
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client -> assertRedirects { url -> runBlocking { client.get(url) } } }
    }

    @Test
    fun `an empty path is signed as the root the server sees`() {
        assertEquals(
            SigCanonical.message("GET", "/", "a=1", 5).decodeToString(),
            SigCanonical.message("GET", "", "a=1", 5).decodeToString(),
        )
    }
}
