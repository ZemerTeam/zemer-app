package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.R
import com.jtech.zemer.offline.OfflineReadProvider
import com.jtech.zemer.search.ZemerResultMapper.toAlbumFacetPage
import com.jtech.zemer.search.ZemerResultMapper.toAlbumItems
import com.jtech.zemer.search.ZemerResultMapper.toAlbumPage
import com.jtech.zemer.search.ZemerResultMapper.toArtistPage
import com.jtech.zemer.search.ZemerResultMapper.toEpisodeItems
import com.jtech.zemer.search.ZemerResultMapper.toGenrePage
import com.jtech.zemer.search.ZemerResultMapper.toChannelEpisodeItems
import com.jtech.zemer.search.ZemerResultMapper.toPodcastChannelPage
import com.jtech.zemer.search.ZemerResultMapper.toPodcastGenrePage
import com.jtech.zemer.search.ZemerResultMapper.toPodcastPage
import com.jtech.zemer.search.ZemerResultMapper.toSongItems
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.PodcastPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummaryPage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A Zemer `/playlist` open, mapped to the UI types the online-playlist screen already renders. */
data class ZemerPlaylistPage(val playlist: PlaylistItem, val songs: List<SongItem>)

/** A Zemer `/radio` page: the next whitelist-pure tracks + the opaque continuation token (null = end). */
data class ZemerRadioPage(val songs: List<SongItem>, val continuation: String?)

/**
 * A curated `/zemer-playlists?id=…` open: the server header plus playable, already-filtered tracks.
 * [albums] = the curated albums as browsable rows (the detail screen's Albums chip). [albumTrackIds] =
 * the videoIds that entered the playlist via an album expansion ([ZemerTrack.fromAlbum]) — the Songs
 * chip's complement, and what Play/Shuffle plays under the Albums chip ([SongItem] can't carry it).
 */
data class ZemerCuratedPlaylistPage(
    val playlist: ZemerCuratedPlaylist,
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val albumTrackIds: Set<String>,
    /**
     * videoId → chart movement, for the `auto-*` playlists. Kept beside [songs] rather than on the
     * items themselves because [SongItem] is an innertube model shared with the YouTube path — the
     * same reason [albumTrackIds] is a side set. Missing key = no badge (the common case).
     */
    val movement: Map<String, ChartMovement>,
    /**
     * videoId → 1-based position on the unfiltered chart. Non-empty IS the test for "ranked chart":
     * the server emits `rank` whenever a stored ordering exists, even when no movement badges do.
     * Because our list is filtered, the displayed positions legitimately have GAPS (…31, 32, 34…) —
     * the dropped row's position is left empty rather than absorbed, so every position keeps meaning
     * the same thing as the delta beside it. Row count therefore does NOT equal the last position;
     * never derive one from the other.
     */
    val rank: Map<String, Int>,
)

/**
 * Whether [this] is the server being unreachable, as opposed to a code bug. Timeouts and non-2xx
 * responses surface as [IOException] (the client wraps them), but Ktor CIO signals missing
 * connectivity / dead DNS — the offline feature's flagship scenario — as
 * [UnresolvedAddressException], which is an [IllegalArgumentException], NOT an [IOException]
 * (same hazard `LyricsHelper` documents). Both must trigger the fallback.
 */
internal fun Throwable.isZemerServerUnreachable(): Boolean =
    this is IOException || this is UnresolvedAddressException

/**
 * Server-first with offline fallback: run [server]; if it fails because the service is unreachable
 * ([isZemerServerUnreachable]), serve [offline] from the on-device snapshot when one is present, else
 * rethrow so the ViewModel keeps its normal error/retry behavior. A non-network null (a 404 the
 * client returns as null) is returned as-is and never triggers the fallback; a non-network exception
 * is never masked. Top-level + `internal` so the routing policy is unit-tested without an Android
 * runtime or mocking.
 */
internal suspend fun <T> serverOrOffline(server: suspend () -> T, offline: suspend () -> T?): T =
    try {
        server()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!e.isZemerServerUnreachable()) throw e
        offline() ?: throw e
    }

