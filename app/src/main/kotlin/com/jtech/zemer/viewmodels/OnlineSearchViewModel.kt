package com.jtech.zemer.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.innertube.pages.SearchSummary
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.R
import com.jtech.zemer.models.ItemsPage
import com.jtech.zemer.models.toAlbumItem
import com.jtech.zemer.models.toArtistItem
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.models.toSongItem
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.search.ResultDedupe
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val zemerRepo: ZemerSearchRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query =
        requireNotNull(savedStateHandle.get<String>("query")) {
            "query is required but was not provided in navigation arguments"
        }.let(Uri::decode)
    private val initialFilter = savedStateHandle.get<String>("filter")?.let { filterParam ->
        when (filterParam) {
            "albums" -> SearchFilter.FILTER_ALBUM
            "songs" -> SearchFilter.FILTER_SONG
            "artists" -> SearchFilter.FILTER_ARTIST
            "videos" -> SearchFilter.FILTER_VIDEO
            "playlists" -> SearchFilter.FILTER_COMMUNITY_PLAYLIST
            "community_playlists" -> SearchFilter.FILTER_COMMUNITY_PLAYLIST
            "featured_playlists" -> SearchFilter.FILTER_FEATURED_PLAYLIST
            else -> null
        }
    }
    val filter = MutableStateFlow<SearchFilter?>(initialFilter)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
    val isSummaryLoading = MutableStateFlow(true)
    val summaryError = MutableStateFlow<String?>(null)
    val filterLoading = mutableStateMapOf<String, Boolean>()
    val filterError = mutableStateMapOf<String, String?>()

    // Telemetry: ONE `search` event per executed query (this ViewModel is created per submitted
    // query) — the first successful load fires it; chip switches and engine toggles never re-fire.
    // A zero-result search is the most valuable event and is sent faithfully. Persisted in the
    // SavedStateHandle: a back-stack entry restored after process death recreates the ViewModel and
    // reloads results, and must NOT re-fire an event for a query executed in a past session.
    private var searchTracked: Boolean
        get() = savedStateHandle.get<Boolean>(SEARCH_TRACKED_KEY) ?: false
        set(value) {
            savedStateHandle[SEARCH_TRACKED_KEY] = value
        }

    private fun trackSearchOnce(results: Int) {
        if (searchTracked) return
        searchTracked = true
        // `provider` stays in the wire contract (the dashboard splits on it); the app is single-engine
        // now, so it is always "zemer". (The YouTube engine was removed per the handoff greenlight in
        // ~/zemer-fix/handoff-docs/zemer-app-artist-album-innertube-swap.md.)
        Tracker.search(query, results, SEARCH_TRACKED_PROVIDER)
    }

    init {
        viewModelScope.launch {
            // collectLatest so a filter change cancels an in-flight (up to 8s) request instead of
            // queueing behind it — otherwise the chip switch appears frozen.
            filter.collectLatest { selectedFilter ->
                if (selectedFilter == null) {
                    loadSummary(force = summaryPage == null)
                } else {
                    loadFiltered(selectedFilter, force = viewStateMap[selectedFilter.value] == null)
                }
            }
        }
        // A retained results screen must not keep pages fetched under the other mode: toggling
        // offline drops every cached page and reloads the current view from the new source.
        reloadOnOfflineModeChange {
            summaryPage = null
            viewStateMap.clear()
            filterError.clear()
            when (val selected = filter.value) {
                null -> loadSummary(force = true)
                else -> loadFiltered(selected, force = true)
            }
        }
    }

    private suspend fun loadSummary(force: Boolean = false) {
        if (!force && summaryPage != null) return

        // Prevent searching with empty query
        if (query.isBlank()) {
            summaryError.value = "Please enter a search query"
            isSummaryLoading.value = false
            return
        }

        isSummaryLoading.value = true
        summaryError.value = null

        // Manual offline mode: search the downloaded catalog in Room — no server call, no
        // offline-subset fallback, and no error path (nothing can fail loudly). Empty results reuse
        // the normal no-results message; a video download surfaces under a categorical Videos
        // section (relabeled for blocked-video users, like every video shelf).
        if (OfflineModeState.enabled) {
            val summaries = withContext(Dispatchers.IO) { offlineSummaries() }
            // Mode-currency guard: a toggle-off during the Room read means the reload owns the screen.
            if (!OfflineModeState.enabled) return
            summaryPage = SearchSummaryPage(summaries = summaries)
            trackSearchOnce(results = summaries.sumOf { it.items.size })
            if (summaries.isEmpty()) {
                summaryError.value = "No results found for \"$query\""
            }
            isSummaryLoading.value = false
            return
        }

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    // Zemer results are already whitelist-scoped server-side; do not re-filter.
                    val summaries = zemerRepo.summary(query, zemerSearchOptions(context)).summaries
                    // One-result-per-song (I3): drop a video row whose audio counterpart is present on the
                    // same summary (authoritative match only — see ResultDedupe). Only when videos are
                    // unblocked; blocked mode is frozen byte-for-byte (spec §1(a)).
                    val blockVideos = context.dataStore.getSuspend(BlockVideosKey, false)
                    if (blockVideos) summaries else ResultDedupe.dedupeSummaries(summaries)
                }
            }

        result.onSuccess { summaries ->
            // Mode-currency guard: an online fetch that was in flight when offline mode turned ON
            // must not land over the downloaded-only results the mode-change reload just published.
            if (OfflineModeState.enabled) return
            summaryPage = SearchSummaryPage(
                summaries = summaries
            )
            trackSearchOnce(results = summaries.sumOf { it.items.size })

            if (summaries.isEmpty()) {
                summaryError.value = "No results found for \"$query\""
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error // a superseded load, not a search failure
            if (OfflineModeState.enabled) return
            summaryError.value = "Search error: ${error.message ?: "Unknown error"}"
            reportException(error)
        }
        isSummaryLoading.value = false
    }

    private suspend fun loadFiltered(filter: SearchFilter, force: Boolean = false) {
        val key = filter.value
        if (!force && viewStateMap[key] != null) return

        // Prevent searching with empty query
        if (query.isBlank()) {
            filterError[key] = "Please enter a search query"
            filterLoading[key] = false
            return
        }

        filterLoading[key] = true
        filterError[key] = null

        // Manual offline mode: every chip serves from the downloaded catalog (playlist chips have
        // no offline source and read empty). No server call, no error path.
        if (OfflineModeState.enabled) {
            val items = withContext(Dispatchers.IO) {
                val inputs = offlineSearchInputs()
                offlineFilteredItems(filter, inputs.songs, inputs.artists, inputs.albums, inputs.episodes)
            }
            // Mode-currency guard: a toggle-off during the Room read means the reload owns the screen.
            if (!OfflineModeState.enabled) return
            viewStateMap[key] = ItemsPage(items, null)
            trackSearchOnce(results = items.size)
            filterLoading[key] = false
            return
        }

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val items = mutableListOf<com.metrolist.innertube.models.YTItem>()

                    // Local DB results first, so locally-saved artists/albums are surfaced even when
                    // the server misses them. No hide-explicit pass here: artists carry no explicit
                    // flag (the old !isLocal predicate hid LOCAL-FILE artists, not explicit ones),
                    // and the Zemer corpus carries no explicit content to begin with.
                    when (filter) {
                        SearchFilter.FILTER_ARTIST -> {
                            val localArtists = database.searchArtists(query).first()
                            items.addAll(localArtists.map { it.toArtistItem() })
                        }
                        SearchFilter.FILTER_ALBUM -> {
                            val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                            val localAlbums = database.searchAlbums(query).first()
                                .filter { if (hideExplicit) !it.album.explicit else true }
                            items.addAll(localAlbums.map { it.toAlbumItem() })
                        }
                        else -> {} // Songs/videos/playlists: online only (local songs are local search)
                    }

                    // Already whitelist-scoped; Zemer has no pagination (continuation == null).
                    items.addAll(zemerRepo.filtered(query, filter, zemerSearchOptions(context)).items)
                    ItemsPage(items.distinctBy { it.id }, null)
                }
            }

        result.onSuccess { itemsPage ->
            // Mode-currency guard (same as loadSummary): a stale online page never lands offline.
            if (OfflineModeState.enabled) return
            viewStateMap[key] = itemsPage
            trackSearchOnce(results = itemsPage.items.size)
            if (itemsPage.items.isEmpty()) {
                filterError[key] = "No results found for \"$query\""
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error // a superseded load, not a search failure
            filterError[key] = "Search error: ${error.message ?: "Unknown error"}"
            reportException(error)
        }
        filterLoading[key] = false
    }

    fun refresh() {
        viewModelScope.launch {
            val currentFilter = filter.value
            // Drop the Zemer response cache so retry actually re-queries the server instead of
            // re-serving the cached (possibly empty) result; clearing VM state alone is not enough.
            zemerRepo.invalidate()
            summaryPage = null
            viewStateMap.clear()
            filterLoading.clear()
            filterError.clear()
            summaryError.value = null
            isSummaryLoading.value = true
            if (currentFilter == null) {
                loadSummary(force = true)
            } else {
                loadFiltered(currentFilter, force = true)
            }
        }
    }

    /**
     * The downloaded-scoped search results feeding both offline branches. Two engines merged for
     * online-parity match quality:
     * 1. The on-device SUBSET's Hebrew-aware/cross-script matcher (when installed), scoped to
     *    downloaded ids - the subset contributes MATCHING, never non-downloaded content.
     * 2. The Room LIKE baseline (title or artist name), so search works with no subset installed.
     * Subset hits lead (relevance order), Room extras follow; everything runs the offline content
     * gate so the mode can never surface what the (passcode-protected) filter hides online.
     * Nav args arrive URLEncoder-encoded ('+' for spaces) - normalized before matching.
     */
    private suspend fun offlineSearchInputs(): OfflineSearchInputs {
        val q = query.replace('+', ' ').trim()
        val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
        val blockPodcasts = context.dataStore.getSuspend(com.jtech.zemer.constants.BlockPodcastsKey, false)
        val config = com.jtech.zemer.utils.ContentFilterState.current

        // The downloaded universe (id sets scope the subset engine; the episode map upgrades subset
        // episode hits to the local rows the offline UI plays from).
        val allSongs = database.downloadedSongsByCreateDateAsc(includeVideos = true).first()
        val songIds = allSongs.mapTo(HashSet()) { it.id }
        val artistIds = database.downloadedArtists(includeVideos = true).first().mapTo(HashSet()) { it.id }
        val albumIds = database.downloadedAlbums(includeVideos = true).first().mapTo(HashSet()) { it.id }
        val allEpisodes = if (blockPodcasts) emptyList() else {
            database.downloadedEpisodes(com.jtech.zemer.constants.SongSortType.CREATE_DATE, descending = true).first()
        }
        val episodeById = allEpisodes.associateBy { it.id }

        // Engine 1: the subset matcher, filtered to downloaded ids (null when not installed/stale).
        val subsetItems = runCatching { zemerRepo.subsetSummary(q, zemerSearchOptions(context)) }
            .getOrNull()?.summaries?.flatMap { it.items }.orEmpty()
        val subsetSongs = subsetItems.filterIsInstance<com.metrolist.innertube.models.SongItem>()
            .filter { !it.isEpisode && it.id in songIds }
        val subsetArtists = subsetItems.filterIsInstance<com.metrolist.innertube.models.ArtistItem>()
            .filter { it.id in artistIds }
        val subsetAlbums = subsetItems.filterIsInstance<com.metrolist.innertube.models.AlbumItem>()
            .filter { it.browseId in albumIds }
        val subsetEpisodes = subsetItems
            .filter { it is com.metrolist.innertube.models.EpisodeItem || (it is com.metrolist.innertube.models.SongItem && it.isEpisode) }
            .mapNotNull { episodeById[it.id] }

        // Engine 2: the Room LIKE baseline.
        val roomSongs = database.searchDownloadedSongs(q, includeVideos = true).first()
        val roomArtists = database.searchDownloadedArtists(q, includeVideos = true).first()
        val roomAlbums = database.searchDownloadedAlbums(q, includeVideos = true).first()
        val roomEpisodes = if (blockPodcasts) emptyList() else database.searchDownloadedEpisodes(q).first()

        val songs = (subsetSongs + roomSongs.map { it.toSongItem() })
            .distinctBy { it.id }
            .filter { !hideExplicit || !it.explicit }
            .filter { s -> offlineContentGatePasses(s.id, s.artists.mapNotNull { it.id }, config) }
        val artists = (subsetArtists + roomArtists.map { it.toArtistItem() })
            .distinctBy { it.id }
            .filter { offlineContentGatePasses(itemId = null, artistIds = listOf(it.id), config = config) }
        val albums = (subsetAlbums + roomAlbums.map { it.toAlbumItem() })
            .distinctBy { it.id }
            .filter { !hideExplicit || !it.explicit }
            .filter { a -> offlineContentGatePasses(a.browseId, a.artists?.mapNotNull { it.id }.orEmpty(), config) }
        val episodes = (subsetEpisodes + roomEpisodes)
            .distinctBy { it.id }
            .filter { offlineContentGatePasses(it.id, emptyList(), config) }
            .map { it.toSongItem() }
        return OfflineSearchInputs(songs, artists, albums, episodes)
    }

    private suspend fun offlineSummaries(): List<SearchSummary> {
        val inputs = offlineSearchInputs()
        val blockVideos = context.dataStore.getSuspend(BlockVideosKey, false)
        return offlineSearchSummarySections(
            songs = inputs.songs,
            artists = inputs.artists,
            albums = inputs.albums,
            songsTitle = context.getString(R.string.songs),
            videosTitle = context.getString(if (blockVideos) R.string.video_songs else R.string.videos),
            artistsTitle = context.getString(R.string.artists),
            albumsTitle = context.getString(R.string.albums),
            episodes = inputs.episodes,
            episodesTitle = context.getString(R.string.filter_episodes),
        )
    }

    private companion object {
        const val SEARCH_TRACKED_KEY = "searchTracked"
    }
}

/**
 * The `search` event's `provider` wire value. The server contract accepts "zemer"/"youtube" and
 * stores anything else as NULL — single-engine now, so it is always "zemer". Top-level + internal so
 * the wire value is pinned by a unit test.
 */
internal const val SEARCH_TRACKED_PROVIDER = "zemer"
