package com.jtech.zemer.search

import kotlinx.serialization.Serializable

/**
 * Wire models for **user playlist sharing** (issue #176; handoff
 * `~/zemer-fix/handoff-docs/zemer-app-user-playlists.md`): a user shares a local playlist, the
 * server mints an unguessable `search.zemer.io/user_playlist/<id>` link, and a recipient's tap
 * opens it in-app via the App Link. Deliberately NOT a public index — the unguessable link IS the
 * access control. Snapshots are immutable (re-sharing after an edit mints a new id); members are
 * corpus-validated AND global-blocked-id-filtered at share time server-side, and the receiver's own
 * content filters apply per open (same per-track contract as `/playlist`).
 */
@Serializable
data class ZemerUserPlaylistCreateRequest(
    val title: String,
    val videoIds: List<String>,
    /** The anonymous tracking uuid — used ONLY for the server's share rate limit, never served. */
    val device: String? = null,
)

@Serializable
data class ZemerUserPlaylistCreateResponse(
    val id: String = "",
    /** The shareable URL; host is fixed server-side — display/share verbatim. */
    val url: String = "",
    val kept: Int = 0,
    /** Members dropped at share time (non-corpus or globally blocked) — surfaced as a toast. */
    val dropped: Int = 0,
)

@Serializable
data class ZemerUserPlaylistHeader(
    val id: String = "",
    val title: String = "",
    val createdAt: Long = 0,
    val trackCount: Int = 0,
)

@Serializable
data class ZemerUserPlaylistTrack(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val thumbnail: String? = null,
    val durationSec: Int? = null,
    val explicit: Boolean = false,
    val isVideo: Boolean = false,
)

@Serializable
data class ZemerUserPlaylistResponse(
    val playlist: ZemerUserPlaylistHeader = ZemerUserPlaylistHeader(),
    val tracks: List<ZemerUserPlaylistTrack> = emptyList(),
    val source: String = "",
)
