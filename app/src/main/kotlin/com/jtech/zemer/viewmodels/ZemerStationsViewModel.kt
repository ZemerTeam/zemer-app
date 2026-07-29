package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.ZemerStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * The "Zemer Radio" home row's own ViewModel (the LatestReleases isolation pattern): a stations
 * fetch failure can never affect the rest of Home — the row just hides (empty list, the `/home-rows`
 * fail-soft convention). One fetch per home load ([refresh]) is the settled freshness contract for
 * the cards' nowPlaying line; no timers, no polling. No content flags exist for stations (pools are
 * pre-filtered server-side), so there is no flag-change re-fetch either.
 */
@HiltViewModel
class ZemerStationsViewModel @Inject constructor(
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _stations = MutableStateFlow<List<ZemerStation>>(emptyList())
    val stations: StateFlow<List<ZemerStation>> = _stations.asStateFlow()

    private val refreshMutex = Mutex()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                // Failure keeps the previous cards (or hides a never-loaded row); a live broadcast
                // catalog is not worth an error state on Home.
                runCatching { repository.stations() }.onSuccess { _stations.value = it }
            }
        }
    }
}
