package com.jtech.zemer.playback

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End-to-end tests of [CastStreamRelay] over real loopback sockets: a raw-socket fake googlevideo
 * upstream on one side, a raw-socket "receiver" client on the other. Pins the behaviours the cast
 * session depends on: container-MIME/CORS/Range fidelity toward the receiver, HEAD→GET translation
 * (googlevideo HEAD false-negatives), the invisible re-mint on an upstream 403, and byte-exact
 * mid-stream resume after an upstream drop.
 */
class CastStreamRelayTest {

    private lateinit var upstream: FakeUpstream
    private lateinit var resolver: RecordingResolver
    private lateinit var relay: CastStreamRelay

    @Before
    fun setUp() {
        upstream = FakeUpstream()
        resolver = RecordingResolver { upstream.url }
        relay = CastStreamRelay(resolver)
        relay.receiverAddress = InetAddress.getLoopbackAddress()
    }

    @After
    fun tearDown() {
        relay.stop()
        upstream.close()
    }

    // --- urlFor ---

    @Test
    fun `urlFor is null without a receiver address and stable per media id with one`() {
        relay.receiverAddress = null
        assertNull(relay.urlFor("song-1"))

        relay.receiverAddress = InetAddress.getLoopbackAddress()
        val a = relay.urlFor("song-1")!!
        assertTrue(a, a.startsWith("http://127.0.0.1:"))
        assertEquals(a, relay.urlFor("song-1")) // same track → same URL (reloads re-use it)
        assertNotEquals(a, relay.urlFor("song-2")) // tokens are per track
    }

    @Test
    fun `servesUrl recognizes only this relay's live URLs`() {
        val url = relay.urlFor("song-1")!!
        assertTrue(relay.servesUrl(url))
        // Foreign / malformed URLs and unknown tokens are not ours (the error ladder must not offer a
        // de-relay rung for a load that was already direct).
        assertTrue(!relay.servesUrl("https://rr3---sn-x.googlevideo.com/videoplayback?a=1"))
        assertTrue(!relay.servesUrl("http://127.0.0.1:9/stream/${"ab".repeat(16)}"))
        assertTrue(!relay.servesUrl(null))
        assertTrue(!relay.servesUrl("not a url"))
        // stop() clears the token table: the URL is dead, so it stops counting as relay-served.
        relay.stop()
        assertTrue(!relay.servesUrl(url))
    }

    // --- basic serving ---

    @Test
    fun `serves the full body with the resolver's container MIME, CORS, and exact length`() {
        val resp = httpExchange(relay.urlFor("song")!!)
        assertEquals(200, resp.status)
        assertEquals("audio/webm", resp.headers["content-type"])
        assertEquals("*", resp.headers["access-control-allow-origin"])
        assertEquals("bytes", resp.headers["accept-ranges"])
        assertEquals(upstream.body.size.toString(), resp.headers["content-length"])
        assertArrayEquals(upstream.body, resp.body)
    }

    @Test
    fun `forwards a Range request and answers 206 with the exact slice and Content-Range`() {
        val resp = httpExchange(relay.urlFor("song")!!, headers = mapOf("Range" to "bytes=100-199"))
        assertEquals(206, resp.status)
        assertEquals("bytes 100-199/${upstream.body.size}", resp.headers["content-range"])
        assertArrayEquals(upstream.body.copyOfRange(100, 200), resp.body)
    }

    @Test
    fun `translates a client HEAD into an upstream GET and returns headers without a body`() {
        val resp = httpExchange(relay.urlFor("song")!!, method = "HEAD")
        assertEquals(200, resp.status)
        assertEquals(upstream.body.size.toString(), resp.headers["content-length"])
        assertEquals(0, resp.body.size)
        // Never forward HEAD upstream: googlevideo 403s HEADs on URLs that GET fine.
        assertEquals("GET", upstream.requests.single().method)
    }

