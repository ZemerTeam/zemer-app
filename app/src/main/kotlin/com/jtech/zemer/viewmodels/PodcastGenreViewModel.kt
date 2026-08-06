package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.PodcastItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs one podcast-genre detail screen (`/podcast-genres?id=<slug>`) — a FLAT list of member shows
 * (no facets/tracklist/radio/paging, unlike music). The id is a stable server slug. Same guards as the
 * music genre detail: fresh load per open, reload on content-flag change, stale-flag responses dropped.
 * Null page = 404 (unknown slug / all filtered out) → [UiState.NotFound], the screen backs out.
 */
@HiltViewModel
class PodcastGenreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val genreId = savedStateHandle.get<String>("genreId")!!

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val title: String, val shows: List<PodcastItem>) : UiState
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
            runCatching { repository.podcastGenre(genreId, options) }
                .onSuccess { page ->
                    if (!zemerOptionsStillCurrent(options, ContentFilterState.current)) return@onSuccess
                    _state.value = if (page == null) {
                        UiState.NotFound
                    } else {
                        UiState.Loaded(page.title, page.shows)
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
}
