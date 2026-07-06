package com.jtech.zemer.playback

import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A resolved upstream stream for the relay: the googlevideo URL plus its container MIME. */
data class RelayUpstream(val url: String, val contentType: String)

/**
 * Resolves the current upstream URL for a media id. Invoked on relay worker threads and may block on
 * network. [forceRefresh] means the previous URL was rejected upstream — drop caches and re-mint.
 */
fun interface RelayUpstreamResolver {
    fun resolve(mediaId: String, forceRefresh: Boolean): RelayUpstream?
}

/**
 * A minimal LAN HTTP server that proxies googlevideo streams to the cast receiver — Stage 2 of the
 * cast-403 fix. googlevideo binds a stream URL to the network identity that minted it and 403s every
 * new connection from a different one past the first free MiB; receivers behind CGNAT IPv4 (or a
 * different IPv6 prefix) can therefore never fetch the phone's URLs directly. Relaying makes the
 * fetching identity equal the minting identity by construction.
 *
 * Design constraints (see local/notes/cast-url-403-and-relay-fix.md for the measurements):
 *  - Alive only while casting: [CastConnector] hands out the first relay URL, [CastController] stops
 *    the relay after a disconnect grace period. Random port + a per-track 128-bit token is the whole
 *    exposure surface.
 *  - The socket binds the wildcard address so a Wi-Fi IP change doesn't kill it; only the advertised
 *    host can go stale, and [urlFor] re-derives it (route probe toward the receiver) per URL.
 *  - Client HEAD is translated to an upstream GET (body discarded): googlevideo HEAD false-negatives
 *    — 403s URLs that GET fine — the same reason WEB_REMIX skips validateStatus.
 *  - Upstream expiry/403 and mid-body drops are handled invisibly: re-resolve (forced on the last
 *    attempt) and splice at the exact byte offset the receiver already has.
 *  - One request per connection (`Connection: close`), CORS open — Chromecast receivers preflight.
 */
class CastStreamRelay(private val resolver: RelayUpstreamResolver) {
    private companion object {
        const val TAG = "CastRelay"
        const val MAX_WORKERS = 8 // receivers open a couple of connections (probe + play + seek)
        const val REQUEST_READ_TIMEOUT_MS = 15_000
        const val UPSTREAM_CONNECT_TIMEOUT_MS = 10_000
        const val UPSTREAM_READ_TIMEOUT_MS = 20_000

        /** Mid-body upstream failures resumed per client request before giving up. */
        const val UPSTREAM_RESUME_ATTEMPTS = 2
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val MAX_HEAD_BYTES = 16 * 1024

        /** Discard port; the UDP "connect" only consults the routing table, no packet is sent. */
        const val ROUTE_PROBE_PORT = 9
    }

    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var workers: ThreadPoolExecutor? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val random = SecureRandom()
    private val tokensByMediaId = ConcurrentHashMap<String, String>()
    private val mediaIdsByToken = ConcurrentHashMap<String, String>()

    /** The receiver's address, set at connect time — the route probe target for the URL host. */
    @Volatile
    var receiverAddress: InetAddress? = null

    /**
     * The relay URL the receiver should fetch [mediaId] through, or null when the relay can't serve
     * (no receiver address, no route toward it, or the server failed to start) — the caller falls back
     * to the raw googlevideo URL. Starts the server on first use; performs socket work (bind + route
     * probe), so call off the main thread.
     */
    fun urlFor(mediaId: String): String? {
        val receiver = receiverAddress ?: return null
        val port = start() ?: return null
        val host = localHostToward(receiver) ?: return null
        val token = tokensByMediaId.computeIfAbsent(mediaId) { id ->
            CastRelayProtocol.newToken(random).also { mediaIdsByToken[it] = id }
        }
        return "http://$host:$port${CastRelayProtocol.STREAM_PATH_PREFIX}$token"
    }

    /**
     * Whether [url] is one of this relay's live stream URLs — used by the error-recovery ladder to
     * decide if a de-relay (direct googlevideo URL) rung is worth trying. False after [stop] (tokens
     * are cleared) and for any foreign URL.
     */
    fun servesUrl(url: String?): Boolean {
        val path = runCatching { URI(url ?: return false).path }.getOrNull() ?: return false
        val token = CastRelayProtocol.tokenFromPath(path) ?: return false
        return mediaIdsByToken.containsKey(token)
    }

