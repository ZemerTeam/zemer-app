package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.GENRE_SECTION_SINGLES
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.AlbumItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs a genre's per-section see-all screen (its full Albums or Singles grid). Pages the server's
 * `facet` endpoint — which returns ONE list in full (the detail screen only loads the top-20 shelf)
 * — accumulating every page until `nextOffset` is null, then shows the complete list. `section` is
 * [com.jtech.zemer.search.GENRE_SECTION_ALBUMS]/[GENRE_SECTION_SINGLES] (both map to the
 * album-shaped facet). Same fetch discipline as the detail: flag re-fetch, stale-flag drop, 404
 * backs out.
 */
@HiltViewModel
class ZemerGenreSectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val genreId = savedStateHandle.get<String>("genreId")!!
    private val section = savedStateHandle.get<String>("section").orEmpty()
    val isSingles = section == GENRE_SECTION_SINGLES

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val albums: List<AlbumItem>) : UiState

        /** 404: the genre is gone or fully filtered out for this viewer — back out. */
        data object NotFound : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        reloadOnContentFlagChange { load() }
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val options = zemerSearchOptions(context)
            runCatching {
                val accumulated = mutableListOf<AlbumItem>()
                var offset = 0
                // Walk every page. Bounded by nextOffset (and a hard page cap as a runaway guard);
                // an album/single facet is at most a few hundred rows = 1-2 pages at limit 200.
                repeat(MAX_PAGES) {
                    val page = repository.genreFacet(genreId, section, options, offset)
                        ?: return@runCatching null
                    accumulated += page.albums
                    val next = page.nextOffset ?: return@runCatching accumulated.distinctBy { it.browseId }
                    offset = next
                }
                accumulated.distinctBy { it.browseId }
            }
                .onSuccess { albums ->
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = when (albums) {
                            null -> UiState.NotFound
                            else -> UiState.Loaded(albums)
                        }
                    }
                }
                .onFailure {
                    reportException(it)
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = UiState.Error
                    }
                }
        }
    }

    private companion object {
        /** Runaway guard: at limit 200 this covers 2000 rows, far beyond any real facet. */
        const val MAX_PAGES = 10
    }
}
