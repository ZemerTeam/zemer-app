package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.models.YTItem
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
            kotlinx.coroutines.flow.combine(query, OfflineModeState.state) { q, offline -> q to offline }
                .flatMapLatest { (query, offlineMode) ->
                    // Manual offline mode: no history (user directive) - as-you-type suggestions come
                    // from the on-device SUBSET matcher, with recommended items scoped to DOWNLOADED
                    // content (query strings pass through; the submitted search is downloaded-only).
                    if (offlineMode) {
                        return@flatMapLatest kotlinx.coroutines.flow.flow {
                            emit(SearchSuggestionViewState())
                            if (query.trim().length >= ZEMER_MIN_QUERY_LENGTH) {
                                val suggestions = withContext(Dispatchers.IO) {
                                    runCatching {
                                        zemerRepo.subsetSuggestions(query, zemerSearchOptions(context))
                                            ?.let { scopeSuggestionsToDownloaded(it) }
                                    }.getOrNull()
                                }
                                if (suggestions != null) {
                                    emit(
                                        SearchSuggestionViewState(
                                            suggestions = suggestions.queries,
                                            items = suggestions.recommendedItems,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else {
                        // Zemer is the app's ONLY search engine (search.zemer.io). History shows
                        // immediately (never blocked on the request). The engine's whitelist-scoped,
                        // cross-script results appear ONLY once the query reaches the floor
                        // (cross-script skeleton matching is itself off below 3 chars) — below it, or
                        // while a request is in flight, only history shows. If the server is
                        // unreachable the repository falls back to the on-device snapshot when one is
                        // downloaded (see docs/offline). flatMapLatest cancels an in-flight request on
                        // the next keystroke.
                        val zemerSuggestions = flow {
                            emit(null) // history renders at once, never waiting on the request
                            // Manual offline mode: no as-you-type server suggestions; history (and
                            // the submitted search's downloaded-catalog results) carry the surface.
                            if (query.trim().length >= ZEMER_MIN_QUERY_LENGTH && !OfflineModeState.enabled) {
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
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }

    /**
     * Offline-mode scoping for subset suggestions: recommended item cards must be DOWNLOADED
     * (query strings pass through - the search they submit is downloaded-only anyway).
     */
    private suspend fun scopeSuggestionsToDownloaded(
        suggestions: com.metrolist.innertube.models.SearchSuggestions,
    ): com.metrolist.innertube.models.SearchSuggestions {
        val songIds = database.downloadedSongsByCreateDateAsc(includeVideos = true)
            .first().mapTo(HashSet()) { it.id }
        val artistIds = database.downloadedArtists(includeVideos = true)
            .first().mapTo(HashSet()) { it.id }
        val albumIds = database.downloadedAlbums(includeVideos = true)
            .first().mapTo(HashSet()) { it.id }
        return com.metrolist.innertube.models.SearchSuggestions(
            queries = suggestions.queries,
            recommendedItems = suggestions.recommendedItems.filter { item ->
                when (item) {
                    is com.metrolist.innertube.models.SongItem -> item.id in songIds
                    is com.metrolist.innertube.models.ArtistItem -> item.id in artistIds
                    is com.metrolist.innertube.models.AlbumItem -> item.browseId in albumIds
                    else -> false
                }
            },
        )
    }
}

/** Minimum query length before the Zemer engine returns as-you-type results. */
private const val ZEMER_MIN_QUERY_LENGTH = 3

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
