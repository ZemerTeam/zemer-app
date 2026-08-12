package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerPodcastGenreSummary
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.OfflineModeState
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
 * Backs the Home "Podcast Genres" chips strip — the podcast twin of [ZemerGenresViewModel], isolated
 * from [HomeViewModel] (the LatestReleases/Stations pattern) so a `/podcast-genres` failure only hides
 * the strip (empty list), never the rest of Home. Screen-open [refresh], reload on content-flag change,
 * fetches serialized behind a [Mutex], stale-flag responses dropped, a failure keeps the previous chips.
 */
@HiltViewModel
class PodcastGenresHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _genres = MutableStateFlow<List<ZemerPodcastGenreSummary>>(emptyList())
    val genres: StateFlow<List<ZemerPodcastGenreSummary>> = _genres.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        reloadOnContentFlagChange { refreshNow() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        // Manual offline mode: clear so the strip hides — no fetch, and deliberately NOT the
        // offline-subset fallback (offline mode browses downloads only, never the snapshot).
        if (OfflineModeState.enabled) {
            _genres.value = emptyList()
            return@withLock
        }
        val options = zemerSearchOptions(context)
        runCatching { repository.podcastGenres(options) }
            .onSuccess { fetched ->
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    // The home strip stays flat (chips) — only the catalog screen groups by kind.
                    _genres.value = fetched.genres
                }
            }
            .onFailure { reportException(it) }
    }
}
