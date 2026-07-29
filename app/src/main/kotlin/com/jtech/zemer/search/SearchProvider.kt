package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem

/**
 * Which backend an item came from — used ONLY to pick its open route (below). The YouTube search
 * ENGINE is gone (single-engine app; removal greenlit in the handoff doc
 * `zemer-app-artist-album-innertube-swap.md`): search is always [ZEMER], and [YOUTUBE] survives
 * solely for the surfaces that still render InnerTube-sourced items (browse / charts / new releases),
 * whose albums/playlists must keep the plain InnerTube open path.
 *
 * - [ZEMER] — whitelist-scoped items from search.zemer.io; open via the server routes (`?zemer=true`).
 * - [YOUTUBE] — InnerTube-sourced items; open via the plain routes.
 */
enum class SearchProvider {
    ZEMER,
    YOUTUBE,
}

/**
 * The `online_playlist` nav route for a playlist opened from a search result. Zemer-sourced playlists
 * carry `?zemer=true` so the screen opens them through the server's `/playlist` endpoint (tracks/count/
 * cover match the search card); YouTube-sourced playlists keep the plain InnerTube path. [community] adds
 * `&community=true` so the opened screen tags plays `community:<id>` (the discovery-sourced community
 * lists — the home "Community playlists" row and the search Community chip) instead of `playlist:<id>`.
 */
fun SearchProvider.onlinePlaylistRoute(playlistId: String, community: Boolean = false): String =
    if (this == SearchProvider.ZEMER) {
        "online_playlist/$playlistId?zemer=true" + if (community) "&community=true" else ""
    } else {
        "online_playlist/$playlistId"
    }

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
