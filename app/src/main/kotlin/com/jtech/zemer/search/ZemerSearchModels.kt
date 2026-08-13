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
    // Podcast SHOWS + EPISODES folded into search (server reply 2026-08-01). Absent on an older
    // server build → empty. Shows open the podcast SHOW screen; episodes play by videoId.
    val podcasts: List<ZemerPodcastShow> = emptyList(),
    val episodes: List<ZemerPodcastEpisode> = emptyList(),
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

/** A track's album link ({id = album browseId, name}) — powers the song menu's "View album". */
@Serializable
data class ZemerTrackAlbum(
    val id: String = "",
    val name: String = "",
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
    // Square album-art URL, when the server sends one on the track (so a song list shows real album art
    // instead of the letterboxed video frame). Absent on surfaces that don't provide it yet;
    // [ZemerResultMapper.toSongItem] then falls back to the videoId-derived thumbnail.
    val thumbnail: String? = null,
    // The track's album {id (browseId), name}, when the server links it — powers the song menu's
    // "View album". Null for standalone singles / videos (no album in the corpus).
    val album: ZemerTrackAlbum? = null,
    val explicit: Boolean = false,
    // `/user_playlist` tracks only (absent = false elsewhere): the client backstop for blockVideos
    // on the one surface whose content is sender-chosen - see ZemerUserPlaylistResponse.toSongItems.
    val isVideo: Boolean = false,
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
    // The primary artist's whitelisted UC… channel id. Present on `/home-rows` cards and (since
    // 2026-08-11) on `/artist` + `/search` album/single rows — id-based credit resolution.
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
    // Operator-featured user-shared playlists (the "Zemer User Playlists" row; contract:
    // `handoff-docs/zemer-app-user-playlists-home-row-request.md`). Absent/empty = row hidden.
    val userPlaylists: List<ZemerFeaturedUserPlaylist> = emptyList(),
)

/**
 * One operator-featured user-shared playlist on `/home-rows.userPlaylists`. [id] is the SHARE id —
 * it opens the existing shared-playlist screen (`user_playlist/<id>`), never an online-playlist
 * route. [trackCount] is the count visible under the request's content flags (entries below the
 * server's floor are omitted server-side); [sharedBy] is the operator-approved display name.
 */
@Serializable
data class ZemerFeaturedUserPlaylist(
    val id: String = "",
    val title: String = "",
    val sharedBy: String? = null,
    val thumbnail: String? = null,
    val trackCount: Int = 0,
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
    // The album's own playlist id (OLAK…), when the server sends it — used for the album's radio/automix
    // when the opener didn't thread one. Absent on older builds; [toAlbumPage] then falls back to the id.
    val playlistId: String? = null,
    val title: String = "",
    val artist: String = "",
    // The primary artist's whitelisted UC… channel id (live 2026-08-11, stuck-skeleton handoff): lets
    // the insert resolve the credit by ID instead of the fragile name lookup. Optional — absent falls
    // back to today's name-only behavior.
    val artistId: String? = null,
    val year: Int? = null,
    val thumbnail: String? = null,
)

/**
 * Wire model for `GET /artist` (search.zemer.io) — an artist's whole catalog, already whitelist-scoped
 * and content-filtered server-side for the flags sent. Returned as flat arrays; the app builds the
 * `ArtistPage` sections from them (there is no pagination — the arrays are complete). A `404` means the
 * artist is filtered out entirely or absent from the corpus; the caller then falls back to InnerTube.
 * The `artist` header and `playlists` reuse [ZemerArtist]/[ZemerPlaylist]; `songs`/`videos` reuse
 * [ZemerTrack]; `albums`/`singles` reuse [ZemerAlbum] (the server splits them on `type`).
 */
@Serializable
data class ZemerArtistResponse(
    val artist: ZemerArtist = ZemerArtist(),
    val songs: List<ZemerTrack> = emptyList(),
    val videos: List<ZemerTrack> = emptyList(),
    val albums: List<ZemerAlbum> = emptyList(),
    val singles: List<ZemerAlbum> = emptyList(),
    val playlists: List<ZemerPlaylist> = emptyList(),
)

/**
 * Wire model for `GET /radio` (search.zemer.io) — the corpus-native, whitelist-pure radio continuation.
 * [tracks] are the next tracks to enqueue; [continuation] is an opaque token echoed back to
 * `GET /radio?continuation=` for the next page (null = end, realistically never for an endless radio).
 * Replaces `YouTube.next()` for SELECTION only — the audio stream stays InnerTube + cipher.
 */
@Serializable
data class ZemerRadioResponse(
    val tracks: List<ZemerTrack> = emptyList(),
    val continuation: String? = null,
)

