package com.jtech.zemer.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the `GET /search` response served by search.zemer.io. Field names match the JSON
 * exactly; everything that the server may omit is nullable/defaulted so a sparse row never fails
 * deserialization. The `Json` reader is configured with `ignoreUnknownKeys = true`, so new server
 * fields are forward-compatible.
 */
@Serializable
data class ZemerSearchResponse(
    val q: String = "",
    val count: Int = 0,
    val categories: ZemerCategories = ZemerCategories(),
)

@Serializable
data class ZemerCategories(
    val artists: List<ZemerArtist> = emptyList(),
    val songs: List<ZemerTrack> = emptyList(),
    val albums: List<ZemerAlbum> = emptyList(),
    val singles: List<ZemerAlbum> = emptyList(),
    val videos: List<ZemerTrack> = emptyList(),
    // Artist-owned playlists (the "Featured playlists" chip) and the community-discovered playlists
    // (the "Community playlists" chip) are two separate server categories; either may be absent
    // (older server build) and then defaults to empty.
    val playlists: List<ZemerPlaylist> = emptyList(),
    val community: List<ZemerPlaylist> = emptyList(),
)

// id/videoId default to "" rather than being required: kotlinx throws MissingFieldException for the
// WHOLE response if one element omits a required field, so a single sparse row would blank the entire
// result. The mapper drops rows whose id is blank instead.
@Serializable
data class ZemerArtist(
    val id: String = "",
    val name: String = "",
    val thumbnail: String? = null,
)

/** Both songs and videos share this shape (videos differ only by which category they arrive in). */
@Serializable
data class ZemerTrack(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val explicit: Boolean = false,
    // `/album` tracks only; absent (null) on the search categories.
    val durationSec: Int? = null,
    val trackNumber: Int? = null,
)

@Serializable
data class ZemerAlbum(
    val id: String = "",
    val playlistId: String? = null,
    val title: String = "",
    val artist: String = "",
    val year: Int? = null,
    val thumbnail: String? = null,
)

@Serializable
data class ZemerPlaylist(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnail: String? = null,
    // Number of whitelisted songs the playlist actually serves. The server filters community playlists
    // to the whitelist at open time, so this — not the raw `total` — is the count to surface (showing
    // `total` would over-count vs. what the user gets when they open it). Absent on older server builds.
    @SerialName("whitelisted") val songCount: Int? = null,
)

/**
 * Wire model for `GET /playlist` (search.zemer.io). [tracks] is already whitelist-scoped and
 * content-filtered server-side (`tracks.size == whitelisted`), so the opened list, count and cover all
 * come from the same source as the search card — never re-run the local artist whitelist over it. The
 * header [ZemerPlaylistHeader.thumbnail] is filter-aware (derived from the first surviving track).
 */
@Serializable
data class ZemerPlaylistResponse(
    val playlist: ZemerPlaylistHeader = ZemerPlaylistHeader(),
    val tracks: List<ZemerTrack> = emptyList(),
    val total: Int = 0,
    val whitelisted: Int = 0,
)

@Serializable
data class ZemerPlaylistHeader(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnail: String? = null,
)

/**
 * Wire model for `GET /album` (search.zemer.io). [tracks] arrive already whitelist-scoped and
 * content-filtered server-side (an entirely blocked album is a 404, not an empty list), so the opened
 * album matches the search card — never re-run the local artist whitelist over it. The header carries
 * no playlistId; the search card's rides along on the nav route instead.
 */
@Serializable
data class ZemerAlbumResponse(
    val album: ZemerAlbumHeader = ZemerAlbumHeader(),
    val tracks: List<ZemerTrack> = emptyList(),
)

@Serializable
data class ZemerAlbumHeader(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val year: Int? = null,
    val thumbnail: String? = null,
)
