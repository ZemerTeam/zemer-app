package com.jtech.zemer.recognition

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "undeniable" whitelist invariant: [RecognitionMatchSelector.select] only ever returns an
 * element of the candidate list it was given (or null). The candidate list is the output of
 * `filterWhitelisted`, so a recognized-but-not-whitelisted song can never be surfaced — the Shazam
 * metadata is used only to rank already-filtered results.
 */
class RecognitionMatchSelectorTest {

    private fun song(id: String, title: String, artist: String) =
        SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artist, id = "UC_$artist")),
            thumbnail = "",
        )

    @Test
    fun `returns the matching candidate from the filtered list`() {
        val match = song("v1", "Daddy", "Mordechai Shapiro")
        val candidates = listOf(
            song("v0", "Unrelated Song", "Another Artist"),
            match,
        )
        val result = RecognitionMatchSelector.select("Daddy", "Mordechai Shapiro", candidates)
        assertSame(match, result)
        assertTrue(result in candidates)
    }

    @Test
    fun `returns null when nothing whitelisted corresponds`() {
        val candidates = listOf(song("v0", "Totally Different", "Another Artist"))
        assertNull(RecognitionMatchSelector.select("Daddy", "Mordechai Shapiro", candidates))
    }

    @Test
    fun `empty candidate list returns null`() {
        assertNull(RecognitionMatchSelector.select("Daddy", "Mordechai Shapiro", emptyList()))
    }

    @Test
    fun `any returned result is always a member of the input list`() {
        // Even when the recognized metadata is "noise", the result must come from the filtered list.
        val candidates = listOf(
            song("v0", "Adon Olam", "Whitelisted A"),
            song("v1", "Daddy", "Whitelisted B"),
        )
        val result = RecognitionMatchSelector.select("Daddy", "Whitelisted B", candidates)
        if (result != null) assertTrue(result in candidates)
    }
}
