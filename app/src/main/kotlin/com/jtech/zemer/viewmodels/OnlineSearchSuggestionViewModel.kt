package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.SearchProviderKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val zemerRepo: ZemerSearchRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                query,
                enumPreferenceFlow(context, SearchProviderKey, SearchProvider.ZEMER),
            ) { typed, provider -> typed to provider }
                .flatMapLatest { (query, provider) ->
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else if (provider == SearchProvider.ZEMER) {
                        // Zemer is its OWN engine (search.zemer.io), independent of the YouTube path.
                        // History shows immediately (never blocked on the request). The engine's
                        // whitelist-scoped, cross-script results appear ONLY once the query reaches the
                        // floor (cross-script skeleton matching is itself off below 3 chars) — below it,
                        // or while a request is in flight, only history shows. There is NO on-device /
                        // local fallback by design: if the service is unreachable the user switches
                        // engines. flatMapLatest cancels an in-flight request on the next keystroke.
                        val zemerSuggestions = flow {
                            emit(null) // history renders at once, never waiting on the request
                            if (query.trim().length >= ZEMER_MIN_QUERY_LENGTH) {
                                emit(
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            zemerRepo.suggestions(query, zemerSearchOptions(context))
                                        }.onFailure {
                                            if (it is CancellationException) throw it
                                            reportException(it)
                                        }.getOrNull()
                                    },
                                )
                            }
                        }
                        database
                            .searchHistory(query)
                            .map { it.take(3) }
                            .combine(zemerSuggestions) { history, zemer ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions = zemer?.queries.orEmpty()
                                        .filter { suggestion -> history.none { it.query == suggestion } },
                                    items = zemer?.recommendedItems.orEmpty(),
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
                            val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)

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

/** Minimum query length before the Zemer engine returns as-you-type results. */
private const val ZEMER_MIN_QUERY_LENGTH = 3

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
