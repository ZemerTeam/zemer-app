package com.jtech.zemer.utils

/**
 * In-memory representation of content filter settings pulled from DataStore.
 * Includes fields for sync functionality and device-specific preferences.
 */
data class ContentFilterConfig(
    val filtersEnabled: Boolean = true,
    val allowFemaleSingers: Boolean = false,
    val blockVideos: Boolean = false,
    val femalePasscodeHash: String? = null,
    val lastSyncTime: Long = -1L,
    val isSynced: Boolean = false
)

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
        femalePasscodeHash: String? = null,
        lastSyncTime: Long? = null,
        isSynced: Boolean? = null
    ) {
        val currentConfig = current
        current = currentConfig.copy(
            filtersEnabled = filtersEnabled ?: currentConfig.filtersEnabled,
            allowFemaleSingers = allowFemaleSingers ?: currentConfig.allowFemaleSingers,
            blockVideos = blockVideos ?: currentConfig.blockVideos,
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
        femalePasscodeHash: String? = null
    ) {
        val currentConfig = current
        updateConfig(
            filtersEnabled = filtersEnabled,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
            femalePasscodeHash = femalePasscodeHash
        )
    }

    /**
     * Update sync-related metadata
     */
    fun updateSyncMetadata(
        lastSyncTime: Long,
        isSynced: Boolean
    ) {
        updateConfig(
            lastSyncTime = lastSyncTime,
            isSynced = isSynced
        )
    }

    /**
     * Mark configuration as modified (not synced)
     */
    fun markAsModified() {
        updateConfig(isSynced = false)
    }

    /**
     * Reset to default configuration
     */
    fun resetToDefaults() {
        current = ContentFilterConfig()
    }

    /**
     * Check if configuration has been modified since last sync
     */
    val hasUnsyncedChanges: Boolean
        get() = !current.isSynced

    /**
     * Check if any content filters are active
     */
    val hasActiveFilters: Boolean
        get() = current.filtersEnabled && (
            current.allowFemaleSingers.not() ||
            current.blockVideos ||
            current.femalePasscodeHash != null
        )
}
