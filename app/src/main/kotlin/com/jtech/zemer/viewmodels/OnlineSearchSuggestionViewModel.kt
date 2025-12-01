package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.WhitelistCache
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else {
                        val filters = ContentFilterState.state.value
                        val whitelist = WhitelistCache.allowedEntries(database, filters)

                        if (filters.filtersEnabled) {
                            val matchingArtists = whitelist
                                .filter { it.artistName.contains(query, ignoreCase = true) }
                                .take(10)
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
                            database
                                .searchHistory(query)
                                .map { it.take(3) }
                                .map { history ->
                                    SearchSuggestionViewState(
                                        history = history,
                                        suggestions = history.map { it.query },
                                        items = matchingArtists
                                    )
                                }
                        } else {
                            val result = YouTube.searchSuggestions(query).getOrNull()
                            val hideExplicit = context.dataStore.get(HideExplicitKey, false)

                            val filteredItems = result
                                ?.recommendedItems
                                ?.distinctBy { it.id }
                                ?.filterExplicit(hideExplicit)
                                ?.filterWhitelisted(database)
                                .orEmpty()

                            val whitelistedArtists = database.getAllWhitelistedArtists().map { entries ->
                                entries.map { it.artistName.lowercase() }
                            }

                            database
                                .searchHistory(query)
                                .map { it.take(3) }
                                .flatMapLatest { history ->
                                    whitelistedArtists.map { artistNames ->
                                        SearchSuggestionViewState(
                                            history = history,
                                            suggestions =
                                            result
                                                ?.queries
                                                ?.filter { suggestionQuery ->
                                                    val lowerQuery = suggestionQuery.lowercase()
                                                    artistNames.any { artistName ->
                                                        lowerQuery.contains(artistName)
                                                    }
                                                }
                                                ?.filter { suggestionQuery ->
                                                    history.none { it.query == suggestionQuery }
                                                }.orEmpty(),
                                            items = filteredItems,
                                        )
                                    }
                                }
                        }
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