    @Test
    fun `answers a CORS preflight with 204 and the allowed methods`() {
        val resp = httpExchange(relay.urlFor("song")!!, method = "OPTIONS")
        assertEquals(204, resp.status)
        assertEquals("GET, HEAD, OPTIONS", resp.headers["access-control-allow-methods"])
        assertEquals("*", resp.headers["access-control-allow-origin"])
    }

    @Test
    fun `rejects unknown tokens with 404 and other methods with 405`() {
        val url = relay.urlFor("song")!!
        val port = URL(url).port
        assertEquals(404, httpExchange("http://127.0.0.1:$port/stream/${"ab".repeat(16)}").status)
        assertEquals(404, httpExchange("http://127.0.0.1:$port/etc/passwd").status)
        assertEquals(405, httpExchange(url, method = "POST").status)
    }

    // --- upstream failure handling ---

    @Test
    fun `a rejected upstream URL is re-minted with forceRefresh invisibly to the client`() {
        upstream.reject403Remaining = 1
        val resp = httpExchange(relay.urlFor("song")!!)
        assertEquals(200, resp.status)
        assertArrayEquals(upstream.body, resp.body)
        // First resolve was the cached path; the 403 forced a fresh one.
        assertEquals(listOf(false, true), resolver.forceFlags.toList())
    }

    @Test
    fun `an upstream drop mid-body is resumed at the exact byte offset already relayed`() {
        upstream.truncateResponsesRemaining = 1
        upstream.truncateAfterBytes = 50_000
        val resp = httpExchange(relay.urlFor("song")!!)
        assertEquals(200, resp.status)
        assertArrayEquals(upstream.body, resp.body) // the receiver never notices the drop
        assertEquals("bytes=50000-", upstream.requests[1].headers["range"])
    }

    @Test
    fun `repeated upstream drops give up after the resume budget with the last attempt forced`() {
        upstream.truncateResponsesRemaining = 10
        upstream.truncateAfterBytes = 10_000
        val resp = httpExchange(relay.urlFor("song")!!)
        assertEquals(200, resp.status)
        // Initial fetch + 2 resumes, then give up — the client got exactly what arrived.
        assertEquals(30_000, resp.body.size)
        assertArrayEquals(upstream.body.copyOfRange(0, 30_000), resp.body)
        assertEquals(3, upstream.requests.size)
        assertEquals(listOf(false, false, true), resolver.forceFlags.toList())
    }

    @Test
    fun `a resolver returning null yields 502 rather than a hang`() {
        val url = relay.urlFor("song")!!
        resolver.resolveToNull = true
        assertEquals(502, httpExchange(url).status)
    }

    // --- lifecycle ---

    @Test
    fun `stop closes the server socket and refuses further connections`() {
        val url = relay.urlFor("song")!!
        assertEquals(200, httpExchange(url).status)
        relay.stop()
        val refused = try {
            httpExchange(url)
            false
        } catch (e: IOException) {
            true
        }
        assertTrue("expected the stopped relay to refuse connections", refused)
    }

    // --- helpers ---

    private class RecordingResolver(private val upstreamUrl: () -> String) : RelayUpstreamResolver {
        val forceFlags = CopyOnWriteArrayList<Boolean>()

        @Volatile
        var resolveToNull = false

        override fun resolve(mediaId: String, forceRefresh: Boolean): RelayUpstream? {
            forceFlags.add(forceRefresh)
            if (resolveToNull) return null
            return RelayUpstream(upstreamUrl(), "audio/webm")
        }
    }

    /**
     * A minimal googlevideo stand-in: serves a fixed body over raw sockets, honours `Range: bytes=a-b`
     * / `bytes=a-` with a proper 206 + Content-Range, and can be told to 403 the next N requests or
     * truncate the next N bodies after a byte count (announcing the full Content-Length, then closing
     * — how a real upstream drop looks to the relay).
     */
    private class FakeUpstream : AutoCloseable {
        val body = ByteArray(120_000) { ((it * 31) % 251).toByte() }
        val requests = CopyOnWriteArrayList<CastRelayProtocol.RequestHead>()

