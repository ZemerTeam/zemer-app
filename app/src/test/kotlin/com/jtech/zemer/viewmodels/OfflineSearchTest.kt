package com.jtech.zemer.viewmodels

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline-mode search assembly (manual offline mode, #366): categorical sections (a video
 * download never appears in two sections) and the per-chip filter selection.
 */
class OfflineSearchTest {

    private fun song(id: String, isVideo: Boolean = false) = SongItem(
        id = id,
        title = "title-$id",
        artists = emptyList(),
        thumbnail = "",
        isVideo = isVideo,
    )

    private fun artist(id: String) = ArtistItem(
        id = id,
        title = "artist-$id",
        thumbnail = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun album(id: String) = AlbumItem(
        browseId = id,
        playlistId = id,
        title = "album-$id",
        artists = emptyList(),
        thumbnail = "",
    )

    @Test
    fun `summary splits songs and videos categorically and skips empty sections`() {
        val sections = offlineSearchSummarySections(
            songs = listOf(song("a"), song("v1", isVideo = true), song("b")),
            artists = listOf(artist("ar")),
            albums = emptyList(),
            songsTitle = "Songs",
            videosTitle = "Videos",
            artistsTitle = "Artists",
            albumsTitle = "Albums",
        )
        assertEquals(listOf("Songs", "Videos", "Artists"), sections.map { it.title })
        assertEquals(listOf("a", "b"), sections[0].items.map { it.id })
        assertEquals(listOf("v1"), sections[1].items.map { it.id })
    }

    @Test
    fun `summary with no matches is empty`() {
        assertTrue(
            offlineSearchSummarySections(
                songs = emptyList(), artists = emptyList(), albums = emptyList(),
                songsTitle = "Songs", videosTitle = "Videos", artistsTitle = "Artists", albumsTitle = "Albums",
            ).isEmpty(),
        )
    }

    @Test
    fun `filter chips select the matching downloaded list`() {
        val songs = listOf(song("a"), song("v1", isVideo = true))
        val artists = listOf(artist("ar"))
        val albums = listOf(album("al"))
        assertEquals(listOf("a"), offlineFilteredItems(SearchFilter.FILTER_SONG, songs, artists, albums).map { it.id })
        assertEquals(listOf("v1"), offlineFilteredItems(SearchFilter.FILTER_VIDEO, songs, artists, albums).map { it.id })
        assertEquals(listOf("ar"), offlineFilteredItems(SearchFilter.FILTER_ARTIST, songs, artists, albums).map { it.id })
        assertEquals(listOf("al"), offlineFilteredItems(SearchFilter.FILTER_ALBUM, songs, artists, albums).map { it.id })
    }

    @Test
    fun `playlist filters have no offline source`() {
        val songs = listOf(song("a"))
        assertTrue(offlineFilteredItems(SearchFilter.FILTER_COMMUNITY_PLAYLIST, songs, emptyList(), emptyList()).isEmpty())
        assertTrue(offlineFilteredItems(SearchFilter.FILTER_FEATURED_PLAYLIST, songs, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `episode filter serves downloaded episodes and the summary gains an episodes section`() {
        val episodes = listOf(song("ep1"))
        assertEquals(
            listOf("ep1"),
            offlineFilteredItems(
                com.jtech.zemer.search.ZEMER_FILTER_EPISODE,
                songs = listOf(song("a")), artists = emptyList(), albums = emptyList(),
                episodes = episodes,
            ).map { it.id },
        )
        val sections = offlineSearchSummarySections(
            songs = emptyList(), artists = emptyList(), albums = emptyList(),
            songsTitle = "Songs", videosTitle = "Videos", artistsTitle = "Artists", albumsTitle = "Albums",
            episodes = episodes, episodesTitle = "Episodes",
        )
        assertEquals(listOf("Episodes"), sections.map { it.title })
    }
}
