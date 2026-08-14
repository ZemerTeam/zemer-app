package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the shared-cpn contract (handoff: emulate-youtube-music-stream / CDN-cpn correlation): the
 * media request and the beacon session must use ONE cpn per listen, and a released listen must mint a
 * fresh cpn on its next play (fresh-cpn-per-play, so view counts keep incrementing).
 */
class PlaybackNonceRegistryTest {

    private fun counting(): Pair<PlaybackNonceRegistry, AtomicInteger> {
        val n = AtomicInteger(0)
        return PlaybackNonceRegistry { "cpn${n.incrementAndGet()}" } to n
    }

    @Test
    fun `same id returns one cpn until released - media and beacon share it`() {
        val (reg, _) = counting()
        val first = reg.getOrCreate("vid")

        assertEquals(first, reg.getOrCreate("vid"))
        assertEquals(first, reg.getOrCreate("vid"))
    }

    @Test
    fun `release rotates the cpn for the next play of the same id`() {
        val (reg, _) = counting()
        val first = reg.getOrCreate("vid")
        reg.release("vid")
        val second = reg.getOrCreate("vid")

        assertNotEquals(first, second)
    }

    @Test
    fun `different ids get different cpns`() {
        val (reg, _) = counting()

        assertNotEquals(reg.getOrCreate("a"), reg.getOrCreate("b"))
    }

    @Test
    fun `cpn is minted at most once per live id`() {
        val (reg, count) = counting()
        repeat(5) { reg.getOrCreate("vid") }

        assertEquals(1, count.get())
    }

    @Test
    fun `real generator produces a 16-char cpn`() {
        val reg = PlaybackNonceRegistry()

        assertEquals(16, reg.getOrCreate("vid").length)
    }

    // --- appendCpn: the pure URL stamp ---

    @Test
    fun `appendCpn uses ampersand on a url that already has a query`() {
        assertEquals(
            "https://r1.googlevideo.com/videoplayback?itag=251&pot=X&cpn=ABC",
            PlaybackNonceRegistry.appendCpn("https://r1.googlevideo.com/videoplayback?itag=251&pot=X", "ABC"),
        )
    }

    @Test
    fun `appendCpn uses question mark on a bare url`() {
        assertEquals("https://host/path?cpn=ABC", PlaybackNonceRegistry.appendCpn("https://host/path", "ABC"))
    }
}
