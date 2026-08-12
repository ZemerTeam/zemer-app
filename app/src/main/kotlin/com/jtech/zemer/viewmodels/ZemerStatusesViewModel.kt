package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.HideImageStatusKey
import com.jtech.zemer.constants.HideTextStatusKey
import com.jtech.zemer.statuses.StatusContentFilter
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusesRepository
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    @ApplicationContext context: Context,
    private val repository: StatusesRepository,
) : ViewModel() {
    val creators: StateFlow<List<StatusCreator>> = repository.creators

    // The persisted "seen" set — drives each circle's read/unread ring, live-updating as statuses are
    // viewed and returned from.
    val seenPostIds: StateFlow<Set<String>> =
        repository.seen.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // The hide-text/hide-image content filter (Settings -> Appearance), so the ring only counts statuses
    // the user can actually view (a fully-hidden creator drops from the row).
    val contentFilter: StateFlow<StatusContentFilter> =
        context.dataStore.data
            .map {
                StatusContentFilter(
                    hideText = it[HideTextStatusKey] ?: true,
                    hideImage = it[HideImageStatusKey] ?: false,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                StatusContentFilter(hideText = true, hideImage = false),
            )

    /**
     * Refresh the row. [force] (pull-to-refresh) always re-fetches; a plain call (screen open) re-fetches
     * only a platform whose cache has gone stale. Fail-soft in the repository (a failure keeps the list).
     */
    fun refresh(force: Boolean = false) {
        // Manual offline mode: no third-party fetch; the row itself is render-gated in HomeScreen
        // (the repository keeps its last-good creators for when the mode turns off).
        if (OfflineModeState.enabled) return
        viewModelScope.launch(Dispatchers.IO) { repository.refreshCreators(force) }
    }
}
