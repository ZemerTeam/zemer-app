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
    /**
     * Optional sharer display name (≤40 chars; screened server-side with the community-titles term
     * list — a screened name silently drops to null). Prompted once and device-remembered.
     */
    val sharedBy: String? = null,
)

@Serializable
data class ZemerUserPlaylistCreateResponse(
    val id: String = "",
    /** The shareable URL; host is fixed server-side — display/share verbatim. */
    val url: String = "",
    val kept: Int = 0,
    /** Members dropped at share time (non-corpus or globally blocked) — surfaced as a toast. */
    val dropped: Int = 0,
    /**
     * The share's owner secret (live-updating shares, 2026-07-30): returned ONCE at create, never
     * served by any GET. Stored in the [ShareCredentialStore] DataStore map (deliberately NOT a
     * Room column - the feature is schema-free); it is the sole capability for PUT
     * (update-in-place, same URL) and DELETE (unshare). Empty on a PUT response (only create
     * mints it).
     */
    val ownerToken: String = "",
)

/** `PUT /user-playlist/<id>` body: replace the share's state in place — same id, same URL. */
@Serializable
data class ZemerUserPlaylistUpdateRequest(
    val ownerToken: String,
    val title: String,
    val videoIds: List<String>,
    val sharedBy: String? = null,
)

@Serializable
data class ZemerUserPlaylistHeader(
    val id: String = "",
    val title: String = "",
    /** The sharer's screened display name; null when not provided or screened. */
    val sharedBy: String? = null,
    val createdAt: Long = 0,
    val trackCount: Int = 0,
    /**
     * Server track-derived cover. NOT rendered by the app: unless/until the server confirms it is
     * per-request filter-aware, the receiver screen derives its cover from the FIRST
     * client-filtered track (the filteredPlaylistCover doctrine) — server art could leak imagery
     * for tracks this receiver's filters hide.
     */
    val thumbnail: String? = null,
    val totalDurationSec: Int? = null,
    /** Epoch ms of the last owner update; null when the share was never updated after create. */
    val updatedAt: Long? = null,
)

@Serializable
data class ZemerUserPlaylistResponse(
    val playlist: ZemerUserPlaylistHeader = ZemerUserPlaylistHeader(),
    /**
     * The snapshot's tracks are wire-compatible with [ZemerTrack] (the lenient reader ignores this
     * endpoint's extra keys), so the shared mapping pipeline ([ZemerResultMapper.songItems]) runs
     * here too - a fourth hand-rolled copy would silently miss the next defense-in-depth change.
     */
    val tracks: List<ZemerTrack> = emptyList(),
    val source: String = "",
)
