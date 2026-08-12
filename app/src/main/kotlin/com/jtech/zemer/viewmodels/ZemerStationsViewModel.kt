package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.ZemerStation
import com.jtech.zemer.utils.OfflineModeState
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
 * fail-soft convention). [refresh] runs on load AND on a 60s ticker while the row is ON SCREEN
 * (an operator cadence upgrade over the handoff's once-per-load default — a stale now-playing line
 * reads as broken; the ticker is LIFECYCLE-scoped via repeatOnLifecycle(RESUMED), so it suspends the
 * moment the app is backgrounded, and `/stations` is a single cheap read). No content flags exist for stations (pools are pre-filtered
 * server-side), so there is no flag-change re-fetch.
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
                // Manual offline mode: a synchronized live broadcast can't be served offline — the
                // RADIO tab is hidden and the ticker stopped; clear so nothing stale lingers.
                if (OfflineModeState.enabled) {
                    _stations.value = emptyList()
                    return@withLock
                }
                // Failure keeps the previous cards (or hides a never-loaded row); a live broadcast
                // catalog is not worth an error state on Home.
                runCatching { repository.stations() }.onSuccess { _stations.value = it }
            }
        }
    }
}

/** On-screen refresh cadence for the station cards' now-playing line (see the class KDoc). */
const val STATION_ROW_REFRESH_MS = 60_000L
