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

    // --- bounded WITHOUT wiping the live cpn (the wholesale-clear regression) ---

    @Test
    fun `a pinned id keeps its cpn even after far more than MAX_ENTRIES strays accrue`() {
        val (reg, _) = counting()
        val live = reg.getOrCreate("live")
        reg.pin("live")

        // Pour in strays well past the cap — the old wholesale clear() would have wiped "live".
        repeat(PlaybackNonceRegistry.MAX_ENTRIES * 3) { reg.getOrCreate("stray$it") }

        assertEquals("the live listen's cpn must survive eviction", live, reg.getOrCreate("live"))
    }

    @Test
    fun `the most-recently-used id survives eviction even without a pin`() {
        val (reg, _) = counting()
        val recent = reg.getOrCreate("recent")

        // Every insert also touches "recent" (getOrCreate = access), keeping it youngest, so the LRU
        // evicts the strays, never the entry we keep using.
        repeat(PlaybackNonceRegistry.MAX_ENTRIES * 3) {
            reg.getOrCreate("stray$it")
            reg.getOrCreate("recent")
        }

        assertEquals(recent, reg.getOrCreate("recent"))
    }

    @Test
    fun `an old untouched stray is eventually evicted past the cap`() {
        val (reg, _) = counting()
        val old = reg.getOrCreate("old") // never touched again → becomes least-recently-used

        repeat(PlaybackNonceRegistry.MAX_ENTRIES * 2) { reg.getOrCreate("stray$it") }

        assertNotEquals("an unpinned, untouched stray should have been evicted", old, reg.getOrCreate("old"))
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
