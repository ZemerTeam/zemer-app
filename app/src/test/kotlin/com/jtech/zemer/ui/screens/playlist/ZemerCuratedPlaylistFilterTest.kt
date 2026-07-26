package com.jtech.zemer.ui.screens.playlist

import com.jtech.zemer.latestreleases.LatestReleaseFilter
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the All/Albums/Songs chip logic on the curated-playlist detail screen (the same
 * [LatestReleaseFilter] chips as the Latest Releases screen): ALBUMS = tracks that entered the
 * playlist via a curated-album expansion, SONGS = direct picks, both keeping curated order. The
 * filtered list is shared by the rows, Play AND Shuffle.
 */
class ZemerCuratedPlaylistFilterTest {

    private fun song(id: String) = SongItem(
        id = id,
        title = id,
        artists = listOf(Artist(name = "A", id = null)),
        album = null,
        duration = null,
        thumbnail = "t",
    )

    private val songs = listOf(song("pick1"), song("alb1"), song("pick2"), song("alb2"))
    private val albumTrackIds = setOf("alb1", "alb2")

    @Test
    fun `ALL keeps the untouched curated order`() {
        assertEquals(songs, filterCuratedTracks(songs, albumTrackIds, LatestReleaseFilter.ALL))
    }

    @Test
    fun `ALBUMS keeps only album-expanded tracks, SONGS only direct picks, both in curated order`() {
        assertEquals(
            listOf("alb1", "alb2"),
            filterCuratedTracks(songs, albumTrackIds, LatestReleaseFilter.ALBUMS).map { it.id },
        )
        assertEquals(
            listOf("pick1", "pick2"),
            filterCuratedTracks(songs, albumTrackIds, LatestReleaseFilter.SONGS).map { it.id },
        )
    }

    @Test
    fun `old server without fromAlbum - empty album set - ALBUMS empty, SONGS is everything`() {
        assertEquals(emptyList<SongItem>(), filterCuratedTracks(songs, emptySet(), LatestReleaseFilter.ALBUMS))
        assertEquals(songs, filterCuratedTracks(songs, emptySet(), LatestReleaseFilter.SONGS))
    }

    @Test
    fun `chips show only when the playlist has albums`() {
        // No albums -> ALL == SONGS and ALBUMS is empty, so the chip row is hidden (plain track list).
        assertFalse(curatedChipsVisible(albumCount = 0))
        // Any albums -> the split is meaningful, show the chips.
        assertTrue(curatedChipsVisible(albumCount = 1))
        assertTrue(curatedChipsVisible(albumCount = 5))
    }

    @Test
    fun `empty-state gates on the SELECTED chip's content, never the raw ALL list`() {
        // ALBUMS chip renders album rows: empty albums -> empty state even with visible songs.
        assertTrue(isCuratedChipEmpty(LatestReleaseFilter.ALBUMS, albumCount = 0, visibleSongCount = 5))
        assertFalse(isCuratedChipEmpty(LatestReleaseFilter.ALBUMS, albumCount = 3, visibleSongCount = 0))
        // ALL/SONGS render the filtered tracks.
        assertTrue(isCuratedChipEmpty(LatestReleaseFilter.SONGS, albumCount = 3, visibleSongCount = 0))
        assertFalse(isCuratedChipEmpty(LatestReleaseFilter.ALL, albumCount = 0, visibleSongCount = 5))
        assertTrue(isCuratedChipEmpty(LatestReleaseFilter.ALL, albumCount = 0, visibleSongCount = 0))
    }
}
