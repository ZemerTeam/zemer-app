package com.jtech.zemer.playback

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Pure HTTP plumbing for [CastStreamRelay] (no Android, no sockets): request-head parsing, the
 * Range/Content-Range math that lets an interrupted upstream body be resumed at the exact offset the
 * receiver already has, stream tokens, and URL-host formatting. Kept out of the server class so these
 * rules are pinned by plain JVM tests (CastRelayProtocolTest).
 */
object CastRelayProtocol {
    /** Path prefix of relayed streams: `/stream/<token>`. */
    const val STREAM_PATH_PREFIX = "/stream/"

    /** A parsed request head. Header names are lowercased (HTTP header names are case-insensitive). */
    data class RequestHead(val method: String, val path: String, val headers: Map<String, String>)

    /**
     * Parses a request head ([lines] = request line followed by header lines, no body). Null when the
     * request line isn't `METHOD target HTTP/x` — the caller answers 400. Malformed *header* lines are
     * skipped rather than fatal (receiver HTTP stacks vary).
     */
    fun parseRequestHead(lines: List<String>): RequestHead? {
        val requestLine = lines.firstOrNull() ?: return null
        val parts = requestLine.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return RequestHead(parts[0], parts[1], headers)
    }

    /**
     * The stream token in [path], or null when it isn't a well-formed `/stream/<hex token>` target
     * (the strict charset also rejects any traversal/query games — tokens are lowercase hex only).
     */
    fun tokenFromPath(path: String): String? {
        if (!path.startsWith(STREAM_PATH_PREFIX)) return null
        val token = path.removePrefix(STREAM_PATH_PREFIX).substringBefore('?')
        return token.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** An upstream `Content-Range: bytes start-end/total` (a `*` total parses as null). */
    data class ContentRange(val start: Long, val end: Long, val total: Long?)

    private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

    fun parseContentRange(header: String?): ContentRange? {
        val match = CONTENT_RANGE.matchEntire(header?.trim() ?: return null) ?: return null
        val (start, end, total) = match.destructured
        return ContentRange(start.toLong(), end.toLong(), total.toLongOrNull())
    }

    /**
     * The `Range` header that resumes an interrupted upstream body after [alreadyCopied] bytes of it
     * were already relayed: the original span ([range] is the first response's Content-Range; null when
     * that response was a plain 200) minus what the client already has.
     */
    fun continuationRangeHeader(range: ContentRange?, alreadyCopied: Long): String =
        if (range == null) "bytes=$alreadyCopied-" else "bytes=${range.start + alreadyCopied}-${range.end}"

    /** The absolute stream offset a continuation of ([range], [alreadyCopied]) must start at to splice. */
    fun continuationOffset(range: ContentRange?, alreadyCopied: Long): Long =
        (range?.start ?: 0L) + alreadyCopied

    /** An unguessable stream token: 128 random bits as lowercase hex. */
    fun newToken(random: SecureRandom): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /**
     * Formats [address] as an http URL host: dotted quad for IPv4, bracketed literal for routable
     * IPv6. Null for the wildcard (no route was chosen) and for link-local IPv6 — reaching that needs
     * an interface scope id, which is only meaningful on the phone and can't be expressed in a URL the
     * receiver resolves.
     */
    fun formatHostForUrl(address: InetAddress): String? = when {
        address.isAnyLocalAddress -> null
        address is Inet4Address -> address.hostAddress
        address is Inet6Address && !address.isLinkLocalAddress ->
            address.hostAddress?.substringBefore('%')?.let { "[$it]" }
        else -> null
    }
}
