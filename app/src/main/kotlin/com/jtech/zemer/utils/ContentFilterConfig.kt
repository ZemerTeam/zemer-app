package com.jtech.zemer.utils

/**
 * In-memory representation of content filter settings pulled from DataStore.
 */
data class ContentFilterConfig(
    val filtersEnabled: Boolean = true,
    val allowFemaleSingers: Boolean = false,
    val promoteChasidish: Boolean = false,
)

object ContentFilterState {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ContentFilterConfig())
    val state: kotlinx.coroutines.flow.StateFlow<ContentFilterConfig> = _state

    var current: ContentFilterConfig
        get() = _state.value
        internal set(value) {
            _state.value = value
        }
}
