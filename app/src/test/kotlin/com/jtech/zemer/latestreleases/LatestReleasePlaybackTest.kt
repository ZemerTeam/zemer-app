package com.jtech.zemer.latestreleases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the single-vs-album tap decision ([LatestRelease.playableSingle]): a one-track release plays as
 * a single, anything else (a real album, or an older cached feed with no track count / no videoId)
 * falls back to opening the album page. Pure JVM — no Android runtime or player needed.
 */
class LatestReleasePlaybackTest {
    private fun release(trackCount: Int?, sampleVideoId: String? = "vid") = LatestRelease(
        artistId = "UC1",
        artistName = "Artist",
        title = "Title",
        browseId = "MPRE1",
        playlistId = "OLAK1",
        thumbnail = "thumb",
        year = 2026,
        uploadDate = "2026-06-17T00:00:00-07:00",
        trackCount = trackCount,
        sampleVideoId = sampleVideoId,
    )

    @Test
    fun `a one-track release is a playable single carrying its track metadata`() {
        val single = release(trackCount = 1).playableSingle()
        assertEquals("vid", single?.id)
        assertEquals("Title", single?.title)
        assertEquals("Artist", single?.artists?.single()?.name)
        assertEquals("UC1", single?.artists?.single()?.id)
        assertEquals("thumb", single?.thumbnailUrl)
    }

    @Test
    fun `a multi-track release is not a single (opens the album)`() {
        assertNull(release(trackCount = 5).playableSingle())
    }

    @Test
    fun `a single with no videoId cannot be played (opens the album)`() {
        assertNull(release(trackCount = 1, sampleVideoId = null).playableSingle())
        assertNull(release(trackCount = 1, sampleVideoId = "").playableSingle())
    }

    @Test
    fun `an older feed entry with no track count opens the album`() {
        assertNull(release(trackCount = null).playableSingle())
    }

    @Test
    fun `isPlayableSingle drives the centred play icon and agrees with what plays on tap`() {
        assertTrue(release(trackCount = 1).isPlayableSingle())
        assertFalse(release(trackCount = 5).isPlayableSingle())
        assertFalse(release(trackCount = 1, sampleVideoId = null).isPlayableSingle())
        assertFalse(release(trackCount = 1, sampleVideoId = "").isPlayableSingle())
        assertFalse(release(trackCount = null).isPlayableSingle())
        // the icon must never promise playback the tap won't deliver
        for (tc in listOf(null, 1, 2)) {
            val r = release(trackCount = tc)
            assertEquals(r.isPlayableSingle(), r.playableSingle() != null)
        }
    }
}
