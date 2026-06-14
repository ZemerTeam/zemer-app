package com.jtech.zemer.recognition

import com.jtech.zemer.recognition.RecognitionMatcher.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the recognition *accuracy* gate. The whitelist (safety) gate happens upstream, so these tests
 * focus on the one thing the matcher must get right: never claim a song matches unless BOTH the title
 * and the artist line up. A candidate that merely shares a title word with a different artist must be
 * rejected — that is the case that would otherwise surface an unrelated (but whitelisted) song.
 */
class RecognitionMatcherTest {

    @Test
    fun `exact title and artist matches`() {
        val candidates = listOf(Candidate("Daddy", listOf("Mordechai Shapiro")))
        assertEquals(0, RecognitionMatcher.bestMatchIndex("Daddy", "Mordechai Shapiro", candidates))
    }

    @Test
    fun `matching title but different artist is rejected`() {
        val candidates = listOf(Candidate("Daddy", listOf("Some Other Artist")))
        assertNull(RecognitionMatcher.bestMatchIndex("Daddy", "Mordechai Shapiro", candidates))
    }

    @Test
    fun `shared title word with a different artist does not match`() {
        // "Yerushalayim" appears in a whitelisted song by a different artist — must NOT be returned.
        val candidates = listOf(Candidate("Yerushalayim Shel Zahav", listOf("Yaakov Shwekey")))
        assertNull(RecognitionMatcher.bestMatchIndex("Yerushalayim", "Avraham Fried", candidates))
    }

    @Test
    fun `ignores diacritics, casing, feat and bracketed segments`() {
        val candidates = listOf(
            Candidate("Hashem Melech (Remix)", listOf("Gad Elbaz feat. Naftali Kalfa")),
        )
        assertEquals(
            0,
            RecognitionMatcher.bestMatchIndex("háshem MÉLECH", "Gad Elbaz", candidates),
        )
    }

    @Test
    fun `partial title still matches when the artist matches`() {
        // Recognized a shorter title; the candidate is a longer variant by the same artist.
        val candidates = listOf(Candidate("Hashem Melech 2.0", listOf("Gad Elbaz")))
        assertEquals(0, RecognitionMatcher.bestMatchIndex("Hashem Melech", "Gad Elbaz", candidates))
    }

    @Test
    fun `picks the tightest scoring candidate`() {
        val candidates = listOf(
            Candidate("Daddy Forever", listOf("Mordechai Shapiro")), // superset title, passes the gate
            Candidate("Daddy", listOf("Mordechai Shapiro")), // exact title, higher Jaccard
        )
        assertEquals(1, RecognitionMatcher.bestMatchIndex("Daddy", "Mordechai Shapiro", candidates))
    }

    @Test
    fun `no candidates returns null`() {
        assertNull(RecognitionMatcher.bestMatchIndex("Daddy", "Mordechai Shapiro", emptyList()))
    }

    @Test
    fun `blank recognized artist never matches`() {
        val candidates = listOf(Candidate("Daddy", listOf("Mordechai Shapiro")))
        assertNull(RecognitionMatcher.bestMatchIndex("Daddy", "", candidates))
    }
}
