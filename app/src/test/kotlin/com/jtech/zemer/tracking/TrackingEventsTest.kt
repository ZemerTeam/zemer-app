package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the exact wire contract of the five tracking events (spec §3) and the batch body (§2) —
 * the server drops unknown types and skips malformed rows, so field names and types are pinned
 * byte-for-byte here.
 */
class TrackingEventsTest {

    @Test
    fun `open event matches the spec exactly`() {
        assertEquals("""{"type":"open","t":1720000000000}""", TrackingEvents.open(1720000000000L).toString())
    }

    @Test
    fun `search event matches the spec exactly - zero results sent faithfully, provider omitted when absent`() {
        assertEquals(
            """{"type":"search","t":1,"q":"shwekey","results":0}""",
            TrackingEvents.search(1, "shwekey", 0).toString(),
        )
    }

    @Test
    fun `search event carries the provider extension when known - exactly zemer or youtube`() {
        assertEquals(
            """{"type":"search","t":1,"q":"acapella","results":49,"provider":"zemer"}""",
            TrackingEvents.search(1, "acapella", 49, "zemer").toString(),
        )
        assertEquals(
            """{"type":"search","t":1,"q":"acapella","results":0,"provider":"youtube"}""",
            TrackingEvents.search(1, "acapella", 0, "youtube").toString(),
        )
    }

    @Test
    fun `click event matches the spec exactly`() {
        assertEquals(
            """{"type":"click","t":1,"q":"shwekey","id":"zVRL5bTbDwk","kind":"song","rank":0}""",
            TrackingEvents.click(1, "shwekey", "zVRL5bTbDwk", "song", 0).toString(),
        )
    }

    @Test
    fun `play event - dur and the client-player extensions are omitted when unknown`() {
        assertEquals(
            """{"type":"play","t":1,"videoId":"v","secs":5,"source":"other"}""",
            TrackingEvents.play(1, "v", 5, dur = null, source = "other").toString(),
        )
        assertEquals(
            """{"type":"play","t":1,"videoId":"v","secs":143,"dur":214,"source":"zemer:acapella","client":"WEB_REMIX","player":"6009b507"}""",
            TrackingEvents.play(1, "v", 143, 214, "zemer:acapella", "WEB_REMIX", "6009b507").toString(),
        )
    }

    @Test
    fun `action event matches the spec exactly`() {
        assertEquals(
            """{"type":"action","t":1,"kind":"favorite","id":"v"}""",
            TrackingEvents.action(1, TrackingActionKind.FAVORITE, "v").toString(),
        )
    }

    @Test
    fun `impression event matches the spec exactly - surface omitted when it is not a valid slug`() {
        assertEquals(
            """{"type":"impression","t":1,"ids":["dQw4w9WgXcQ","abc123def45"],"surface":"home:quick-picks"}""",
            TrackingEvents.impression(1, listOf("dQw4w9WgXcQ", "abc123def45"), "home:quick-picks").toString(),
        )
        // Absent or non-conforming surfaces are stored as NULL server-side; don't spend bytes on them.
        assertEquals(
            """{"type":"impression","t":1,"ids":["dQw4w9WgXcQ"]}""",
            TrackingEvents.impression(1, listOf("dQw4w9WgXcQ"), null).toString(),
        )
        assertEquals(
            """{"type":"impression","t":1,"ids":["dQw4w9WgXcQ"]}""",
            TrackingEvents.impression(1, listOf("dQw4w9WgXcQ"), "home:quick picks").toString(),
        )
    }

    @Test
    fun `impression chunks drop non-videoIds and repeats, and split at 50 so the server never truncates`() {
        // Album/playlist/channel ids and junk share these rows — the server drops them anyway.
        assertEquals(
            listOf(listOf("dQw4w9WgXcQ", "abc123def45")),
            impressionChunks(listOf("dQw4w9WgXcQ", "MPREb_abc123", "", "abc123def45", "short")),
        )
        // A repeat within one row is one impression.
        assertEquals(
            listOf(listOf("dQw4w9WgXcQ")),
            impressionChunks(listOf("dQw4w9WgXcQ", "dQw4w9WgXcQ")),
        )
        // 120 ids → 50/50/20, never one truncated-to-the-head event.
        val many = (0 until 120).map { "id%09d".format(it) }
        assertEquals(listOf(50, 50, 20), impressionChunks(many).map { it.size })
        assertEquals(many, impressionChunks(many).flatten())
        assertEquals(emptyList<List<String>>(), impressionChunks(emptyList()))
    }

