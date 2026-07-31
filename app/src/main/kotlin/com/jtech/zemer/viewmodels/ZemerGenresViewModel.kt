package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerGenreSummary
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Backs the Home genre-chips row, deliberately separate from [HomeViewModel] (the LatestReleases /
 * ZemerStations isolation pattern) so a `/genres` failure can never affect the rest of Home — the
 * row just hides (empty list). Raw catalog out; the row trims it with
 * [com.jtech.zemer.search.homeGenreChips]. Same fetch discipline as the curated-playlists list VM:
 * screen-open [refresh], a re-fetch on content-flag change (chip visibility/counts are
 * flag-dependent), fetches serialized behind a [Mutex], stale-flag responses dropped, failure keeps
 * the previous chips.
 */
@HiltViewModel
class ZemerGenresViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _genres = MutableStateFlow<List<ZemerGenreSummary>>(emptyList())
    val genres: StateFlow<List<ZemerGenreSummary>> = _genres.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        reloadOnContentFlagChange { refreshNow() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val options = zemerSearchOptions(context)
        runCatching { repository.genres(options) }
            .onSuccess { fetched ->
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    _genres.value = fetched
                }
            }
            .onFailure { reportException(it) }
    }
}