    /** Starts the server if needed and returns its port; null when listening can't be established. */
    private fun start(): Int? = synchronized(lock) {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        try {
            val server = ServerSocket(0) // wildcard bind: survives Wi-Fi address changes
            val pool = ThreadPoolExecutor(0, MAX_WORKERS, 30L, TimeUnit.SECONDS, SynchronousQueue())
            serverSocket = server
            workers = pool
            Thread({ acceptLoop(server, pool) }, "CastRelay-accept").apply {
                isDaemon = true
                start()
            }
            Timber.tag(TAG).i("Cast stream relay listening on port %d", server.localPort)
            server.localPort
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Cast stream relay failed to start")
            serverSocket = null
            workers = null
            null
        }
    }

    /** Stops the server and drops all state. Safe to call repeatedly / when never started. */
    fun stop() {
        val server: ServerSocket?
        val pool: ThreadPoolExecutor?
        synchronized(lock) {
            server = serverSocket
            pool = workers
            serverSocket = null
            workers = null
            receiverAddress = null
            tokensByMediaId.clear()
            mediaIdsByToken.clear()
        }
        server?.let { runCatching { it.close() } }
        pool?.shutdownNow()
        // Workers block on socket I/O, not interruptible waits — closing the sockets is what frees them.
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        if (server != null) Timber.tag(TAG).i("Cast stream relay stopped")
    }