    @Test
    fun `a POST is capped at the server's impression-row limit, and the cap keeps a queue PREFIX`() {
        fun impression(n: Int) = TrackingEvents.impression(1, List(n) { "id%09d".format(it) }, "s").toString()
        val play = """{"type":"play","t":1,"videoId":"v","secs":5,"source":"other"}"""

        // 12 full events = 600 rows; the drain is event-counted (100) so nothing else would stop it.
        val batch = List(12) { impression(50) }
        val capped = capImpressionRows(batch)
        assertEquals(10, capped.size) // 500 rows exactly
        assertEquals(batch.take(10), capped) // a PREFIX — removeBatch aligns against the queue head
        assertEquals(500, capped.sumOf { impressionRowCount(it) })

        // Other event types are free: they cost no impression rows and must not be cut short.
        assertEquals(List(50) { play }, capImpressionRows(List(50) { play }))
        assertEquals(0, impressionRowCount(play))

        // Under the limit, everything travels.
        assertEquals(batch.take(3), capImpressionRows(batch.take(3)))

        // A single event larger than the whole cap still goes out rather than stalling the queue.
        assertEquals(1, capImpressionRows(listOf(impression(50)), max = 10).size)
    }

    @Test
    fun `surface slugs stay inside the alphabet the server enforces`() {
        assertEquals(true, isTrackingSurface(TrackingSurface.SEARCH))
        assertEquals(true, isTrackingSurface(TrackingSurface.home("forgotten-favorites")))
        assertEquals(true, isTrackingSurface(TrackingSurface.artist("UCabc_123.x")))
        assertEquals(false, isTrackingSurface(""))
        assertEquals(false, isTrackingSurface("home:quick picks"))
        assertEquals(false, isTrackingSurface("x".repeat(81)))
    }

    @Test
    fun `upload counters are parsed when present and never throw on anything else`() {
        assertEquals(
            TrackingUploadCounters(accepted = 412, impressionsDropped = 50),
            parseTrackingUploadCounters("""{"ok":true,"accepted":412,"impressionsDropped":50}"""),
        )
        // Absent impressionsDropped is the normal case: nothing was truncated.
        assertEquals(
            TrackingUploadCounters(accepted = 501, impressionsDropped = 0),
            parseTrackingUploadCounters("""{"ok":true,"accepted":501,"debug":true}"""),
        )
        // Diagnostics only — a proxy error page or an empty body must never fail an upload.
        assertEquals(
            TrackingUploadCounters(accepted = null, impressionsDropped = 0),
            parseTrackingUploadCounters("<html>502</html>"),
        )
        assertEquals(
            TrackingUploadCounters(accepted = null, impressionsDropped = 0),
            parseTrackingUploadCounters(""),
        )
    }

    @Test
    fun `batch body wraps device, app_ver, debug flag and raw event lines - strings JSON-escaped`() {
        val body = trackingBatchBody(
            device = "08e84a6b-9389-49fe-8c80-098322f7490a",
            appVer = "34\"x",
            debug = false,
            eventLines = listOf("""{"type":"open","t":1}""", """{"type":"open","t":2}"""),
        )
        assertEquals(
            """{"device":"08e84a6b-9389-49fe-8c80-098322f7490a","app_ver":"34\"x","debug":false,"events":[{"type":"open","t":1},{"type":"open","t":2}]}""",
            body,
        )
        // Debug builds send debug:true — the server ACKs identically but stores nothing.
        assertEquals(
            """{"device":"d","app_ver":"34","debug":true,"events":[]}""",
            trackingBatchBody("d", "34", debug = true, eventLines = emptyList()),
        )
    }

    @Test
    fun `retry ladder - 30s then 2min then 10min, rate-limit floor 2min`() {
        assertEquals(30_000L, trackingRetryDelayMs(1, rateLimited = false))
        assertEquals(120_000L, trackingRetryDelayMs(2, rateLimited = false))
        assertEquals(600_000L, trackingRetryDelayMs(3, rateLimited = false))
        assertEquals(600_000L, trackingRetryDelayMs(9, rateLimited = false))
        // 429 waits at least 2 minutes even on the first failure.
        assertEquals(120_000L, trackingRetryDelayMs(1, rateLimited = true))
        assertEquals(600_000L, trackingRetryDelayMs(3, rateLimited = true))
    }

    @Test
    fun `device id must be a canonical UUID - the server 400s anything else`() {
        assertEquals(true, isCanonicalUuid("08e84a6b-9389-49fe-8c80-098322f7490a"))
        assertEquals(false, isCanonicalUuid(""))
        assertEquals(false, isCanonicalUuid("not-a-uuid"))
        assertEquals(false, isCanonicalUuid("08E84A6B-9389-49FE-8C80-098322F7490A".lowercase() + "x"))
        // Java's UUID.randomUUID().toString() is lowercase canonical — must always pass.
        assertEquals(true, isCanonicalUuid(java.util.UUID.randomUUID().toString()))
    }
}
