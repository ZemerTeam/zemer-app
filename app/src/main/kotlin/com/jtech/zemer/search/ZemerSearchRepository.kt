package com.jtech.zemer.search

import android.content.Context
import com.jtech.zemer.R
import com.jtech.zemer.search.ZemerResultMapper.toAlbumPage
import com.jtech.zemer.search.ZemerResultMapper.toSongItems
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummaryPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A Zemer `/playlist` open, mapped to the UI types the online-playlist screen already renders. */
data class ZemerPlaylistPage(val playlist: PlaylistItem, val songs: List<SongItem>)

/**
 * Entry point for Zemer-engine search. It returns the same `YTItem`/page types the YouTube path does,
 * so the ViewModels can swap providers with a one-line branch and the UI is reused verbatim.
 *
 * Every query goes to [ZemerSearchClient] (search.zemer.io); there is no on-device fallback by design.
 * If the service is unreachable the call throws, the ViewModel shows the search-error state, and the
 * user can flip the toggle to YouTube Music search.
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
     * the server's album header doesn't return one. Not cached — each open is a single fetch.
     */
    suspend fun album(browseId: String, playlistId: String?, options: ZemerSearchOptions): AlbumPage =
        client.album(browseId, options.allowFemale, options.blockVideos).toAlbumPage(playlistId)

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
        val response = client.search(trimmed, options.allowFemale, options.blockVideos, k)
        cacheMutex.withLock { cache[key] = response }
        return response
    }

    companion object {
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
