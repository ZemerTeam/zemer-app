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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.innertube.pages.SearchSummary
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.SearchProviderKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.ItemsPage
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.search.SearchProvider
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.enumPreferenceFlow
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    savedStateHandle: SavedStateHandle,
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

    /** The engine in effect for the current load; updated reactively from the preference. */
    private var provider: SearchProvider = SearchProvider.ZEMER
    private var lastProvider: SearchProvider? = null

    init {
        viewModelScope.launch {
            combine(
                filter,
                enumPreferenceFlow(context, SearchProviderKey, SearchProvider.ZEMER),
            ) { selectedFilter, selectedProvider -> selectedFilter to selectedProvider }
                // collectLatest so a provider/filter change cancels an in-flight (up to 8s) request
                // instead of queueing behind it — otherwise the toggle appears frozen.
                .collectLatest { (selectedFilter, selectedProvider) ->
                    provider = selectedProvider
                    // Toggling the engine invalidates cached results so the new one reloads in place.
                    if (lastProvider != null && lastProvider != selectedProvider) {
                        summaryPage = null
                        viewStateMap.clear()
                        filterLoading.clear()
                        filterError.clear()
                        summaryError.value = null
                    }
                    lastProvider = selectedProvider
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
                    if (provider == SearchProvider.ZEMER) {
                        // Zemer results are already whitelist-scoped server-side; do not re-filter.
                        zemerRepo.summary(query, zemerSearchOptions(context)).summaries
                    } else {
                        val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                        val ytResults = YouTube.searchSummary(query).getOrNull()
                        val ytFilteredPage = ytResults?.filterExplicit(hideExplicit)

                        ytFilteredPage?.summaries
                            ?.mapNotNull { summary ->
                                val filteredItems = summary.items.filterWhitelisted(database)
                                if (filteredItems.isEmpty()) {
                                    null
                                } else {
                                    summary.copy(items = filteredItems)
                                }
                            }
                            .orEmpty()
                    }
                }
            }

        result.onSuccess { summaries ->
            summaryPage = SearchSummaryPage(
                summaries = summaries
            )

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

                    // Local DB results first — for both engines, so locally-saved artists/albums are
                    // surfaced regardless of which online engine is active.
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

                    if (provider == SearchProvider.ZEMER) {
                        // Already whitelist-scoped; Zemer has no pagination (continuation == null).
                        items.addAll(zemerRepo.filtered(query, filter, zemerSearchOptions(context)).items)
                        ItemsPage(items.distinctBy { it.id }, null)
                    } else {
                        val ytResult = YouTube.search(query, filter).getOrNull()
                        if (ytResult != null) {
                            items.addAll(
                                ytResult.items
                                    .filterExplicit(hideExplicit)
                                    .filterWhitelisted(database)
                            )
                        }
                        ItemsPage(items.distinctBy { it.id }, ytResult?.continuation)
                    }
                }
            }

        result.onSuccess { itemsPage ->
            viewStateMap[key] = itemsPage
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

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                filterLoading[filter] = true
                filterError[filter] = null
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: run {
                        filterLoading[filter] = false
                        return@launch
                    }
                val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + searchResult.items)
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterWhitelisted(database),
                    searchResult.continuation
                )
                filterLoading[filter] = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Read the engine preference fresh: a refresh fired right after toggling engines must use
            // the new one, not the snapshot the reactive collector may not have written to `provider`
            // yet (otherwise the retry briefly reloads the old engine's results under the new toggle).
            provider = enumPreferenceFlow(context, SearchProviderKey, SearchProvider.ZEMER).first()
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

    private suspend fun getAllowedMatches(query: String, limit: Int): List<com.metrolist.innertube.models.YTItem> {
        val filters = ContentFilterState.state.value
        return WhitelistCache.allowedEntries(database, filters)
            .filter { it.artistName.contains(query, ignoreCase = true) }
            .shuffled()
            .take(limit)
            .map { entry ->
                com.metrolist.innertube.models.ArtistItem(
                    id = entry.artistId,
                    title = entry.artistName,
                    thumbnail = null,
                    channelId = null,
                    playEndpoint = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null
                )
            }
    }
}
