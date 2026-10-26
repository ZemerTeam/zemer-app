package com.jtech.zemer.sync

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

class ReportRetryTest {
    @Test
    fun `unreachable, 429 and 5xx are queued for retry`() {
        assertTrue(IOException("down").isRetryableReportFailure())
        assertTrue(UnresolvedAddressException().isRetryableReportFailure())
        assertTrue(ContentServerException(429, "x").isRetryableReportFailure())
        assertTrue(ContentServerException(500, "x").isRetryableReportFailure())
        assertTrue(ContentServerException(503, "x", challenge = true).isRetryableReportFailure())
    }

    @Test
    fun `rejections and unreadable answers are never re-sent`() {
        assertFalse(ContentServerException(400, "x").isRetryableReportFailure())
        assertFalse(ContentServerException(403, "x").isRetryableReportFailure())
        assertFalse(ContentServerException(413, "x").isRetryableReportFailure())
        // a 201 whose body we could not read: the report may already be stored
        assertFalse(SerializationException("bad body").isRetryableReportFailure())
    }

    @Test
    fun `write backoff doubles from 5s and caps at 5 minutes`() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L), (0..7).map(::writeRetryDelayMs))
        assertEquals(300_000L, writeRetryDelayMs(Int.MAX_VALUE))
    }

    @Test
    fun `Retry-After wins when longer than the backoff, capped at 10 minutes`() {
        assertEquals(5_000L, retryDelayMs(5_000L, null))
        assertEquals(5_000L, retryDelayMs(5_000L, IOException("down")))
        assertEquals(5_000L, retryDelayMs(5_000L, ContentServerException(429, "x", retryAfterSec = 2)))
        assertEquals(120_000L, retryDelayMs(5_000L, ContentServerException(429, "x", retryAfterSec = 120)))
        assertEquals(600_000L, retryDelayMs(5_000L, ContentServerException(429, "x", retryAfterSec = 86_400)))
        assertEquals(5_000L, retryDelayMs(5_000L, ContentServerException(429, "x", retryAfterSec = -5)))
    }
}
