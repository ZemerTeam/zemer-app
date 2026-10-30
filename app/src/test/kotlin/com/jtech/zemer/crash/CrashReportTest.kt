package com.jtech.zemer.crash

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CrashReportTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val env = CrashEnvironment(38, "13.0", "abc123", 1234, "release", 30, "Google", "Pixel", "arm64-v8a", false)

    private fun build(t: Throwable, crumbs: List<String> = emptyList(), secrets: List<String> = emptyList()) =
        CrashReport.build("nonfatal", t, "main", 1_000L, 3, crumbs, env, secrets)

    @Test
    fun `scrubs URLs, tokens, IPs, emails and the uid, but not clock times`() {
        val s = CrashScrubber.scrub(
            "GET https://rr1.googlevideo.com/videoplayback?ip=203.0.113.9&pot=abc ok at 10:23:45 " +
                "visitorData=Cgt4 \"poToken\":\"xyz\" from 2001:db8::1 and fe80:0:0:0:1:2:3:4 by a.b@c.io uid uidSECRET99",
            secrets = listOf("uidSECRET99"),
        )
        assertEquals(
            "GET https://rr1.googlevideo.com/videoplayback ok at 10:23:45 visitorData=<redacted> " +
                "\"poToken\":\"<redacted>\" from <ip> and <ip> by <email> uid <uid>",
            s,
        )
        assertEquals("seen <ip> key=<redacted> n=<redacted>", CrashScrubber.scrub("seen 203.0.113.9 key=K1 n=2"))
    }

    @Test
    fun `the cause chain is outermost first, with suppressed exceptions and scrubbed messages`() {
        val root = IOException("connect to 198.51.100.7 failed")
        val top = IllegalStateException("load failed", root).apply { addSuppressed(RuntimeException("cleanup")) }
        val ex = build(top)["exception"]!!.jsonArray
        assertEquals(listOf("java.lang.IllegalStateException", "java.io.IOException"), ex.map { it.jsonObject["class"]!!.jsonPrimitive.content })
        assertEquals("connect to <ip> failed", ex[1].jsonObject["message"]!!.jsonPrimitive.content)
        assertEquals("cleanup", ex[0].jsonObject["suppressed"]!!.jsonArray[0].jsonObject["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a cause cycle ends the chain`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals(2, build(a)["exception"]!!.jsonArray.size)
    }

    @Test
    fun `the exception chain and breadcrumbs stay within their caps, keeping the newest lines`() {
        val deep = RuntimeException("deep").apply { stackTrace = Array(5_000) { StackTraceElement("C$it", "m", "F.kt", it) } }
        val report = build(deep, crumbs = (1..500).map { "line $it" })
        val stacks = report["exception"]!!.jsonArray.sumOf { it.jsonObject["stack"]!!.jsonPrimitive.content.length }
        assertTrue(stacks <= CrashReport.MAX_EXCEPTION_CHARS)
        val crumbs = report["breadcrumbs"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(CrashReport.MAX_BREADCRUMBS, crumbs.size)
        assertEquals("line 500", crumbs.last())
    }

    @Test
    fun `the report carries the contract fields`() {
        val r = build(RuntimeException("x"))
        listOf("kind", "occurredAt", "sentAt", "count", "app", "device", "thread", "exception", "breadcrumbs", "debug")
            .forEach { assertTrue(it, it in r) }
        assertEquals(38, r["app"]!!.jsonObject["versionCode"]!!.jsonPrimitive.content.toInt())
        assertEquals(5_000L, CrashReport.withSentAt(r, 5_000L)["sentAt"]!!.jsonPrimitive.long)
    }

    @Test
    fun `the fingerprint follows the root cause's class and top frames, not the message`() {
        fun at(vararg frames: String) = RuntimeException("m${frames.size}").apply {
            stackTrace = frames.map { StackTraceElement("C", it, "F.kt", 1) }.toTypedArray()
        }
        assertEquals(CrashReport.fingerprint(at("a", "b")), CrashReport.fingerprint(at("a", "b")))
        assertNotEquals(CrashReport.fingerprint(at("a", "b")), CrashReport.fingerprint(at("a", "c")))
        assertEquals(CrashReport.fingerprint(at("a")), CrashReport.fingerprint(IllegalStateException("wrap", at("a"))))
    }

    @Test
    fun `stored, rejected and oversize reports leave the queue, anything else stays`() {
        listOf(200, 202, 400, 413).forEach { assertEquals(CrashSendOutcome.DONE, crashSendOutcome(it)) }
        listOf(null, 429, 500, 503, 401).forEach { assertEquals(CrashSendOutcome.KEEP, crashSendOutcome(it)) }
    }

    @Test
    fun `a coroutine cancellation anywhere in the chain is not reported as an error`() {
        assertTrue(CrashReport.isCancellation(kotlinx.coroutines.CancellationException("Job was cancelled")))
        assertTrue(CrashReport.isCancellation(IllegalStateException("wrap", kotlinx.coroutines.CancellationException("x"))))
        assertTrue(CrashReport.isCancellation(InterruptedException()))
        assertFalse(CrashReport.isCancellation(IOException("down")))
    }

    @Test
    fun `reportedFrom keeps the reporting app frames, without the reporting machinery`() {
        fun frame(cls: String) = StackTraceElement(cls, "m", "F.kt", 1)
        val stack = arrayOf(
            frame("com.jtech.zemer.crash.CrashReporter"),
            frame("com.jtech.zemer.utils.CrashReportingTree"),
            frame("timber.log.Timber\$Forest"),
            frame("com.jtech.zemer.utils.UtilsKt"),
            frame("com.jtech.zemer.utils.SyncUtils\$syncAlbums\$1"),
            frame("kotlinx.coroutines.DispatchedTask"),
            frame("com.metrolist.innertube.YouTube"),
        )
        assertEquals(
            listOf("com.jtech.zemer.utils.SyncUtils\$syncAlbums\$1.m(F.kt:1)", "com.metrolist.innertube.YouTube.m(F.kt:1)"),
            CrashReport.reportedFrom(stack),
        )
        assertEquals(1, CrashReport.reportedFrom(stack, max = 1).size)
        val report = CrashReport.build("nonfatal", IOException("x"), "main", 1L, 1, emptyList(), env, reportedFrom = listOf("a.b(C.kt:1)"))
        assertEquals("a.b(C.kt:1)", report["reportedFrom"]!!.jsonArray[0].jsonPrimitive.content)
        assertTrue("reportedFrom" !in build(IOException("x")))
    }

    @Test
    fun `connectivity failures to any host are not reported, real errors are`() {
        val refused = java.net.ConnectException("Failed to connect to www.youtube.com/1.2.3.4:443")
        assertTrue(CrashReport.isConnectivityFailure(refused))
        assertTrue(CrashReport.isConnectivityFailure(IOException("wrap", IOException("connect failed: ECONNREFUSED (Connection refused)"))))
        assertTrue(CrashReport.isConnectivityFailure(java.net.UnknownHostException("Unable to resolve host \"content.zemer.io\"")))
        assertTrue(CrashReport.isConnectivityFailure(java.net.SocketTimeoutException("timeout")))
        assertTrue(CrashReport.isConnectivityFailure(io.ktor.client.plugins.HttpRequestTimeoutException("https://search.zemer.io/podcast-home-rows", 8_000)))
        assertTrue(CrashReport.isConnectivityFailure(java.net.ConnectException("Failed to connect to search.zemer.io/1.2.3.4:443")))
        assertTrue(CrashReport.isConnectivityFailure(java.net.SocketException("Connection reset")))
        assertTrue(CrashReport.isConnectivityFailure(java.nio.channels.UnresolvedAddressException()))
        assertTrue(CrashReport.isConnectivityFailure(javax.net.ssl.SSLHandshakeException("Connection closed by peer")))
        assertTrue(CrashReport.isConnectivityFailure(IOException("wrap", java.security.cert.CertPathValidatorException("Trust anchor not found"))))
        // okhttp's message for a connection dropped mid-response, as a ProtocolException or a plain IOException
        assertTrue(CrashReport.isConnectivityFailure(IOException("wrap", java.net.ProtocolException("unexpected end of stream"))))
        assertTrue(CrashReport.isConnectivityFailure(IOException("unexpected end of stream on https://rr4---sn-x.googlevideo.com/...")))
        assertFalse(CrashReport.isConnectivityFailure(IllegalStateException("search.zemer.io")))
        assertFalse(CrashReport.isConnectivityFailure(IOException("Zemer home-rows returned HTTP 502")))
        assertFalse(CrashReport.isConnectivityFailure(IOException("HTTP 400")))
    }

    @Test
    fun `non-fatals are reported once per kind per launch, with their repeat count, up to the cap`() {
        val d = PerLaunchDedup<String>(max = 2)
        assertTrue(d.offer("a") { "A" })
        assertFalse(d.offer("a") { "A2" })
        assertFalse(d.offer("a") { "A3" })
        assertTrue(d.offer("b") { "B" })
        assertFalse(d.offer("c") { "C" }) // over the per-launch cap
        assertEquals(listOf("A" to 3, "B" to 1), d.drain())
        assertFalse(d.offer("a") { "A4" }) // already reported this launch
        assertEquals(emptyList<Pair<String, Int>>(), d.drain())
    }

    @Test
    fun `the queue keeps the newest reports up to its cap`() {
        val q = CrashQueue(tmp.newFolder(), max = 3)
        repeat(5) { q.add(JsonObject(mapOf("n" to kotlinx.serialization.json.JsonPrimitive(it)))); Thread.sleep(2) }
        val kept = q.files().map { it.readText() }
        assertEquals(3, kept.size)
        assertFalse(kept.any { "\"n\":0" in it || "\"n\":1" in it })
    }
}