/**
 * Entry point for Zemer search — the app's ONLY search engine (the YouTube engine was removed per the
 * handoff greenlight in `~/zemer-fix/handoff-docs/zemer-app-artist-album-innertube-swap.md`). It returns the same
 * `YTItem`/page types the old YouTube path did, so the search UI is reused verbatim.
 *
 * Queries go to [ZemerSearchClient] (search.zemer.io) first; if the service is unreachable AND the user
 * has downloaded an on-device snapshot, the reproducible endpoints fall back to [OfflineReadProvider]
 * (search / album / home-rows / curated playlists) so browse + search keep working offline. With no
 * snapshot the call throws, and the ViewModel shows the search-error state with Retry. A rare
 * not-yet-harvested miss (regular-channel-only songs, brand-new releases inside the harvest lag) shows
 * the graceful empty state — the residual the server side is closing via the #108 regular-channel
 * harvest. `/playlist` and `/radio` are live-only (not in the snapshot).
 *
 * Responses are memoized in a small LRU keyed by (k, filters, query): the song/video/album/artist/
 * featured-playlist chips all request the same k, so after the first they hit the cache instead of
 * re-fetching the full payload; the summary and as-you-type share the k=8 entry too. The Community
 * chip is the exception — it requests a much larger k so its whole curated set comes back uncapped (see
 * [K_COMMUNITY]) — so it owns its own cache entry.
 */
