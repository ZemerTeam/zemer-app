package com.jtech.zemer.latestreleases

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Latest Releases "See all" filter: ALBUMS keeps multi-track releases, SONGS keeps playable
 * singles, ALL keeps everything — and feed order is preserved. Pure JVM.
 */
class LatestReleaseFilterTest {
    private fun release(id: String, trackCount: Int?, sampleVideoId: String? = "vid") = LatestRelease(
        artistId = "UC1",
        artistName = "Artist",
        title = id,
        browseId = id,
        playlistId = "OLAK_$id",
        thumbnail = "thumb",
        year = 2026,
        uploadDate = "2026-06-17T00:00:00-07:00",
        trackCount = trackCount,
        sampleVideoId = sampleVideoId,
    )

    private val single = release("single", trackCount = 1)
    private val album = release("album", trackCount = 8)
    private val unknown = release("unknown", trackCount = null) // older cached feed -> treated as album
    private val all = listOf(single, album, unknown)

    @Test fun all_keepsEverythingInOrder() {
        assertEquals(all, all.applyFilter(LatestReleaseFilter.ALL))
    }

    @Test fun albums_keepsMultiTrackAndUnknown() {
        assertEquals(listOf(album, unknown), all.applyFilter(LatestReleaseFilter.ALBUMS))
    }

    @Test fun songs_keepsOnlyPlayableSingles() {
        assertEquals(listOf(single), all.applyFilter(LatestReleaseFilter.SONGS))
    }

    @Test fun songs_excludesOneTrackReleaseWithNoVideoId() {
        val noVid = release("noVid", trackCount = 1, sampleVideoId = null)
        assertEquals(emptyList<LatestRelease>(), listOf(noVid).applyFilter(LatestReleaseFilter.SONGS))
        assertEquals(listOf(noVid), listOf(noVid).applyFilter(LatestReleaseFilter.ALBUMS))
    }
}
