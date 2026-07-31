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
 * Backs a genre's per-section see-all screen (its full Albums or Singles grid). Re-fetches the
 * genre page with the server's max shelf cap ([GENRE_SECTION_K]) — the detail screen only loads the
 * default top-20 — and exposes the chosen section's releases. `section` is
 * [com.jtech.zemer.search.GENRE_SECTION_ALBUMS]/[GENRE_SECTION_SINGLES]. Same fetch discipline as
 * the detail: flag re-fetch, stale-flag drop, 404 backs out.
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
            runCatching { repository.genre(genreId, options, k = GENRE_SECTION_K) }
                .onSuccess { page ->
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = when (page) {
                            null -> UiState.NotFound
                            else -> UiState.Loaded(if (isSingles) page.singles else page.albums)
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

    companion object {
        /** The server's documented max artist/album/single shelf cap (handoff §3). */
        const val GENRE_SECTION_K = 60
    }
}
