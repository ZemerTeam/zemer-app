package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerCuratedPlaylistPage
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
 * Backs one curated "Zemer Playlists" detail screen. The id is a stable server slug (never a YouTube
 * playlist id), fetched through the same `/zemer-playlists` endpoint as the Home section with the same
 * content-filter flags, so the opened tracklist always matches the card that was tapped.
 */
@HiltViewModel
class ZemerCuratedPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val page: ZemerCuratedPlaylistPage) : UiState

        /** 404: curation changed between the list and this open — back out and let Home refresh. */
        data object NotFound : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        // The server flags sent at fetch time are the ONLY filter on this surface — without this, a
        // detail kept alive on the back stack (or open during a remote preference sync) keeps
        // showing tracks fetched under the old flags.
        reloadOnContentFlagChange { load() }
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val options = zemerSearchOptions(context)
            runCatching { repository.curatedPlaylist(playlistId, options) }
                .onSuccess { page ->
                    // Same guard as the list VM: never publish a response fetched under flags that
                    // are no longer current (a slow fetch racing a flag change re-loads anyway).
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = if (page == null) UiState.NotFound else UiState.Loaded(page)
                    }
                }
                .onFailure {
                    reportException(it)
                    _state.value = UiState.Error
                }
        }
    }
}