        @Volatile
        var reject403Remaining = 0

        @Volatile
        var truncateResponsesRemaining = 0

        @Volatile
        var truncateAfterBytes = 0

        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val url get() = "http://127.0.0.1:${server.localPort}/video"

        init {
            Thread({
                while (!server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    Thread({ handle(client) }).apply { isDaemon = true }.start()
                }
            }, "fake-upstream").apply {
                isDaemon = true
                start()
            }
        }

        private fun handle(client: Socket) {
            client.use { socket ->
                socket.soTimeout = 10_000
                val reader = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                val lines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    lines.add(line)
                }
                val head = CastRelayProtocol.parseRequestHead(lines) ?: return
                requests.add(head)
                val out = socket.getOutputStream()
                if (reject403Remaining > 0) {
                    reject403Remaining--
                    out.write(
                        "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .toByteArray(StandardCharsets.ISO_8859_1),
                    )
                    out.flush()
                    return
                }
                var start = 0
                var end = body.size - 1
                var status = "200 OK"
                var contentRange: String? = null
                val range = head.headers["range"]
                if (range != null) {
                    val match = Regex("""bytes=(\d+)-(\d*)""").matchEntire(range.trim())
                        ?: return // a malformed relay Range is a test failure by hang/empty response
                    start = match.groupValues[1].toInt()
                    end = match.groupValues[2].toIntOrNull() ?: (body.size - 1)
                    status = "206 Partial Content"
                    contentRange = "bytes $start-$end/${body.size}"
                }
                val slice = body.copyOfRange(start, end + 1)
                out.write(
                    buildString {
                        append("HTTP/1.1 ").append(status).append("\r\n")
                        append("Content-Type: application/octet-stream\r\n")
                        append("Content-Length: ").append(slice.size).append("\r\n")
                        contentRange?.let { append("Content-Range: ").append(it).append("\r\n") }
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(StandardCharsets.ISO_8859_1),
                )
                val truncate = truncateResponsesRemaining > 0
                if (truncate) truncateResponsesRemaining--
                out.write(slice, 0, if (truncate) minOf(truncateAfterBytes, slice.size) else slice.size)
                out.flush()
            } // close = the premature drop when truncated
        }

        override fun close() {
            runCatching { server.close() }
        }
    }

    private data class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    /** One raw-socket HTTP exchange (the receiver side): send a request, read to EOF, parse. */
    private fun httpExchange(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val target = URL(url)
        Socket(target.host, target.port).use { socket ->
            socket.soTimeout = 15_000
            val out = socket.getOutputStream()
            out.write(
                buildString {
                    append(method).append(' ').append(target.file).append(" HTTP/1.1\r\n")
                    append("Host: ").append(target.host).append(':').append(target.port).append("\r\n")
                    headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                    append("Connection: close\r\n\r\n")
                }.toByteArray(StandardCharsets.ISO_8859_1),
            )
            out.flush()
            val all = socket.getInputStream().readBytes()
            var split = -1
            for (i in 0..all.size - 4) {
                if (all[i] == 13.toByte() && all[i + 1] == 10.toByte() && all[i + 2] == 13.toByte() && all[i + 3] == 10.toByte()) {
                    split = i
                    break
                }
            }
            require(split >= 0) { "no header terminator in response: ${String(all, StandardCharsets.ISO_8859_1)}" }
            val headLines = String(all, 0, split, StandardCharsets.ISO_8859_1).split("\r\n")
            val status = headLines[0].split(' ')[1].toInt()
            val parsedHeaders = headLines.drop(1).associate { line ->
                val colon = line.indexOf(':')
                line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }
            return HttpResponse(status, parsedHeaders, all.copyOfRange(split + 4, all.size))
        }
    }
}