@Singleton
class ZemerSearchRepository @Inject constructor(
    private val client: ZemerSearchClient,
    private val offlineReads: OfflineReadProvider,
    @ApplicationContext private val context: Context,
) {
    // Localized "N songs" for a playlist's whitelisted track count (reuses the shared n_song plural).
    private val formatSongCount: (Int) -> String =
        { n -> context.resources.getQuantityString(R.plurals.n_song, n, n) }

    suspend fun summary(query: String, options: ZemerSearchOptions): SearchSummaryPage =
        ZemerResultMapper.summaryPage(fetch(query, options, K_SUMMARY), options.hideExplicit, formatSongCount)

    suspend fun filtered(query: String, filter: SearchFilter, options: ZemerSearchOptions): SearchResult {
        // The Community chip browses a whole curated set, so it must not be clipped by the default
        // per-chip cap; every other chip uses K_FILTER.
        val k = if (filter.value == SearchFilter.FILTER_COMMUNITY_PLAYLIST.value) K_COMMUNITY else K_FILTER
        return ZemerResultMapper.filtered(fetch(query, options, k), filter, options.hideExplicit, formatSongCount)
    }

    suspend fun suggestions(query: String, options: ZemerSearchOptions): SearchSuggestions =
        ZemerResultMapper.suggestions(fetch(query, options, K_SUGGEST), options.hideExplicit, formatSongCount)

    /**
     * Open a playlist through the server's `/playlist` endpoint so the tracks, count and cover match the
     * search card (which comes from the same server filter) — instead of the InnerTube fetch +
     * local-whitelist path, which produced a different count. The header is a synthetic [PlaylistItem]:
     * count comes from the returned (already-filtered) track list, and the cover from the server's
     * filter-aware [ZemerPlaylistHeader.thumbnail]. Not cached — each open is a single fetch.
     */
    suspend fun playlist(id: String, options: ZemerSearchOptions): ZemerPlaylistPage {
        val response = client.playlist(id, options.allowFemale, options.blockVideos)
        val songs = response.toSongItems(options.hideExplicit)
        val header = PlaylistItem(
            id = id,
            title = response.playlist.title,
            author = response.playlist.artist.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = null) },
            songCountText = songs.size.takeIf { it > 0 }?.let(formatSongCount),
            thumbnail = response.playlist.thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        return ZemerPlaylistPage(header, songs)
    }

    /**
     * Open an album through the server's `/album` endpoint: the InnerTube album fetch runs on the
     * server (immune to on-device bot-gating/rate limits) and comes back already whitelist-scoped +
     * content-filtered, mapped to the same [AlbumPage] the YouTube path yields so the album screen
     * and DB persist flow are reused unchanged. [playlistId] is the search card's OP playlist id —
     * the server's album header doesn't return one. Null = 404 (the album is gone from the
     * whitelist/corpus) — the caller deletes its stale local copy. Not cached — each open is a
     * single fetch.
     */
    suspend fun album(browseId: String, playlistId: String?, options: ZemerSearchOptions): AlbumPage? =
        serverOrOffline(
            server = { client.album(browseId, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.album(browseId, options.allowFemale, options.blockVideos) },
        )?.toAlbumPage(playlistId)

    /**
     * The telemetry-ranked home rows (albums / videos / artists), already whitelist-scoped and
     * content-filtered server-side for the flags sent, mapped to the app's native item types. Like the
     * curated playlists this is deliberately NOT cached: a plain re-fetch per home load is the endpoint's
     * freshness contract and guarantees a response fetched under one flag set is never rendered under
     * another. Ranked order is preserved; the caller applies its one-per-artist rotation + fallback.
     */
    suspend fun homeRows(options: ZemerSearchOptions): ZemerResultMapper.HomeRows =
        ZemerResultMapper.homeRows(
            serverOrOffline(
                server = { client.homeRows(options.allowFemale, options.blockVideos) },
                offline = { offlineReads.homeRows(options.allowFemale, options.blockVideos) },
            ),
            formatSongCount,
        )

    /**
     * Open an artist through the server's `/artist` endpoint (whitelist-scoped + content-filtered),
     * mapped to the [AlbumPage]-sibling [ArtistPage] the artist screen already consumes. Null = 404
     * (the artist is filtered out entirely, or is absent from the corpus) — the screen shows its
     * not-available state. Offline-covered: search/home-rows render tappable artist cards from the
     * snapshot, so an artist open must not be the one dead surface in the outage mode. Not cached: a
     * single user-initiated open, same freshness contract as [album]/[playlist].
     */
    suspend fun artist(id: String, options: ZemerSearchOptions): ArtistPage? =
        serverOrOffline(
            server = { client.artist(id, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.artist(id, options.allowFemale, options.blockVideos) },
        )?.toArtistPage(options.hideExplicit, formatSongCount)

    // --- Podcasts. Server-first with the on-device snapshot fallback (server reply 4: the subset now
    // carries podcast shards, pre-gated to approved channels). The browse grid + channel allow-set come
    // from the Room-backed content mirror. Playback stays InnerTube: an episode carries its YouTube
    // videoId and plays through the existing pipeline. `/playlist` + `/radio` remain live-only. ---

    /** A SHOW page (header + one episode page). Null when the show is unknown / filtered out (404). */
    suspend fun podcast(id: String, offset: Int, options: ZemerSearchOptions): PodcastPage? =
        serverOrOffline(
            server = { client.podcast(id, offset, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.podcast(id, offset, options.allowFemale, options.blockVideos) },
        )?.toPodcastPage()

    /**
     * A host CHANNEL as an [ArtistPage] plus the episodes paging cursor (its shows + latest episodes).
     * Null on 404. The offline snapshot is unpaged — its response carries no `nextOffset`, so the
     * cursor is simply null there.
     */
    suspend fun podcastChannel(id: String, options: ZemerSearchOptions): ZemerResultMapper.PodcastChannelPage? =
        serverOrOffline(
            server = { client.podcastChannel(id, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.podcastChannel(id, options.allowFemale, options.blockVideos) },
        )?.toPodcastChannelPage()

    /**
     * One deeper page of the channel-wide episode list (`/podcast-channel?offset=`), as the episode
     * rows plus the next cursor. LIVE-ONLY: the offline snapshot has no paging, so an outage simply
     * stops the see-all at what's loaded (the caller leaves its cursor unchanged and retries on the
     * next near-end trigger).
     */
    suspend fun podcastChannelEpisodes(
        id: String,
        offset: Int,
        options: ZemerSearchOptions,
    ): Pair<List<EpisodeItem>, Int?>? =
        client.podcastChannel(id, options.allowFemale, options.blockVideos, offset)
            ?.let { it.toChannelEpisodeItems() to it.nextOffset }

    /**
     * The telemetry-ranked Podcasts-tab rows (Top Podcasts + Trending Episodes). Live-only (discovery,
     * like `/playlist`/`/radio`) — no offline snapshot; the caller's fail-soft VM hides the rows on a
     * failure. The server applies an alphabetical fallback for `topPodcasts` while telemetry is thin.
     */
    suspend fun podcastHomeRows(options: ZemerSearchOptions): ZemerResultMapper.PodcastHomeRows =
        ZemerResultMapper.podcastHomeRows(client.podcastHomeRows(options.allowFemale, options.blockVideos))

    /** The Videos tab's ranked rows. Live-only like [podcastHomeRows]; throws on failure (fail-soft VM). */
    suspend fun videoHomeRows(options: ZemerSearchOptions): ZemerResultMapper.VideoHomeRows =
        ZemerResultMapper.videoHomeRows(client.videoHomeRows(options.allowFemale, options.blockVideos))

    /** Latest episodes across all whitelisted shows (Library New Episodes), newest-first. */
    suspend fun podcastsNewEpisodes(k: Int, options: ZemerSearchOptions): List<EpisodeItem> =
        serverOrOffline(
            server = { client.podcastsNewEpisodes(k, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.podcastsNewEpisodes(k, options.allowFemale, options.blockVideos) },
        ).toEpisodeItems()

    /**
     * The flat podcast-genre catalog. Server-first with the offline snapshot fallback (the subset's
     * `podcasts` shard carries each show's genres). Flag-keyed TTL memo, mirroring [genres] — the Home-
     * adjacent catalog is opened repeatedly, and the data changes on curation timescales.
     */
    suspend fun podcastGenres(options: ZemerSearchOptions): PodcastGenreCatalog {
        val key = "${options.allowFemale}|${options.blockVideos}"
        val now = System.currentTimeMillis()
        podcastGenresCache?.let { (cachedKey, at, value) ->
            if (cachedKey == key && now - at < GENRES_CACHE_TTL_MS) return value
        }
        val response = serverOrOffline(
            server = { client.podcastGenres(options.allowFemale, options.blockVideos) },
            offline = { offlineReads.podcastGenres(options.allowFemale, options.blockVideos) },
        )
        return PodcastGenreCatalog(
            kinds = response.kinds,
            genres = response.genres.filter { it.id.isNotBlank() }.distinctBy { it.id },
        ).also { podcastGenresCache = Triple(key, now, it) }
    }

    @Volatile
    private var podcastGenresCache: Triple<String, Long, PodcastGenreCatalog>? = null

    /**
     * One podcast genre's page (its member shows). Null = 404 (unknown slug / all filtered out) — the
     * screen backs out. Server-first with the offline fallback; uncached, like [genre].
     */
    suspend fun podcastGenre(id: String, options: ZemerSearchOptions): ZemerResultMapper.PodcastGenrePage? =
        serverOrOffline(
            server = { client.podcastGenre(id, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.podcastGenre(id, options.allowFemale, options.blockVideos) },
        )?.toPodcastGenrePage()

    /**
     * Corpus-native radio (see [ZemerRadioResponse]): the first page seeded by [kind]/[seed] (`artist` /
     * `album` / `song`, or `shuffle` with a null seed), mapped to playable [SongItem]s. Not cached — a
     * live continuation; tracks are whitelist-pure + blocked-ids filtered server-side.
     */
    suspend fun radio(kind: String, seed: String?, options: ZemerSearchOptions): ZemerRadioPage =
        client.radio(kind, seed, options.allowFemale, options.blockVideos).toRadioPage()

    /** The next radio page for an opaque [continuation] token (the seed + flags ride inside the token). */
    suspend fun radioContinuation(continuation: String): ZemerRadioPage =
        client.radioContinuation(continuation).toRadioPage()

    /**
     * The live Zemer Stations for the "Zemer Radio" home row ([liveStations]: live-only cards,
     * absolute covers, fail-soft empty). Deliberately NOT wrapped in [serverOrOffline] — a
     * synchronized broadcast cannot be served from a snapshot, so stations are live-only like
     * `/playlist` and `/radio`. Not cached: the responses are clock-dependent, and the row's
     * `nowPlaying` line refreshes once per home load by contract (handoff, settled 2026-07-29).
     */
    suspend fun stations(): List<ZemerStation> =
        client.stations().liveStations(::resolveZemerUrl)

    /**
     * One station's tune-in payload — the on-air entry with its live offset + the schedule runway.
     * Null = unknown id OR station offline (hide the card / show the station-offline player state and
     * retry later; never a silent fallback). Live-only and uncached, like [stations].
     */
    suspend fun stationTuneIn(id: String, next: Int = 5): ZemerStationTuneInResponse? =
        client.station(id, next)?.let { resp ->
            resp.copy(station = resp.station.copy(thumbnail = resolveZemerUrl(resp.station.thumbnail)))
        }

    // Routed through the mapper so radio gets the same dropBlocked id-overrides pass as every other
    // Zemer surface (the server filters too; this covers its ~10-min override-sync lag).
    private fun ZemerRadioResponse.toRadioPage(): ZemerRadioPage =
        ZemerRadioPage(
            songs = toSongItems(),
            continuation = continuation,
        )

    /**
     * The hand-curated "Zemer Playlists" section, in editorial order (rendered as received). Not
     * cached — the doc'd contract is a plain re-fetch on screen open (single-digit-ms server reads),
     * which also guarantees a response fetched under one flag set is never shown under another.
     * Sparse/duplicate rows are dropped defensively (the id keys a Compose lazy list).
     */
    suspend fun curatedPlaylists(options: ZemerSearchOptions): List<ZemerCuratedPlaylist> =
        serverOrOffline(
            server = { client.curatedPlaylists(options.allowFemale, options.blockVideos) },
            offline = { offlineReads.curatedPlaylists(options.allowFemale, options.blockVideos) },
        )
            .playlists
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }

    /**
     * One curated playlist's tracks, in curated order, filtered server-side for the same flags as the
     * list. Null = 404 (gone, or nothing survives these flags) — the screen backs out gracefully.
     */
    suspend fun curatedPlaylist(id: String, options: ZemerSearchOptions): ZemerCuratedPlaylistPage? =
        serverOrOffline(
            server = { client.curatedPlaylist(id, options.allowFemale, options.blockVideos) },
            offline = { offlineReads.curatedPlaylist(id, options.allowFemale, options.blockVideos) },
        )?.let { response ->
            ZemerCuratedPlaylistPage(
                playlist = response.playlist,
                songs = response.toSongItems(options.hideExplicit),
                albums = response.toAlbumItems(),
                albumTrackIds = response.tracks
                    .filter { it.fromAlbum && it.videoId.isNotBlank() }
                    .map { it.videoId }
                    .toSet(),
                movement = response.tracks
                    .filter { it.videoId.isNotBlank() }
                    .mapNotNull { track -> chartMovementOf(track)?.let { track.videoId to it } }
                    .toMap(),
                rank = response.tracks
                    .filter { it.videoId.isNotBlank() }
                    .mapNotNull { track -> track.rank?.let { track.videoId to it } }
                    .toMap(),
            )
        }

    /**
     * The genre catalog for the home chips row + the catalog screen, in the server's
     * most-populated-first order, with counts computed against the flags sent. LIVE-ONLY: the genre
     * taxonomy is not in the offline snapshot, so this is deliberately not wrapped in
     * [serverOrOffline] (like `/playlist`, `/radio` and `/stations`). Memoized for a short TTL,
     * KEYED ON THE FLAG PAIR (so a response fetched under one flag set is never rendered under
     * another): the catalog is fetched by two independent surfaces (the Home strip's ViewModel and
     * the catalog screen's) and Home re-fires its refresh on every return to the tab, so a common
     * Home → see-all → chip → back loop otherwise issues ~4 identical requests within seconds for
     * data that changes on curation timescales. Sparse/duplicate/non-music cleanup is the caller's
     * ([musicGenres]) — the raw kinds are needed to group the catalog.
     */
    suspend fun genres(options: ZemerSearchOptions): List<ZemerGenreSummary> {
        val key = "${options.allowFemale}|${options.blockVideos}"
        val now = System.currentTimeMillis()
        genresCache?.let { (cachedKey, at, value) ->
            if (cachedKey == key && now - at < GENRES_CACHE_TTL_MS) return value
        }
        return client.genres(options.allowFemale, options.blockVideos)
            .genres
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .also { genresCache = Triple(key, now, it) }
    }

    // One-entry TTL memo for [genres]; a benign write race just refreshes the same data.
    @Volatile
    private var genresCache: Triple<String, Long, List<ZemerGenreSummary>>? = null

    /**
     * One genre's page, filtered server-side for the flags sent; [offset] pages the songs/videos
     * tracklist. Null = 404 (unknown slug, or nothing survives these flags) — the screen backs out
     * gracefully, mirroring [curatedPlaylist]. Live-only and uncached, like [genres].
     */
    suspend fun genre(id: String, options: ZemerSearchOptions, offset: Int = 0): ZemerResultMapper.ZemerGenrePage? =
        client.genre(id, options.allowFemale, options.blockVideos, offset)?.toGenrePage(options.hideExplicit)

    /**
     * One page of a genre's full Albums or Singles list (`facet` see-all). Null = 404 (gone/empty).
     * Live-only and uncached, like [genre]; the caller pages with [offset] until nextOffset is null.
     */
    suspend fun genreFacet(
        id: String,
        facet: String,
        options: ZemerSearchOptions,
        offset: Int = 0,
    ): ZemerResultMapper.ZemerGenreFacetPage? =
        client.genreFacet(id, facet, options.allowFemale, options.blockVideos, offset)?.toAlbumFacetPage()

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<String, ZemerSearchResponse>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ZemerSearchResponse>) = size > CACHE_SIZE
    }

    /**
     * Drop all memoized responses so the next call re-hits the server. Without this the session LRU
     * would keep serving a stale (or empty) response — making the "Retry" action a silent no-op — for
     * the whole process lifetime. Called from the ViewModel's pull-to-refresh / retry path.
     */
    suspend fun invalidate() = cacheMutex.withLock { cache.clear() }

    private suspend fun fetch(query: String, options: ZemerSearchOptions, k: Int): ZemerSearchResponse {
        val trimmed = query.trim()
        val key = "$k|${options.allowFemale}|${options.blockVideos}|$trimmed"
        cacheMutex.withLock { cache[key] }?.let { return it }
        // Only a SERVER response is memoized. Caching an offline-fallback response would keep serving
        // the reduced snapshot result for the rest of the process after the server recovers: the
        // access-ordered LRU refreshes the entry on every hit so it never ages out, and invalidate()
        // only runs from the error-state Retry path — which a "successfully" cached result never shows.
        return serverOrOffline(
            server = {
                client.search(trimmed, options.allowFemale, options.blockVideos, k)
                    .also { response -> cacheMutex.withLock { cache[key] = response } }
            },
            offline = { offlineReads.search(trimmed, k, options.allowFemale, options.blockVideos) },
        )
    }

    companion object {
        // Long enough to absorb a navigation burst, short enough that a curation change appears
        // within a minute (the catalog itself updates on far slower timescales).
        private const val GENRES_CACHE_TTL_MS = 60_000L
        private const val K_SUMMARY = 8
        private const val K_FILTER = 100
        private const val K_SUGGEST = 8
        // Community playlists are a browsable curated set (a few hundred and growing); request well
        // above the corpus size so the Community chip returns all query-relevant results uncapped (the
        // server now honors k for that category). Bump if the community catalog ever approaches this.
        private const val K_COMMUNITY = 500
        private const val CACHE_SIZE = 12
    }
}
