package com.jtech.zemer.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * The offline-fallback routing policy ([serverOrOffline]): server-first, fall back to the on-device
 * snapshot ONLY when the server is unreachable (an [IOException]) and the snapshot has an answer.
 */
class ZemerSearchRoutingTest {

    @Test
    fun `server result is used when the server succeeds`() = runBlocking {
        var offlineCalled = false
        val r = serverOrOffline(server = { "server" }, offline = { offlineCalled = true; "offline" })
        assertEquals("server", r)
        assertFalse("offline must not be consulted when the server answers", offlineCalled)
    }

    @Test
    fun `falls back to the snapshot when the server is unreachable`() = runBlocking {
        val r = serverOrOffline<String>(server = { throw IOException("unreachable") }, offline = { "offline" })
        assertEquals("offline", r)
    }

    @Test
    fun `rethrows the network error when there is no snapshot`() {
        val e = assertThrows(IOException::class.java) {
            runBlocking { serverOrOffline<String>(server = { throw IOException("unreachable") }, offline = { null }) }
        }
        assertEquals("unreachable", e.message)
    }

    @Test
    fun `a non-network failure is never masked by the fallback`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { serverOrOffline<String>(server = { throw IllegalStateException("bug") }, offline = { "offline" }) }
        }
    }

    @Test
    fun `a legitimate null (e-g- a 404) is returned as-is, offline never consulted`() = runBlocking {
        var offlineCalled = false
        val r = serverOrOffline<String?>(server = { null }, offline = { offlineCalled = true; "x" })
        assertEquals(null, r)
        assertFalse(offlineCalled)
    }
}
