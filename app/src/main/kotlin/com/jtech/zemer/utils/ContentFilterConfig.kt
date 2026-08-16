package com.jtech.zemer.utils

/**
 * In-memory representation of content filter settings pulled from DataStore.
 * Includes fields for sync functionality and device-specific preferences.
 */
data class ContentFilterConfig(
    val filtersEnabled: Boolean = true,
    val allowFemaleSingers: Boolean = false,
    val blockVideos: Boolean = false,
    val blockPodcasts: Boolean = false,
    val femalePasscodeHash: String? = null,
    val lastSyncTime: Long = -1L,
    val isSynced: Boolean = false
)

/**
 * Whether female singers are currently allowed: either filtering is off entirely, or the female toggle
 * is on. The single source for the podcast female gate — used by [ContentFilterState]-driven podcast
 * surfaces (library sources, the whitelisted-podcasts browse VM) so they can't drift apart.
 */
fun ContentFilterConfig.allowsFemale(): Boolean = !filtersEnabled || allowFemaleSingers

object ContentFilterState {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ContentFilterConfig())
    val state: kotlinx.coroutines.flow.StateFlow<ContentFilterConfig> = _state

    var current: ContentFilterConfig
        get() = _state.value
        internal set(value) {
            _state.value = value
        }

    /**
     * Update content filter configuration
     */
    fun updateConfig(
        filtersEnabled: Boolean? = null,
        allowFemaleSingers: Boolean? = null,
        blockVideos: Boolean? = null,
        blockPodcasts: Boolean? = null,
        femalePasscodeHash: String? = null,
        lastSyncTime: Long? = null,
        isSynced: Boolean? = null
    ) {
        val currentConfig = current
        current = currentConfig.copy(
            filtersEnabled = filtersEnabled ?: currentConfig.filtersEnabled,
            allowFemaleSingers = allowFemaleSingers ?: currentConfig.allowFemaleSingers,
            blockVideos = blockVideos ?: currentConfig.blockVideos,
            blockPodcasts = blockPodcasts ?: currentConfig.blockPodcasts,
            femalePasscodeHash = femalePasscodeHash ?: currentConfig.femalePasscodeHash,
            lastSyncTime = lastSyncTime ?: currentConfig.lastSyncTime,
            isSynced = isSynced ?: currentConfig.isSynced
        )
    }

    /**
     * Update only content filter settings (preserving sync metadata)
     */
    fun updateContentFilters(
        filtersEnabled: Boolean? = null,
        allowFemaleSingers: Boolean? = null,
        blockVideos: Boolean? = null,
        blockPodcasts: Boolean? = null,
        femalePasscodeHash: String? = null
    ) {
        updateConfig(
            filtersEnabled = filtersEnabled,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
            blockPodcasts = blockPodcasts,
            femalePasscodeHash = femalePasscodeHash
        )
    }

    /**
     * Check if any content filters are active
     */
    val hasActiveFilters: Boolean
        get() = current.filtersEnabled && (
            current.allowFemaleSingers.not() ||
            current.blockVideos ||
            current.blockPodcasts ||
            current.femalePasscodeHash != null
        )
}
