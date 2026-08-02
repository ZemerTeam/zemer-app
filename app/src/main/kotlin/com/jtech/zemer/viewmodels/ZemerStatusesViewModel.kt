package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusSeenStore
import com.jtech.zemer.statuses.StatusesRepository
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * The "Music Statuses" home row's own ViewModel (the LatestReleases / Stations isolation pattern): the
 * JewishStatus feed is a THIRD-PARTY service the app can't guarantee is up, so a fetch failure can
 * never affect the rest of Home — the row just stays empty and HomeScreen hides it. No content flags
 * (the row is shown as-is, an owner decision), so there is no flag-change re-fetch.
 */
@HiltViewModel
class ZemerStatusesViewModel @Inject constructor(
    private val repository: StatusesRepository,
    seenStore: StatusSeenStore,
) : ViewModel() {
    private val _creators = MutableStateFlow<List<StatusCreator>>(emptyList())
    val creators: StateFlow<List<StatusCreator>> = _creators.asStateFlow()

    // The persisted "seen" set — drives each circle's read/unread ring, updating live as statuses are
    // viewed and returned from.
    val seenPostIds: StateFlow<Set<String>> =
        seenStore.seen.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    private val refreshMutex = Mutex()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                // Failure keeps the previous list (or hides a never-loaded row). A statuses outage is
                // not worth an error state on Home.
                runCatching { repository.creators() }
                    .onSuccess { _creators.value = it }
                    .onFailure { reportException(it) }
            }
        }
    }
}
