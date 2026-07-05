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
    fun `search event matches the spec exactly - zero results sent faithfully`() {
        assertEquals(
            """{"type":"search","t":1,"q":"shwekey","results":0}""",
            TrackingEvents.search(1, "shwekey", 0).toString(),
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
