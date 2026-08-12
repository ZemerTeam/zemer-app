package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerCuratedPlaylist
import com.jtech.zemer.search.ZemerSearchOptions
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterConfig
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
 * True when a response fetched with [options] may still be shown under the live filter [config] —
 * the guard that upholds "a list fetched under one flag set is never shown under another" against a
 * slow fetch racing a flag change. Pure, so the rule is unit-testable.
 *
 * Deliberately compares ONLY [ZemerSearchOptions.allowFemale]: [ZemerSearchOptions.blockVideos] is a
 * pinned constant `false` ([com.jtech.zemer.search.zemerSearchOptions] — the server always sends
 * videos so the client can render them as audio; blocking is enforced client-side, not by the
 * request). Comparing it against the live [ContentFilterConfig.blockVideos] would make this guard
 * PERMANENTLY false for every "Block videos" user — every response silently dropped, every screen
 * stuck loading — since a constant can never equal a live `true` setting. Guard against reintroducing
 * that comparison if [ZemerSearchOptions] ever gains a field that CAN vary with `blockVideos` again.
 */
internal fun zemerOptionsStillCurrent(options: ZemerSearchOptions, config: ContentFilterConfig): Boolean =
    options.allowFemale == config.allowFemaleSingers

/**
 * Backs the hand-curated "Zemer Playlists" Home section, deliberately separate from [HomeViewModel]
 * (like [LatestReleasesViewModel]) so a failed server fetch can never affect the rest of Home.
 *
 * The list is server-rendered for the user's content-filter flags and shown as received (editorial
 * order, no client re-filtering). Empty is a normal state — the section hides. Refresh triggers:
 * the consuming screens call [refresh] on each screen-open (the endpoint's plain-refetch freshness
 * contract — the initial flag emission is dropped so a screen open is exactly ONE fetch), plus a
 * re-fetch whenever the content-filter flags change. Fetches are serialized behind a [Mutex] and a
 * response whose flags are no longer current is dropped ([zemerOptionsStillCurrent]), so a slow
 * stale-flag response can never overwrite a fresher filtered list. On failure the previous list is
 * kept.
 */
@HiltViewModel
class ZemerCuratedPlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _playlists = MutableStateFlow<List<ZemerCuratedPlaylist>>(emptyList())
    val playlists: StateFlow<List<ZemerCuratedPlaylist>> = _playlists.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        reloadOnContentFlagChange { refreshNow() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        // Manual offline mode: curated playlists are server browse content — clear so the section
        // hides, no fetch (and no offline-subset fallback; offline mode shows downloads only).
        if (OfflineModeState.enabled) {
            _playlists.value = emptyList()
            return@withLock
        }
        val options = zemerSearchOptions(context)
        runCatching { repository.curatedPlaylists(options) }
            .onSuccess { fetched ->
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    _playlists.value = fetched
                }
            }
            .onFailure { reportException(it) }
    }
}
