package com.jtech.zemer.latestreleases

import com.jtech.zemer.models.MediaMetadata
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

    private fun playingTrack(id: String, albumId: String? = null) = MediaMetadata(
        id = id,
        title = "Now Playing",
        artists = listOf(MediaMetadata.Artist(id = "UC1", name = "Artist")),
        duration = 0,
        album = albumId?.let { MediaMetadata.Album(id = it, title = "Album") },
    )

    @Test
    fun `a single is active when its videoId is the current track (not via album id)`() {
        val single = release(trackCount = 1, sampleVideoId = "vid")
        // The single plays as a videoId with no album bound, so matching must be on the track id.
        assertTrue(single.isNowPlaying(playingTrack(id = "vid")))
        assertFalse(single.isNowPlaying(playingTrack(id = "other")))
        assertFalse(single.isNowPlaying(null))
    }

    @Test
    fun `an album release is active when a track from that album (browseId) is playing`() {
        val album = release(trackCount = 5)
        assertTrue(album.isNowPlaying(playingTrack(id = "anySong", albumId = "MPRE1")))
        assertFalse(album.isNowPlaying(playingTrack(id = "anySong", albumId = "MPRE_other")))
        assertFalse(album.isNowPlaying(playingTrack(id = "anySong", albumId = null)))
        assertFalse(album.isNowPlaying(null))
    }

    @Test
    fun `sampleMediaMetadata builds a track for any release with a videoId (single or album)`() {
        assertEquals("vid", release(trackCount = 1).sampleMediaMetadata()?.id)   // single
        assertEquals("vid", release(trackCount = 5).sampleMediaMetadata()?.id)   // multi-track album
        assertNull(release(trackCount = 1, sampleVideoId = null).sampleMediaMetadata())
        assertNull(release(trackCount = 5, sampleVideoId = "").sampleMediaMetadata())
    }

    @Test
    fun `sampleTracks keeps the sample of every release that has a videoId, preserving order`() {
        val list = listOf(
            release(trackCount = 1, sampleVideoId = "a"),
            release(trackCount = 5, sampleVideoId = "b"),    // an album still contributes its sample
            release(trackCount = 1, sampleVideoId = null),   // dropped — nothing to play
            release(trackCount = null, sampleVideoId = ""),  // dropped — nothing to play
        )
        assertEquals(listOf("a", "b"), list.sampleTracks().map { it.id })
    }

    @Test
    fun `the metadata a single actually plays makes its own card active`() {
        // Guards the real bug: playableSingle() carries no album, so an album-id check never matched.
        val single = release(trackCount = 1, sampleVideoId = "vid")
        assertTrue(single.isNowPlaying(single.playableSingle()))
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
