package com.jtech.zemer.net

import com.jtech.zemer.net.ZemerHeaders.withZemerHeaders
import com.jtech.zemer.sig.SigCanonical
import com.jtech.zemer.sig.ZemerSig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.security.MessageDigest
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
    private class Seen(val method: String, val target: String, val headers: Map<String, String>, val sigCount: Int, val body: ByteArray)

    private lateinit var server: ServerSocket
    private val seen = mutableListOf<Seen>()
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val local = InetAddress.getByName("127.0.0.1")
    private val dns = Dns { host -> if (host.endsWith("zemer.io") || host == "example.org") listOf(local) else Dns.SYSTEM.lookup(host) }
    private val build = com.jtech.zemer.BuildConfig.VERSION_CODE.toString()
    @Volatile private var dateHeader = "Mon, 26 Oct 2026 19:00:00 GMT"

    private fun hmac(message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

    private fun fields(s: Seen) = s.headers["x-zemer-sig"]?.split(';')?.associate { it.substringBefore('=') to it.substringAfter('=') }

    /**
     * What the server does: recompute over the received method + request target with the header's t, and for
     * version 2 also its nonce and the hash of the body bytes RECEIVED.
     */
    private fun verify(s: Seen): Boolean {
        val f = fields(s) ?: return false
        val q = s.target.indexOf('?')
        val path = if (q < 0) s.target else s.target.substring(0, q)
        val query = if (q < 0) null else s.target.substring(q + 1)
        val t = f.getValue("t").toLong()
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val message = if (f["v"] == "2") {
            if (f["b"] != b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.body))) return false
            SigCanonical.message2(s.method, path, query, t, f.getValue("n"), f.getValue("b"))
        } else {
            SigCanonical.message(s.method, path, query, t)
        }
        return f["k"] == ZemerSig.keyId && f["s"] == b64.encodeToString(hmac(message))
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
                        // test bodies are ASCII, so chars read = bytes sent
                        val len = headers["content-length"]?.toInt() ?: 0
                        val body = buildString { repeat(len) { append(input.read().toChar()) } }.toByteArray()
                        synchronized(seen) { seen += Seen(method, target, headers, sigCount, body) }
                        // redirects: same host (/hop-same -> /final?x=1) and off to a non-Zemer host (/hop-away)
                        val location = when {
                            target.startsWith("/hop-same") -> "/final?x=1"
                            target.startsWith("/hop-away") -> "http://example.org:${server.localPort}/away"
                            else -> null
                        }
                        // refusals: the servers' JSON answer, the relay's plain-text one, and another 403
                        val (status, reply) = when {
                            location != null -> "302 Found\r\nLocation: $location" to "ok"
                            target.startsWith("/unofficial-json") -> "403 Forbidden\r\nContent-Type: application/json" to
                                """{"error":"unofficial_copy","message":"json message"}"""
                            target.startsWith("/unofficial-relay") -> "403 Forbidden\r\nContent-Type: text/plain\r\nX-Zemer-Refusal: unofficial_copy" to "relay message"
                            target.startsWith("/update-required") -> "403 Forbidden\r\nContent-Type: application/json" to """{"error":"update_required"}"""
                            // clock skew: /skew-once refuses only its first request, /skew-always every one
                            target.startsWith("/skew-") && (target.startsWith("/skew-always") || synchronized(seen) { seen.count { it.target == target } } == 1) ->
                                if (target.contains("relay")) "403 Forbidden\r\nContent-Type: text/plain\r\nX-Zemer-Refusal: clock_skew" to "clock message"
                                else "403 Forbidden\r\nContent-Type: application/json" to """{"error":"clock_skew","message":"clock message"}"""
                            else -> "200 OK" to "ok"
                        }
                        s.getOutputStream().apply {
                            write("HTTP/1.1 $status\r\nDate: $dateHeader\r\nContent-Length: ${reply.length}\r\nConnection: close\r\n\r\n$reply".encodeToByteArray())
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
        UnofficialCopy.reset()
        server.close()
    }

    @Test
    fun `two unofficial-copy answers from any servers refuse the copy, then no Zemer request leaves the device`() = runBlocking {
        val port = server.localPort
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client ->
            client.get("http://content.zemer.io:$port/unofficial-json")
            client.get("http://search.zemer.io:$port/update-required")
            assertFalse("another refusal in between resets the count", UnofficialCopy.refused)
            client.get("http://content.zemer.io:$port/unofficial-json")
            assertFalse(UnofficialCopy.refused)
            okGet("http://stream.zemer.io:$port/unofficial-relay")
            assertEquals("relay message", UnofficialCopy.message.value)
            val before = seenSnapshot().size
            assertTrue(runCatching { client.get("http://search.zemer.io:$port/search?q=a") }.exceptionOrNull() is UnofficialCopy.Refused)
            assertTrue(runCatching { okGet("http://tracking.zemer.io:$port/v1/events") }.exceptionOrNull() is UnofficialCopy.Refused)
            client.get("http://example.org:$port/page") // other hosts are untouched
            assertEquals(before + 1, seenSnapshot().size)
        }
        Unit
    }

    @Test
    fun `the OkHttp answer is peeked without taking the body from the caller`() {
        repeat(2) {
            val body = okhttp().newCall(Request.Builder().url("http://content.zemer.io:${server.localPort}/unofficial-json").build())
                .execute().use { it.body.string() }
            assertTrue(body, body.contains("unofficial_copy"))
        }
        assertEquals("json message", UnofficialCopy.message.value)
    }

    @Test
    fun `the Ktor JSON answer is read without taking the body from the caller`() = runBlocking {
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client ->
            val body = client.get("http://content.zemer.io:${server.localPort}/unofficial-json").bodyAsText()
            assertTrue(body, body.contains("unofficial_copy"))
            client.get("http://content.zemer.io:${server.localPort}/unofficial-json")
        }
        assertEquals("json message", UnofficialCopy.message.value)
    }

    /** Every request to [target] was signed; the resend has its own nonce and the server's clock from the refusal. */
    private fun assertResentOnce(target: String, requests: Int) {
        val sent = seenSnapshot().filter { it.target == target }
        assertEquals(requests, sent.size)
        sent.forEach { assertTrue(it.target, verify(it)) }
        val f = sent.map { fields(it)!! }
        assertEquals(requests, f.map { it["n"] }.toSet().size)
        assertTrue("signed with the wrong clock first", kotlin.math.abs(f.first().getValue("t").toLong() - SERVER_SEC) > 1_000_000)
        assertEquals(SERVER_SEC.toDouble(), f.last().getValue("t").toDouble(), 2.0)
    }

    private val SERVER_SEC = 1_793_041_200L // dateHeader

    private fun wrongClock() {
        ZemerSig.clockOffsetSec = -100_000_000 // the device clock is years off
    }

    @Test
    fun `a clock-skew refusal is resent once with the server clock and never counts toward the unofficial copy`() = runBlocking {
        val port = server.localPort
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client ->
            wrongClock()
            assertEquals("ok", client.get("http://content.zemer.io:$port/skew-once").bodyAsText())
            assertResentOnce("/skew-once", 2)
            wrongClock()
            assertEquals("ok", client.post("http://tracking.zemer.io:$port/skew-once-post") { setBody("""{"a":1}""") }.bodyAsText())
            assertResentOnce("/skew-once-post", 2)
            client.get("http://content.zemer.io:$port/unofficial-json")
            wrongClock()
            val failed = runCatching { client.get("http://search.zemer.io:$port/skew-always") }.exceptionOrNull()
            assertTrue("$failed", failed is ZemerRefusal.ClockSkew && failed is java.io.IOException)
            assertResentOnce("/skew-always", 2)
            assertFalse(UnofficialCopy.refused)
            client.get("http://content.zemer.io:$port/unofficial-json")
        }
        assertTrue("the clock refusals in between did not reset the count", UnofficialCopy.refused)
    }

    @Test
    fun `okhttp resends a clock-skew refusal once, the relay's header answer too`() {
        val port = server.localPort
        wrongClock()
        val body = okhttp().newCall(Request.Builder().url("http://stream.zemer.io:$port/skew-once-relay").build()).execute().use { it.body.string() }
        assertEquals("ok", body)
        assertResentOnce("/skew-once-relay", 2)
        okGet("http://content.zemer.io:$port/unofficial-json")
        wrongClock()
        val failed = runCatching { okGet("http://content.zemer.io:$port/skew-always") }.exceptionOrNull()
        assertTrue("$failed", failed is ZemerRefusal.ClockSkew)
        assertResentOnce("/skew-always", 2)
        assertFalse(UnofficialCopy.refused)
        okGet("http://stream.zemer.io:$port/unofficial-relay")
        assertTrue(UnofficialCopy.refused)
        // a non-Zemer host's 403 is never resent
        okGet("http://example.org:$port/skew-always-away")
        assertEquals(1, seenSnapshot().count { it.target == "/skew-always-away" })
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
            assertEquals(build, s.headers["x-zemer-build"])
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
            assertNull(s.headers["x-zemer-build"])
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
        seenSnapshot().forEach {
            assertNull(it.headers["x-zemer-sig"])
            assertEquals("the build header does not need a key", build, it.headers["x-zemer-build"])
        }
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
        assertNull(third.headers["x-zemer-build"])
        all.filter { it !== third }.forEach { assertEquals(build, it.headers["x-zemer-build"]) }
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
        assertNull("nor the build header", away.headers["x-zemer-build"])
        assertEquals(build, same.headers["x-zemer-build"])
        all.filter { it.target.startsWith("/hop-") }.forEach { assertTrue(it.target, verify(it)) }
        val signed = all.mapNotNull { fields(it) }
        assertTrue("every hop is signed version 2", signed.all { it["v"] == "2" })
        assertEquals("every hop has its own nonce", signed.size, signed.map { it["n"] }.toSet().size)
    }

    @Test
    fun `bodies are signed version 2 over the bytes sent, and a one-shot body falls back to version 1`() = runBlocking {
        val port = server.localPort
        val json = "application/json".toMediaType()
        okhttp().newCall(Request.Builder().url("http://tracking.zemer.io:$port/v1/events").post("""{"a":1}""".toRequestBody(json)).build()).execute().close()
        val oneShot = object : RequestBody() {
            override fun contentType() = json
            override fun contentLength() = 2L
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { sink.writeUtf8("{}") }
        }
        okhttp().newCall(Request.Builder().url("http://content.zemer.io:$port/reports").post(oneShot).build()).execute().close()
        HttpClient(OkHttp) {
            engine { config { dns(dns) } }
            install(ZemerHeaders.KtorPlugin)
        }.use { client -> client.post("http://tracking.zemer.io:$port/v1/crash") { setBody(ByteArray(3) { 'x'.code.toByte() }) } }
        val all = seenSnapshot()
        assertEquals(3, all.size)
        all.forEach { assertTrue("${it.method} ${it.target}", verify(it)) }
        val version = all.associate { it.target to fields(it)!!["v"] }
        assertEquals(mapOf("/v1/events" to "2", "/reports" to null, "/v1/crash" to "2"), version)
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
    fun `the environment signals ride Zemer hops only, and only when one is set`() {
        val port = server.localPort
        try {
            okGet("http://search.zemer.io:$port/search?q=a")
            EnvSignals.mask = EnvSignals.HOOKING or EnvSignals.EMULATOR
            okGet("http://search.zemer.io:$port/search?q=b")
            okGet("http://example.org:$port/x")
            okGet("http://search.zemer.io:$port/hop-away")
        } finally {
            EnvSignals.mask = 0
        }
        val env = seenSnapshot().associate { it.target to it.headers["x-zemer-env"] }
        assertNull("nothing set, no header", env["/search?q=a"])
        assertEquals("12", env["/search?q=b"])
        assertEquals("12", env["/hop-away"])
        assertNull(env["/x"])
        assertNull("not on a redirect off the Zemer hosts", env["/away"])
    }

    @Test
    fun `an empty path is signed as the root the server sees`() {
        assertEquals(
            SigCanonical.message("GET", "/", "a=1", 5).decodeToString(),
            SigCanonical.message("GET", "", "a=1", 5).decodeToString(),
        )
    }
}