    /**
     * The local address the OS would source traffic toward [receiver] from, formatted as a URL host.
     * A connected UDP socket consults the routing table without sending anything.
     */
    private fun localHostToward(receiver: InetAddress): String? = try {
        DatagramSocket().use { socket ->
            socket.connect(receiver, ROUTE_PROBE_PORT)
            CastRelayProtocol.formatHostForUrl(socket.localAddress)
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w("No route toward receiver %s: %s", receiver, e.message)
        null
    }

    private fun acceptLoop(server: ServerSocket, pool: ThreadPoolExecutor) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break // closed by stop()
            }
            clients.add(client)
            try {
                pool.execute { handleClient(client) }
            } catch (e: RejectedExecutionException) { // saturated or shut down
                clients.remove(client)
                runCatching { client.close() }
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = REQUEST_READ_TIMEOUT_MS
            client.tcpNoDelay = true
            val out = client.getOutputStream()
            val head = readRequestHead(client.getInputStream())?.let(CastRelayProtocol::parseRequestHead)
            when {
                head == null -> sendSimple(out, 400, "Bad Request")
                head.method == "OPTIONS" -> sendPreflight(out) // Chromecast CORS preflight
                head.method != "GET" && head.method != "HEAD" -> sendSimple(out, 405, "Method Not Allowed")
                else -> {
                    val mediaId = CastRelayProtocol.tokenFromPath(head.path)?.let { mediaIdsByToken[it] }
                    if (mediaId == null) sendSimple(out, 404, "Not Found") else serveStream(out, head, mediaId)
                }
            }
        } catch (e: IOException) {
            // Routine: receivers abort probe connections and drop the socket mid-body to seek.
            Timber.tag(TAG).d("Relay client connection ended: %s", e.message)
        } finally {
            clients.remove(client)
            runCatching { client.close() }
        }
    }

    /** Reads up to and including the CRLFCRLF head terminator; null on EOF / oversized head. */
    private fun readRequestHead(input: InputStream): List<String>? {
        val bytes = ByteArrayOutputStream()
        var tail = 0
        while (bytes.size() < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b == -1) return null
            bytes.write(b)
            tail = (tail shl 8) or b
            if (tail == 0x0D0A0D0A) {
                return bytes.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n")
            }
        }
        return null
    }

    private fun serveStream(out: OutputStream, head: CastRelayProtocol.RequestHead, mediaId: String) {
        var upstream = resolver.resolve(mediaId, forceRefresh = false)
            ?: return sendSimple(out, 502, "Bad Gateway")
        val rangeHeader = head.headers["range"]
        var conn = openUpstream(upstream.url, rangeHeader)
        var code = upstreamCode(conn)
        if (code != 200 && code != 206) {
            // The cached URL expired or was rejected (403) — re-mint once, invisibly to the receiver.
            Timber.tag(TAG).w("Upstream answered %d for %s — re-resolving fresh", code, mediaId)
            conn.disconnect()
            upstream = resolver.resolve(mediaId, forceRefresh = true)
                ?: return sendSimple(out, 502, "Bad Gateway")
            conn = openUpstream(upstream.url, rangeHeader)
            code = upstreamCode(conn)
            if (code != 200 && code != 206) {
                conn.disconnect()
                return sendSimple(out, 502, "Bad Gateway")
            }
        }
        val headers = mutableListOf(
            "Content-Type" to upstream.contentType, // the container MIME, never the codec MIME
            "Accept-Ranges" to "bytes",
            "Access-Control-Allow-Origin" to "*",
            "Connection" to "close",
        )
        conn.contentLengthLong.takeIf { it >= 0 }?.let { headers.add("Content-Length" to it.toString()) }
        if (code == 206) conn.getHeaderField("Content-Range")?.let { headers.add("Content-Range" to it) }
        writeHead(out, code, if (code == 206) "Partial Content" else "OK", headers)
        if (head.method == "HEAD") {
            conn.disconnect() // upstream was still a GET (HEAD false-negatives); just discard the body
            return
        }
        relayBody(out, conn, mediaId, code)
    }

    /**
     * Copies the upstream body to the client. An upstream drop (read error or EOF short of the
     * promised length) is resumed at the exact offset already relayed, up to [UPSTREAM_RESUME_ATTEMPTS]
     * times — the last attempt with a forced fresh URL. A client write error propagates to
     * [handleClient] (the receiver hung up; nothing to answer).
     */
    private fun relayBody(out: OutputStream, first: HttpURLConnection, mediaId: String, firstCode: Int) {
        var conn = first
        val range = if (firstCode == 206) CastRelayProtocol.parseContentRange(conn.getHeaderField("Content-Range")) else null
        val expected = conn.contentLengthLong.takeIf { it >= 0 }
        var copied = 0L
        var resumesLeft = UPSTREAM_RESUME_ATTEMPTS
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        try {
            while (true) {
                val read = try {
                    conn.inputStream.read(buffer)
                } catch (e: IOException) {
                    -2 // upstream failure — distinct from EOF
                }
                when {
                    read > 0 -> {
                        out.write(buffer, 0, read)
                        copied += read
                    }
                    read == -1 && (expected == null || copied >= expected) -> {
                        out.flush()
                        return // clean completion
                    }
                    else -> { // error, or EOF short of the promised length
                        if (resumesLeft == 0) {
                            Timber.tag(TAG).w("Upstream for %s kept failing; giving up at %d/%s bytes", mediaId, copied, expected)
                            return
                        }
                        resumesLeft--
                        conn.disconnect()
                        conn = reopenAtOffset(mediaId, range, copied, force = resumesLeft == 0) ?: return
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Reopens the upstream to continue after [copied] relayed bytes of the original span [range]
     * (null = the first response was a plain 200). Only a 206 starting at exactly the continuation
     * offset can be spliced into the bytes already sent; anything else returns null (give up).
     */
    private fun reopenAtOffset(
        mediaId: String,
        range: CastRelayProtocol.ContentRange?,
        copied: Long,
        force: Boolean,
    ): HttpURLConnection? {
        val offset = CastRelayProtocol.continuationOffset(range, copied)
        val upstream = resolver.resolve(mediaId, force) ?: return null
        val conn = openUpstream(upstream.url, CastRelayProtocol.continuationRangeHeader(range, copied))
        val code = upstreamCode(conn)
        val continued = CastRelayProtocol.parseContentRange(conn.getHeaderField("Content-Range"))
        if (code != 206 || continued == null || continued.start != offset) {
            Timber.tag(TAG).w("Upstream resume for %s at offset %d failed (code=%d, range=%s)", mediaId, offset, code, continued)
            conn.disconnect()
            return null
        }
        Timber.tag(TAG).w("Resumed upstream for %s at offset %d (forceRefresh=%b)", mediaId, offset, force)
        return conn
    }

    private fun openUpstream(url: String, rangeHeader: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            // Always GET, even for a client HEAD — googlevideo HEAD false-negatives (403s URLs that GET fine).
            requestMethod = "GET"
            connectTimeout = UPSTREAM_CONNECT_TIMEOUT_MS
            readTimeout = UPSTREAM_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            // Transparent gzip would break Content-Length/Content-Range fidelity toward the receiver.
            setRequestProperty("Accept-Encoding", "identity")
            rangeHeader?.let { setRequestProperty("Range", it) }
        }

    private fun upstreamCode(conn: HttpURLConnection): Int = try {
        conn.responseCode
    } catch (e: IOException) {
        -1
    }

    private fun sendPreflight(out: OutputStream) = writeHead(
        out, 204, "No Content",
        listOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
            "Access-Control-Allow-Headers" to "Range",
            "Connection" to "close",
        ),
    )

    private fun sendSimple(out: OutputStream, status: Int, reason: String) = writeHead(
        out, status, reason,
        listOf(
            "Content-Length" to "0",
            "Access-Control-Allow-Origin" to "*",
            "Connection" to "close",
        ),
    )

    private fun writeHead(out: OutputStream, status: Int, reason: String, headers: List<Pair<String, String>>) {
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            for ((name, value) in headers) append(name).append(": ").append(value).append("\r\n")
            append("\r\n")
        }
        out.write(head.toByteArray(StandardCharsets.ISO_8859_1))
        out.flush()
    }
}
