package com.metrolist.lrclib

import com.metrolist.lrclib.models.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: a same-length song by another artist ("Koi Koi Koi", a Japanese track) was served for Baruch Levine. */
class LrcLibIdentityTest {
    @Test
    fun `duration alone never matches`() {
        assertFalse(LrcLib.identityMatches("こいこいこい", "Some J-Pop Artist", "Koi Koi Koi", "Baruch Levine", 200.0, 200))
        assertFalse(LrcLib.identityMatches("Koi Koi Koi", "Some J-Pop Artist", "Koi Koi Koi", "Baruch Levine", 200.0, 200))
    }

    @Test
    fun `title plus artist plus duration match`() {
        assertTrue(LrcLib.identityMatches("Koi Koi Koi", "Baruch Levine", "Koi Koi Koi (feat. X)", "Baruch Levine", 201.0, 200))
        assertFalse("duration off by 30s", LrcLib.identityMatches("Koi Koi Koi", "Baruch Levine", "Koi Koi Koi", "Baruch Levine", 230.0, 200))
        assertTrue(LrcLib.identityMatches("Vezakeini", "Baruch Levine", "Vezakeini", "Baruch Levine & Shira Choir", 210.0, -1))
    }

    private fun track(duration: Double, synced: String?, plain: String?) = Track(1, "Koi Koi Koi", "Baruch Levine", duration, plain, synced)

    /** A track inside the 3 s identity gate but outside the 1 s sync gate never serves its synced body. */
    @Test
    fun `drifting candidate without plain text yields nothing, not its synced body`() {
        assertNull(LrcLib.pickBody(listOf(track(203.0, "[00:01.00] drifting", null)), 200))
    }

    @Test
    fun `drifting candidate serves plain text only`() {
        assertEquals("plain words", LrcLib.pickBody(listOf(track(203.0, "[00:01.00] drifting", "plain words")), 200))
    }

    @Test
    fun `same recording serves synced text`() {
        assertEquals("[00:01.00] synced", LrcLib.pickBody(listOf(track(201.0, "[00:01.00] synced", "plain")), 200))
        assertEquals("unknown duration accepts synced", "[00:01.00] synced", LrcLib.pickBody(listOf(track(230.0, "[00:01.00] synced", "plain")), -1))
    }

    @Test
    fun `blank synced body does not beat available plain text`() {
        assertEquals("plain words", LrcLib.pickBody(listOf(track(200.0, "", "plain words")), 200))
        assertFalse(LrcLib.syncable(track(200.0, "  ", "plain words"), 200))
    }

    @Test
    fun `a later syncable candidate wins over an earlier drifting one`() {
        val body = LrcLib.pickBody(listOf(track(203.0, "[00:01.00] drifting", "plain a"), track(200.0, "[00:01.00] exact", "plain b")), 200)
        assertEquals("[00:01.00] exact", body)
    }
}
