package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem

/**
 * Which engine backs the online search screen.
 *
 * - [ZEMER] — the whitelist-scoped, Hebrew-aware engine at search.zemer.io (the default). Results are
 *   already content-filtered server-side, so the app does not re-run the local whitelist filter on them.
 * - [YOUTUBE] — the upstream YouTube Music search path, with the local whitelist filter applied on top.
 *
 * Persisted as its [name] under `searchProvider` (see `SearchProviderKey`).
 */
enum class SearchProvider {
    ZEMER,
    YOUTUBE,
}

/**
 * The `online_playlist` nav route for a playlist opened from a search result. Zemer-sourced playlists
 * carry `?zemer=true` so the screen opens them through the server's `/playlist` endpoint (tracks/count/
 * cover match the search card); YouTube-sourced playlists keep the plain InnerTube path.
 */
fun SearchProvider.onlinePlaylistRoute(playlistId: String): String =
    if (this == SearchProvider.ZEMER) "online_playlist/$playlistId?zemer=true" else "online_playlist/$playlistId"

/**
 * The `album` nav route for an album opened from a search result. Zemer-sourced albums carry
 * `?zemer=true` so the screen loads them through the server's `/album` endpoint (whitelist-scoped,
 * immune to on-device InnerTube bot-gating) plus the search card's playlistId — the server's album
 * header doesn't return one, and the persisted album needs the real OLAK… id for share/radio.
 * YouTube-sourced albums keep the plain InnerTube path.
 */
fun SearchProvider.onlineAlbumRoute(album: AlbumItem): String =
    if (this == SearchProvider.ZEMER) {
        "album/${album.browseId}?zemer=true&playlistId=${album.playlistId}"
    } else {
        "album/${album.browseId}"
    }
