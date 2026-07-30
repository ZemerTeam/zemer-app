package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Zemer nav-route builders + the search telemetry wire value. Both are contracts: the routes are
 * parsed by NavigationBuilder's argument declarations, and the `provider` value is what the tracking
 * server accepts ("zemer" — anything else is stored NULL), so a rename/typo here silently breaks
 * navigation or NULLs a dashboard dimension.
 */
class ZemerRoutesTest {

    private val album = AlbumItem(
        browseId = "MPRE1",
        playlistId = "OLAK1",
        title = "T",
        artists = null,
        year = null,
        thumbnail = "th",
    )

    @Test
    fun `playlists route through the server path`() {
        assertEquals("online_playlist/PL1?zemer=true", zemerPlaylistRoute("PL1"))
    }

    @Test
    fun `community flag adds community=true so plays tag community not playlist`() {
        assertEquals("online_playlist/PL1?zemer=true&community=true", zemerPlaylistRoute("PL1", community = true))
    }

    @Test
    fun `albums route through the server path with the card's playlistId`() {
        assertEquals("album/MPRE1?zemer=true&playlistId=OLAK1", zemerAlbumRoute(album))
    }

    @Test
    fun `genre routes are the raw slugs (vocabulary is url-safe by contract)`() {
        assertEquals("genres", zemerGenresRoute())
        assertEquals("genre/nigunim", zemerGenreRoute("nigunim"))
        assertEquals("genre/shavuos-simchas-torah", zemerGenreRoute("shavuos-simchas-torah"))
    }

    @Test
    fun `the search telemetry provider wire value is pinned`() {
        assertEquals("zemer", com.jtech.zemer.viewmodels.SEARCH_TRACKED_PROVIDER)
    }
}
