package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the `online_playlist`/`album` route contracts the ViewModels' `zemer` nav args +
 * NavigationBuilder depend on: a Zemer-sourced playlist/album opens through the server path (the album
 * additionally carrying the search card's playlistId, which the server's album header doesn't return),
 * while a YouTube one keeps the plain InnerTube path. Changing a string here without updating the route
 * (or vice-versa) breaks opening.
 */
class SearchProviderTest {

    @Test
    fun `zemer playlists route through the server path`() {
        assertEquals("online_playlist/PL1?zemer=true", SearchProvider.ZEMER.onlinePlaylistRoute("PL1"))
    }

    @Test
    fun `youtube playlists keep the plain innertube path`() {
        assertEquals("online_playlist/PL1", SearchProvider.YOUTUBE.onlinePlaylistRoute("PL1"))
    }

    private val album = AlbumItem(
        browseId = "MPRE1",
        playlistId = "OLAK1",
        title = "Album",
        artists = null,
        thumbnail = "",
    )

    @Test
    fun `zemer albums route through the server path with the card's playlistId`() {
        assertEquals("album/MPRE1?zemer=true&playlistId=OLAK1", SearchProvider.ZEMER.onlineAlbumRoute(album))
    }

    @Test
    fun `youtube albums keep the plain innertube path`() {
        assertEquals("album/MPRE1", SearchProvider.YOUTUBE.onlineAlbumRoute(album))
    }
}
