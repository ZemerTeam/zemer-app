package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.PodcastGenreSection
import com.jtech.zemer.search.podcastGenreSections
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the podcast-genre catalog screen: `/podcast-genres` grouped into the server-owned kind
 * sections ([podcastGenreSections] — flat when the server sends no `kinds`). Same fetch discipline as
 * [ZemerGenreCatalogViewModel]: a fresh fetch per screen open, a re-fetch on content-flag change, and
 * a response fetched under stale flags is dropped.
 */
@HiltViewModel
class PodcastGenreCatalogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val sections: List<PodcastGenreSection>) : UiState
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
            runCatching { repository.podcastGenres(options) }
                .onSuccess { catalog ->
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = UiState.Loaded(podcastGenreSections(catalog))
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
