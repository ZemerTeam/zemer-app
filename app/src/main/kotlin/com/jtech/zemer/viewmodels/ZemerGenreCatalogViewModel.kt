package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.GenreKind
import com.jtech.zemer.search.ZemerGenreSummary
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.genresByKind
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
 * Backs the genre catalog screen: `/genres` grouped by kind (Styles, then Occasions), each bucket in
 * the server's most-populated-first order. Non-music genres never reach the screen ([genresByKind]
 * drops them — handoff rule: spoken-word slugs exist to be excluded, not featured). Same fetch
 * discipline as the curated playlists: a fresh fetch per screen open, a re-fetch on content-flag
 * change, and a response fetched under stale flags is dropped ([zemerOptionsStillCurrent]).
 */
@HiltViewModel
class ZemerGenreCatalogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val groups: Map<GenreKind, List<ZemerGenreSummary>>) : UiState
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
            runCatching { repository.genres(options) }
                .onSuccess { genres ->
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = UiState.Loaded(genresByKind(genres))
                    }
                }
                .onFailure {
                    reportException(it)
                    // Same still-current guard as the success path: a stale-flag failure is already
                    // superseded by the collector's reload and must not clobber the fresher state.
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = UiState.Error
                    }
                }
        }
    }
}
