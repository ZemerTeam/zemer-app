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
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.ItemsPage
import com.jtech.zemer.models.toMediaMetadata
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
        // now, so it is always "zemer". (The YouTube engine was removed per the handoff greenlight —
        // see zemer-app-artist-album-innertube-swap.md.)
        Tracker.search(query, results, TRACKED_PROVIDER)
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

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    // Zemer results are already whitelist-scoped server-side; do not re-filter.
                    zemerRepo.summary(query, zemerSearchOptions(context)).summaries
                }
            }

        result.onSuccess { summaries ->
            summaryPage = SearchSummaryPage(
                summaries = summaries
            )
            trackSearchOnce(results = summaries.sumOf { it.items.size })

            if (summaries.isEmpty()) {
                summaryError.value = "No results found for \"$query\""
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error // a superseded load, not a search failure
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

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                    val items = mutableListOf<com.metrolist.innertube.models.YTItem>()

                    // Local DB results first, so locally-saved artists/albums are surfaced even when
                    // the server misses them.
                    when (filter) {
                        SearchFilter.FILTER_ARTIST -> {
                            val localArtists = database.searchArtists(query).first()
                                .filter { if (hideExplicit) !it.artist.isLocal else true }
                            items.addAll(
                                localArtists.map { artist ->
                                    com.metrolist.innertube.models.ArtistItem(
                                        id = artist.id,
                                        title = artist.title,
                                        thumbnail = artist.thumbnailUrl,
                                        shuffleEndpoint = null,
                                        radioEndpoint = null,
                                    )
                                }
                            )
                        }
                        SearchFilter.FILTER_ALBUM -> {
                            val localAlbums = database.searchAlbums(query).first()
                                .filter { if (hideExplicit) !it.album.explicit else true }
                            items.addAll(
                                localAlbums.map { album ->
                                    com.metrolist.innertube.models.AlbumItem(
                                        browseId = album.id,
                                        playlistId = album.album.playlistId ?: album.id,
                                        title = album.title,
                                        artists = album.artists.map { artist ->
                                            com.metrolist.innertube.models.Artist(
                                                name = artist.name,
                                                id = artist.id,
                                            )
                                        },
                                        year = album.album.year,
                                        thumbnail = album.thumbnailUrl ?: "",
                                    )
                                }
                            )
                        }
                        else -> {} // Songs/videos/playlists: online only (local songs are local search)
                    }

                    // Already whitelist-scoped; Zemer has no pagination (continuation == null).
                    items.addAll(zemerRepo.filtered(query, filter, zemerSearchOptions(context)).items)
                    ItemsPage(items.distinctBy { it.id }, null)
                }
            }

        result.onSuccess { itemsPage ->
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

    private companion object {
        const val SEARCH_TRACKED_KEY = "searchTracked"

        /** The `search` event's `provider` wire value — single-engine now, always "zemer". */
        const val TRACKED_PROVIDER = "zemer"
    }
}
