package com.jtech.zemer.viewmodels

import android.content.Context
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
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.models.ItemsPage
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.get
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = requireNotNull(savedStateHandle.get<String>("query")) {
        "query is required but was not provided in navigation arguments"
    }
    private val initialFilter = savedStateHandle.get<String>("filter")?.let { filterParam ->
        when (filterParam) {
            "albums" -> SearchFilter.FILTER_ALBUM
            "songs" -> SearchFilter.FILTER_SONG
            "artists" -> SearchFilter.FILTER_ARTIST
            "playlists" -> SearchFilter.FILTER_COMMUNITY_PLAYLIST
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

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    loadSummary(force = summaryPage == null)
                } else {
                    loadFiltered(filter, force = viewStateMap[filter.value] == null)
                }
            }
        }
    }

    private suspend fun loadSummary(force: Boolean = false) {
        if (!force && summaryPage != null) return
        isSummaryLoading.value = true
        summaryError.value = null
        YouTube
            .searchSummary(query)
            .onSuccess { page ->
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val filteredPage = page.filterExplicit(hideExplicit)
                summaryPage = SearchSummaryPage(
                    summaries = filteredPage.summaries.map { summary ->
                        val filteredItems = summary.items.filterWhitelisted(database)
                        summary.copy(items = filteredItems)
                    }
                )
            }.onFailure {
                summaryError.value = it.message ?: "Failed to load search results"
                reportException(it)
            }
        isSummaryLoading.value = false
    }

    private suspend fun loadFiltered(filter: SearchFilter, force: Boolean = false) {
        val key = filter.value
        if (!force && viewStateMap[key] != null) return
        filterLoading[key] = true
        filterError[key] = null
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        YouTube
            .search(query, filter)
            .onSuccess { result ->
                viewStateMap[key] =
                    ItemsPage(
                        result.items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterWhitelisted(database),
                        result.continuation,
                    )
            }.onFailure {
                filterError[key] = it.message ?: "Failed to load results"
                reportException(it)
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
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
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
            val currentFilter = filter.value
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
