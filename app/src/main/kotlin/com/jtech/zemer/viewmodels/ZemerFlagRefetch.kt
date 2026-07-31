package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.utils.ContentFilterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * THE single encoding of "re-fetch when the content-filter flags change" for Zemer-served surfaces
 * (curated playlists, genres — every screen whose server response is computed against the flags
 * sent). One copy so extending the reactive flag set lands everywhere at once — a per-ViewModel
 * copy someone misses keeps serving content fetched under stale flags, exactly the bug this
 * pattern exists to prevent. The StateFlow replays the current value immediately; the initial load
 * is the caller's (`drop(1)`), so only actual flag CHANGES fire [onChange]. Pairs with
 * [zemerOptionsStillCurrent], which guards the response side of the same contract.
 */
internal fun ViewModel.reloadOnContentFlagChange(onChange: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        ContentFilterState.state
            .map { it.allowFemaleSingers to it.blockVideos }
            .distinctUntilChanged()
            .drop(1)
            .collect { onChange() }
    }
}