// ---------------------------------------------------------------------------------------------------
// Podcasts — server discovery contract (handoff `zemer-app-podcasts-request.md`, server reply
// 2026-08-01). Every id/videoId defaults to "" (a single sparse row must never blank the whole
// response — the mapper drops blank-id rows), and durationSeconds defaults to 0 (server sends 0 only
// when YouTube truly has no length; the client resume math degrades gracefully).
// ---------------------------------------------------------------------------------------------------

/** A podcast SHOW row (browse grid, search, host-channel shelf). Keyed/routed on the `MPSP…` id. */
@Serializable
data class ZemerPodcastShow(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    // The host CHANNEL (UC…) the show belongs to — tapping opens the channel (§4) when present, else
    // the show (§3). Resolved server-side for 167/169 shows; rare misses fall back to the show.
    val channelId: String? = null,
    val thumbnail: String? = null,
    val episodeCountText: String? = null,
)

/** An episode row (search, show, channel, new-episodes). `videoId` is the YouTube id the app plays. */
@Serializable
data class ZemerPodcastEpisode(
    val videoId: String = "",
    val title: String = "",
    val podcastId: String? = null,
    val podcastName: String? = null,
    val channelId: String? = null,
    val thumbnail: String? = null,
    val durationSeconds: Int = 0,
    val publishedAt: String? = null,
)

/** The SHOW header on `GET /podcast?id=` (adds description + categories over the browse row). */
@Serializable
data class ZemerPodcastDetail(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val channelId: String? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
)

/** `GET /podcast?id=MPSP…&offset=` — the SHOW page. `nextOffset` is null at the end. */
@Serializable
data class ZemerPodcastResponse(
    val podcast: ZemerPodcastDetail? = null,
    val episodes: List<ZemerPodcastEpisode> = emptyList(),
    val nextOffset: Int? = null,
)

/** The host-CHANNEL header on `GET /podcast-channel?id=`. `banner` is currently always absent. */
@Serializable
data class ZemerPodcastChannelHeader(
    val id: String = "",
    val name: String = "",
    val thumbnail: String? = null,
    val banner: String? = null,
    val description: String? = null,
)

/**
 * `GET /podcast-channel?id=UC…&offset=` — the host-channel page (replaces InnerTube `YouTube.artist`).
 * `offset` pages the channel-wide, newest-first episode list (30/page); `nextOffset` is null at the end
 * (and absent on pre-paging servers / the offline snapshot — both read as "no more pages"). `shows` rides
 * page 0 only.
 */
@Serializable
data class ZemerPodcastChannelResponse(
    val channel: ZemerPodcastChannelHeader? = null,
    val shows: List<ZemerPodcastShow> = emptyList(),
    val episodes: List<ZemerPodcastEpisode> = emptyList(),
    val nextOffset: Int? = null,
)

/** `GET /podcasts/new-episodes?k=` — latest episodes across all whitelisted shows, newest-first. */
@Serializable
data class ZemerNewEpisodesResponse(
    val episodes: List<ZemerPodcastEpisode> = emptyList(),
)

/**
 * `GET /podcast-home-rows?k=` — the telemetry-ranked Podcasts-tab rows (the podcast analogue of
 * `/home-rows`, contract: `handoff-docs/zemer-app-podcast-home-rows-request.md`). [topPodcasts] are
 * shows ranked by completion-weighted distinct-device listening (server-side alphabetical fallback
 * while telemetry is thin, so the row is never empty); [trendingEpisodes] are episodes by recent
 * completion-weighted reach. Whitelist-pure + content-filtered server-side; an empty row → the app
 * hides it (per-row fail-soft). The server's bonus `genres` on a show is ignored (ignoreUnknownKeys).
 */
@Serializable
data class ZemerPodcastHomeRowsResponse(
    // Hand-curated editorial shows (server `data/podcast-featured.json`), served in order — the strongest
    // cold-start signal (zero telemetry), rendered as the LEAD row. Server reply 3, 2026-08-07.
    val featured: List<ZemerPodcastShow> = emptyList(),
    val topPodcasts: List<ZemerPodcastShow> = emptyList(),
    val trendingEpisodes: List<ZemerPodcastEpisode> = emptyList(),
)

/**
 * Wire model for `GET /video-home-rows` (handoff `zemer-app-video-home-rows-request.md`) — the Videos
 * tab's ranked rows beyond the `topVideos` lead row `/home-rows` already serves. Reuses the exact
 * `/home-rows` row shapes so the existing mappers work unchanged. Every list defaults empty (each row
 * is independently fail-soft; the endpoint being absent hides all three).
 */
@Serializable
data class ZemerVideoHomeRowsResponse(
    /** 14d completion-weighted distinct-device reach — deliberately shorter than topVideos' 30d. */
    val trendingVideos: List<ZemerTrack> = emptyList(),
    /** Newest corpus video releases (no telemetry — new videos have no other home surface). */
    val newVideos: List<ZemerTrack> = emptyList(),
    /** Artists ranked by distinct-device video listening; cards open the artist page. */
    val topVideoArtists: List<ZemerArtist> = emptyList(),
)
