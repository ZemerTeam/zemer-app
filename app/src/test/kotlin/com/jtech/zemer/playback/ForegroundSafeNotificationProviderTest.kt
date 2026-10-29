package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A refused foreground start from media3's late-artwork notification update must never crash the app. */
class ForegroundSafeNotificationProviderTest {
    private class Refused : IllegalStateException("startForegroundService() not allowed")

    private val isRefusal: (Throwable) -> Boolean = { it is Refused }

    @Test
    fun `a refused foreground start is dropped`() {
        ignoringForegroundRefusal(isRefusal) { throw Refused() }
    }

    @Test
    fun `any other failure still propagates`() {
        val other = IllegalStateException("something else")
        val thrown = assertThrows(IllegalStateException::class.java) {
            ignoringForegroundRefusal(isRefusal) { throw other }
        }
        assertEquals(other, thrown)
    }

    @Test
    fun `the update runs when nothing fails`() {
        var ran = false
        ignoringForegroundRefusal(isRefusal) { ran = true }
        assertTrue(ran)
    }
}
