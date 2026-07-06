package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Pins the pure HTTP/Range/token rules of the cast stream relay. The relay's correctness toward the
 * receiver hangs on these exact behaviours: strict token matching (the whole auth surface), byte-exact
 * resume offsets after an upstream drop, and never advertising a host the receiver can't reach.
 */
class CastRelayProtocolTest {

    // --- parseRequestHead ---

    @Test
    fun `parses a request line and lowercases header names`() {
        val head = CastRelayProtocol.parseRequestHead(
            listOf("GET /stream/abc HTTP/1.1", "Host: 10.0.0.2:1234", "RANGE: bytes=0-", ""),
        )!!
        assertEquals("GET", head.method)
        assertEquals("/stream/abc", head.path)
        assertEquals("bytes=0-", head.headers["range"])
        assertEquals("10.0.0.2:1234", head.headers["host"])
    }

    @Test
    fun `rejects a malformed request line but skips malformed header lines`() {
        assertNull(CastRelayProtocol.parseRequestHead(listOf("GARBAGE")))
        assertNull(CastRelayProtocol.parseRequestHead(listOf("GET /x")))
        assertNull(CastRelayProtocol.parseRequestHead(listOf("GET /x NOTHTTP")))
        assertNull(CastRelayProtocol.parseRequestHead(emptyList()))
        // A junk header line must not kill the request (receiver HTTP stacks vary).
        val head = CastRelayProtocol.parseRequestHead(listOf("GET /x HTTP/1.1", "no-colon-here", "A: b"))!!
        assertEquals(mapOf("a" to "b"), head.headers)
    }

    // --- tokenFromPath ---

    @Test
    fun `extracts a hex token and strips any query`() {
        assertEquals("00ff", CastRelayProtocol.tokenFromPath("/stream/00ff"))
        assertEquals("00ff", CastRelayProtocol.tokenFromPath("/stream/00ff?x=1"))
    }

    @Test
    fun `rejects non-token paths including traversal and uppercase`() {
        assertNull(CastRelayProtocol.tokenFromPath("/"))
        assertNull(CastRelayProtocol.tokenFromPath("/stream/"))
        assertNull(CastRelayProtocol.tokenFromPath("/other/00ff"))
        assertNull(CastRelayProtocol.tokenFromPath("/stream/../etc/passwd"))
        assertNull(CastRelayProtocol.tokenFromPath("/stream/00FF")) // tokens are lowercase hex only
        assertNull(CastRelayProtocol.tokenFromPath("/stream/00ff/extra"))
    }

    // --- parseContentRange ---

    @Test
    fun `parses Content-Range including an unknown total`() {
        assertEquals(
            CastRelayProtocol.ContentRange(100, 199, 5000),
            CastRelayProtocol.parseContentRange("bytes 100-199/5000"),
        )
        assertEquals(
            CastRelayProtocol.ContentRange(0, 99, null),
            CastRelayProtocol.parseContentRange(" bytes 0-99/* "),
        )
    }

    @Test
    fun `rejects absent or malformed Content-Range`() {
        assertNull(CastRelayProtocol.parseContentRange(null))
        assertNull(CastRelayProtocol.parseContentRange(""))
        assertNull(CastRelayProtocol.parseContentRange("bytes */5000")) // unsatisfied-range form
        assertNull(CastRelayProtocol.parseContentRange("items 0-1/2"))
    }

    // --- continuation math ---

    @Test
    fun `continuation after a plain 200 resumes as an open-ended range at the copied offset`() {
        assertEquals("bytes=50000-", CastRelayProtocol.continuationRangeHeader(null, 50_000))
        assertEquals(50_000L, CastRelayProtocol.continuationOffset(null, 50_000))
    }

    @Test
    fun `continuation after a 206 keeps the original span end and offsets the start`() {
        val range = CastRelayProtocol.ContentRange(1000, 9999, 20_000)
        assertEquals("bytes=1300-9999", CastRelayProtocol.continuationRangeHeader(range, 300))
        assertEquals(1300L, CastRelayProtocol.continuationOffset(range, 300))
    }

    // --- newToken ---

    @Test
    fun `tokens are 32 lowercase hex chars and unique`() {
        val random = SecureRandom()
        val a = CastRelayProtocol.newToken(random)
        val b = CastRelayProtocol.newToken(random)
        assertEquals(32, a.length)
        assertEquals(a, CastRelayProtocol.tokenFromPath("/stream/$a")) // round-trips through the path rules
        assertNotEquals(a, b)
    }

    // --- formatHostForUrl ---

    @Test
    fun `formats IPv4 and routable IPv6 hosts`() {
        assertEquals("192.168.0.7", CastRelayProtocol.formatHostForUrl(InetAddress.getByName("192.168.0.7")))
        // The JVM renders the expanded (uncompressed) form; either is a valid bracketed URL host.
        assertEquals("[2001:db8:0:0:0:0:0:1]", CastRelayProtocol.formatHostForUrl(InetAddress.getByName("2001:db8::1")))
    }

    @Test
    fun `rejects the wildcard and link-local IPv6 as URL hosts`() {
        // Wildcard = the route probe failed to pick a source address — no URL can be built from it.
        assertNull(CastRelayProtocol.formatHostForUrl(InetAddress.getByName("0.0.0.0")))
        assertNull(CastRelayProtocol.formatHostForUrl(InetAddress.getByName("::")))
        // Link-local needs a scope id that only means something on the phone.
        assertNull(CastRelayProtocol.formatHostForUrl(InetAddress.getByName("fe80::1")))
    }
}
