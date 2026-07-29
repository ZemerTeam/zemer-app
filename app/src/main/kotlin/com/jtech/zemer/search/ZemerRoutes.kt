package com.jtech.zemer.search

import com.metrolist.innertube.models.AlbumItem

// The nav-route builders for Zemer-served items. The old SearchProvider enum (ZEMER/YOUTUBE) is gone
// with the YouTube search engine (removal greenlit in
// ~/zemer-fix/handoff-docs/zemer-app-artist-album-innertube-swap.md): every consumer passed ZEMER
// literally, so the two-branch route pickers collapsed to these plain functions. Surfaces that still
// render InnerTube-sourced items (browse / charts / new releases) navigate via raw route strings and
// never used these helpers.

/**
 * The `online_playlist` nav route for a Zemer-served playlist. `?zemer=true` opens it through the
 * server's `/playlist` endpoint (tracks/count/cover match the card it was tapped from). [community]
 * adds `&community=true` so the opened screen tags plays `community:<id>` (the discovery-sourced
 * community lists: the home "Community playlists" row and the search Community chip) instead of
 * `playlist:<id>`.
 */
fun zemerPlaylistRoute(playlistId: String, community: Boolean = false): String =
    "online_playlist/$playlistId?zemer=true" + if (community) "&community=true" else ""

/**
 * The `album` nav route for a Zemer-served album. `?zemer=true` loads it through the server's
 * `/album` endpoint (whitelist-scoped, immune to on-device InnerTube bot-gating) plus the card's
 * playlistId, which rides along for the persisted album's real OLAK id (share/radio).
 */
fun zemerAlbumRoute(album: AlbumItem): String =
    "album/${album.browseId}?zemer=true&playlistId=${album.playlistId}"
