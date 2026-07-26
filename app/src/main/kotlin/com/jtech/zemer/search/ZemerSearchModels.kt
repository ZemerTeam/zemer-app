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
    // The artist's YouTube channel id. Present on `/home-rows` cards (so the app's one-per-artist
    // dedup + female/israeli defence-in-depth can run); absent (null) on the search categories, where
    // the artist arrives as a name only. Maps to `Artist.id` — null there keeps the prior behaviour.
    val artistId: String? = null,
    val explicit: Boolean = false,
    // `/album` tracks only; absent (null) on the search categories.
    val durationSec: Int? = null,
    val trackNumber: Int? = null,
    // Curated `/zemer-playlists?id=…` tracks only: true = the track entered the playlist via an
    // `albumIds` expansion, false/absent = a direct song pick. Drives the detail screen's
    // All/Albums/Songs chips; absent until the server ships it (requested in
    // handoff-docs/zemer-curated-playlists-track-provenance-request.md) — then every track reads
    // as a Song, which is safe.
    val fromAlbum: Boolean = false,
    // Chart movement, `auto-*` playlists only (contract: the tracking thread's RESPONSE 3). All four
    // are ADDITIVE and ABSENT — never zeroed — when unavailable: a curated (non-chart) playlist, a
    // rank history too young, or a ranking-formula change that just reset the baseline. Absent means
    // render NO badge; it must never fall back to a device-local snapshot, which would show
    // different arrows to different users for the same chart. See [chartMovementOf].
    /**
     * 1-based position on the RAW, UNFILTERED chart — not this list's row index, which is filtered
     * both server-side (the content flags we send) and client-side (explicit filter, chip). Present
     * whenever the chart has a stored ordering, INDEPENDENTLY of the rank history: a position is
     * knowable in cases the movement is not, so this is the test for "is this a ranked chart",
     * never [ZemerCuratedPlaylist.anchorDate], which is absent during a post-formula-change blackout
     * when the chart is still very much a chart.
     */
    val rank: Int? = null,
    val prevRank: Int? = null,
    /** `prevRank − currentRank`, so **positive = climbed**. */
    val delta: Int? = null,
    @SerialName("new") val isNew: Boolean = false,
    /** Charted before, fell off, now back — NOT inferable from [delta]. */
    @SerialName("reentry") val isReentry: Boolean = false,
)

/**
 * A track's movement on an `auto-*` chart, relative to the server's weekly anchor. Null (rather than
 * a member of this type) is the normal "no badge" case — see [ZemerTrack.prevRank].
 */
sealed interface ChartMovement {
    data object New : ChartMovement
    data object Reentry : ChartMovement
    data class Up(val places: Int) : ChartMovement
    data class Down(val places: Int) : ChartMovement
    data object Unchanged : ChartMovement
}

/**
 * The server's documented precedence: `new`/`reentry` are checked BEFORE `delta`, because exactly one
 * of the three is present per row and a new entry has no meaningful delta to render.
 */
fun chartMovementOf(track: ZemerTrack): ChartMovement? = when {
    track.isNew -> ChartMovement.New
    track.isReentry -> ChartMovement.Reentry
    track.delta == null -> null
    track.delta > 0 -> ChartMovement.Up(track.delta)
    track.delta < 0 -> ChartMovement.Down(-track.delta)
    else -> ChartMovement.Unchanged
}

@Serializable
data class ZemerAlbum(
    val id: String = "",
    val playlistId: String? = null,
    val title: String = "",
    val artist: String = "",
    // Present on `/home-rows` cards (see [ZemerTrack.artistId]); absent on the search categories.
    val artistId: String? = null,
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
 * Wire model for `GET /home-rows` (search.zemer.io) — the telemetry-ranked home tab rows, each ranked
 * by real distinct-device listening over a 30-day live window (contract: `handoff-docs/home-rows-plan.md`).
 * Every list is already whitelist-scoped and content-filtered (female / blocked-ids / kidZone) server-side
 * for the flags sent, so the app does NOT re-run the artist-membership whitelist; it re-applies only the
 * client-owned checks as defence-in-depth (female/israeli via [ZemerTrack.artistId], `hideExplicit`).
 *
 * [topCommunity] stays empty until the app tags `community:<playlistId>` playback; the lenient parser
 * ([zemerResponseJson]) tolerates the key being absent on older servers.
 */
@Serializable
data class ZemerHomeRowsResponse(
    val topAlbums: List<ZemerAlbum> = emptyList(),
    val topVideos: List<ZemerTrack> = emptyList(),
    val topArtists: List<ZemerArtist> = emptyList(),
    val topCommunity: List<ZemerPlaylist> = emptyList(),
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

/**
 * Wire model for `GET /zemer-playlists` (no `id`) — the hand-curated "Zemer Playlists" section. The
 * server returns the playlists in editorial order (render as received, never re-sort), with counts,
 * covers and runtimes already computed AGAINST THE FLAGS SENT — so nothing here is re-filtered
 * client-side. An empty list is a normal state (nothing curated yet): the section just doesn't render.
 * (The wire `count` is ignored — [playlists]`.size` after the repository's cleanup is the truth.)
 */
@Serializable
data class ZemerCuratedPlaylistsResponse(
    val playlists: List<ZemerCuratedPlaylist> = emptyList(),
)

@Serializable
data class ZemerCuratedPlaylist(
    // A stable server slug ("shabbos"), NOT a YouTube playlist id — it must never be routed through
    // any YouTube-playlist code path.
    val id: String = "",
    val title: String = "",
    // Always filter-safe: the server derives it from a member track that survives the sent flags.
    val thumbnail: String? = null,
    val trackCount: Int = 0,
    // Null = unknown; the runtime label is hidden then.
    val totalDurationSec: Int? = null,
    // ISO date of the chart the per-track movement is measured against ("movement since"). Weekly,
    // rolling over on Sundays — the chart data itself refreshes twice daily, but the BASELINE does
    // not, so arrows are stable all week by design. Absent on non-chart playlists and whenever the
    // per-track movement fields are absent too.
    val anchorDate: String? = null,
)

/**
 * Wire model for `GET /zemer-playlists?id=…` — one curated playlist's tracks, in curated order,
 * already filtered server-side for the flags sent. Tracks reuse [ZemerTrack]; the extra per-track
 * fields the endpoint sends (`playCount`, `releaseDate`, `isVideo`) are ignored by the lenient reader.
 * [albums] = the curator's `albumIds` as browsable rows (same shape as the `/search` album category —
 * [ZemerAlbum]), post-filter, in curated order; absent on an older server (Albums chip just empty —
 * requested in handoff-docs/zemer-curated-playlists-albums-list-request.md).
 */
@Serializable
data class ZemerCuratedPlaylistResponse(
    val playlist: ZemerCuratedPlaylist = ZemerCuratedPlaylist(),
    val albums: List<ZemerAlbum> = emptyList(),
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
