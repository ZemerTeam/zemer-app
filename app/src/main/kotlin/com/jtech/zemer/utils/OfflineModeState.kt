package com.jtech.zemer.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide mirror of the manual offline-mode preference (`OfflineModeKey`), seeded by the
 * DataStore collector in `App.observeSettingsChanges()` — the [ContentFilterState] pattern. When
 * [enabled], browse surfaces show downloaded content only and content-discovery network calls are
 * suppressed at their source (ViewModel fetch entry points, queue factories), so an offline session
 * produces no failing requests and no error noise. Reads are synchronous and non-blocking
 * (`StateFlow.value`), safe from any thread.
 */
object OfflineModeState {
    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state

    val enabled: Boolean get() = _state.value

    fun set(value: Boolean) {
        _state.value = value
    }
}
