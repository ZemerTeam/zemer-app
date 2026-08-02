package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Music Status" home row's ViewModel (the LatestReleases / Stations isolation pattern): the
 * JewishStatus feed is a THIRD-PARTY service the app can't guarantee is up, so a fetch failure can
 * never affect the rest of Home — the row just stays empty and HomeScreen hides it. Creators + the
 * seen set come from the shared [StatusesRepository] (single source, also read by the story viewer).
 */
@HiltViewModel
class ZemerStatusesViewModel @Inject constructor(
    private val repository: StatusesRepository,
) : ViewModel() {
    val creators: StateFlow<List<StatusCreator>> = repository.creators

    // The persisted "seen" set — drives each circle's read/unread ring, live-updating as statuses are
    // viewed and returned from.
    val seenPostIds: StateFlow<Set<String>> =
        repository.seen.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    /**
     * Refresh the row. [force] (pull-to-refresh) always re-fetches; a plain call (screen open) re-fetches
     * only a platform whose cache has gone stale. Fail-soft in the repository (a failure keeps the list).
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { repository.refreshCreators(force) }
    }
}
