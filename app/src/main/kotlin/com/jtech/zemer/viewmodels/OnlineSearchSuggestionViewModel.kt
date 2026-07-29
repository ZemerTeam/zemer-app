package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.models.YTItem
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.SearchHistory
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
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
            query
                .flatMapLatest { query ->
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
