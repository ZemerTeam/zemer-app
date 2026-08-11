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

/** The genre catalog screen (the home chips row's see-all). */
fun zemerGenresRoute(): String = "genres"

/**
 * One genre's detail screen. [id] is the server genre slug — the vocabulary is `[\w-]` only
 * (e.g. "shavuos-simchas-torah"), URL- and nav-safe as-is, so no encoding is applied (keeps the
 * builder pure for the JVM tests, like the other helpers here).
 */
fun zemerGenreRoute(id: String): String = "genre/$id"

/**
 * A genre's per-section see-all screen (its full Albums or Singles grid). [id] is the genre slug
 * ([\w-], no encoding); [section] is one of [GENRE_SECTION_ALBUMS]/[GENRE_SECTION_SINGLES].
 */
fun zemerGenreSectionRoute(id: String, section: String): String = "genre_section/$id?section=$section"

const val GENRE_SECTION_ALBUMS = "albums"
const val GENRE_SECTION_SINGLES = "singles"

/** The podcast-genre catalog screen. */
fun zemerPodcastGenresRoute(): String = "podcast_genres"

/**
 * One podcast genre's detail screen (its flat list of shows). [id] is the server genre slug
 * ([\w-] only, URL- and nav-safe as-is, so no encoding — keeps the builder pure for the JVM tests).
 */
fun zemerPodcastGenreRoute(id: String): String = "podcast_genre/$id"
