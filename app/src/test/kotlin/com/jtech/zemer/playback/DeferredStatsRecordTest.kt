package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deferred offline-stats record: a JSONL round-trip that survives process death, a corrupt line
 * that degrades to null (never crashes the flush), and the staleness cap.
 */
class DeferredStatsRecordTest {

    private val sample = DeferredStatsRecord(
        videoId = "abc123",
        st = "0.0,45.0",
        et = "30.0,90.0",
        cmt = "90.0",
        rt = "75.0",
        endedAtMs = 1_000_000L,
    )

    @Test
    fun `encode then decode round-trips every field`() {
        assertEquals(sample, DeferredStatsRecord.decode(sample.encode()))
    }

    @Test
    fun `encoded record is a single JSON line`() {
        val line = sample.encode()
        assertTrue(line.startsWith("{"))
        assertTrue(line.endsWith("}"))
        assertFalse("a queue line must not contain a newline", line.contains("\n"))
    }

    @Test
    fun `a corrupt or partial line decodes to null, never throws`() {
        assertNull(DeferredStatsRecord.decode("{not json"))
        assertNull(DeferredStatsRecord.decode(""))
        assertNull(DeferredStatsRecord.decode("""{"videoId":"x"}""")) // missing required fields
    }

    @Test
    fun `isStale is true only past the max age`() {
        val maxAge = 7L * 24 * 60 * 60 * 1000
        assertFalse(sample.isStale(nowMs = sample.endedAtMs + maxAge, maxAgeMs = maxAge))
        assertTrue(sample.isStale(nowMs = sample.endedAtMs + maxAge + 1, maxAgeMs = maxAge))
        assertFalse("a freshly ended listen is never stale", sample.isStale(nowMs = sample.endedAtMs, maxAgeMs = maxAge))
    }
}
