package com.jtech.zemer.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TokenRetryTest {
    private val refreshes = mutableListOf<Boolean>()
    private val token: suspend (Boolean) -> String = { force -> refreshes += force; if (force) "fresh" else "cached" }

    @Test
    fun `success uses the cached token only`() = runBlocking {
        assertEquals("ok:cached", withTokenRetry(token) { "ok:$it" })
        assertEquals(listOf(false), refreshes)
    }

    @Test
    fun `a 401 retries once with a refreshed token`() = runBlocking {
        val result = withTokenRetry(token) { t -> if (t == "cached") throw ContentServerException(401, "expired") else "ok:$t" }
        assertEquals("ok:fresh", result)
        assertEquals(listOf(false, true), refreshes)
    }

    @Test
    fun `a second 401 propagates, no loop`() = runBlocking {
        try {
            withTokenRetry<Unit>(token) { throw ContentServerException(401, "bad") }
            fail("expected 401")
        } catch (e: ContentServerException) {
            assertEquals(401, e.status)
        }
        assertEquals(listOf(false, true), refreshes)
    }

    @Test
    fun `other statuses are not retried`() = runBlocking {
        for (status in listOf(400, 404, 429, 503)) {
            refreshes.clear()
            try {
                withTokenRetry<Unit>(token) { throw ContentServerException(status, "x") }
                fail("expected $status")
            } catch (e: ContentServerException) {
                assertEquals(status, e.status)
            }
            assertEquals(listOf(false), refreshes)
        }
    }
}
