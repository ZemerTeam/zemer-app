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

    @Test
    fun `community flag adds community=true so plays tag community not playlist`() {
        assertEquals(
            "online_playlist/PL1?zemer=true&community=true",
            SearchProvider.ZEMER.onlinePlaylistRoute("PL1", community = true),
        )
        // Default (artist-owned / non-community) is unchanged.
        assertEquals("online_playlist/PL1?zemer=true", SearchProvider.ZEMER.onlinePlaylistRoute("PL1", community = false))
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

    /**
     * The search-provider telemetry field (handoff-docs/zemer-tracking-search-provider-request.md) is
     * sent as `name.lowercase()`, and the server contract accepts exactly `"zemer"`/`"youtube"`
     * (anything else stored as NULL). Renaming an enum constant would silently start NULLing the field,
     * so the mapping is pinned here.
     */
    @Test
    fun `enum names lowercase to the exact tracking-contract provider values`() {
        assertEquals("zemer", SearchProvider.ZEMER.name.lowercase())
        assertEquals("youtube", SearchProvider.YOUTUBE.name.lowercase())
    }
}
