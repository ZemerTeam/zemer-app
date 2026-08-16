package com.jtech.zemer.sync.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Content filter configuration for a device
 */
@IgnoreExtraProperties
data class DeviceContentFilters(
    val enableContentFilters: Boolean = true,
    val allowFemaleSingers: Boolean = false,
    val blockVideos: Boolean = false,
    // Nullable so an UNSET podcast field (a doc written before podcast-blocking shipped, or by an authority
    // who never touched it) is distinguishable from an explicit false. When unset, toConfig() derives it
    // from blockVideos — so a sync-account video-blocker is locked out of podcasts too. Once a client
    // writes an explicit value it is respected.
    val blockPodcasts: Boolean? = null,
    val femalePasscodeHash: String? = null
) {
    companion object {
    }

    /**
     * Convert from local ContentFilterConfig to Firestore model
     */
    fun fromConfig(config: com.jtech.zemer.utils.ContentFilterConfig): DeviceContentFilters {
        return DeviceContentFilters(
            enableContentFilters = config.filtersEnabled,
            allowFemaleSingers = config.allowFemaleSingers,
            blockVideos = config.blockVideos,
            blockPodcasts = config.blockPodcasts,
            femalePasscodeHash = config.femalePasscodeHash
        )
    }

    /**
     * Convert to local ContentFilterConfig. An unset blockPodcasts is coupled to blockVideos so a
     * video-blocking account restores with podcasts blocked without needing the server field backfilled.
     */
    fun toConfig(): com.jtech.zemer.utils.ContentFilterConfig {
        return com.jtech.zemer.utils.ContentFilterConfig(
            filtersEnabled = enableContentFilters,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
            blockPodcasts = blockPodcasts ?: blockVideos,
            femalePasscodeHash = femalePasscodeHash
        )
    }
}

/**
 * Device information metadata
 */
@IgnoreExtraProperties
data class DeviceMetadata(
    val deviceName: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val appVersion: String = "",
    val firstSeen: Date? = null,
    val lastSeen: Date? = null
) {
    companion object {
    }

}

/**
 * Simple device data for storing in user document
 */
@IgnoreExtraProperties
data class UserDeviceData(
    val deviceId: String = "",
    val deviceInfo: DeviceMetadata = DeviceMetadata(),
    val contentFilters: DeviceContentFilters = DeviceContentFilters(),
    val createdAt: Date? = null,
    val lastSyncTime: Long = -1
)

/**
 * Firestore entity representing content filter preferences for a user with multiple devices.
 * All devices are stored in one document per user for easy browsing and searching.
 */
@IgnoreExtraProperties
data class DevicePreferencesEntity(
    val userId: String = "",
    val userEmail: String = "",
    val contentFilters: DeviceContentFilters = DeviceContentFilters(),
    val deviceInfo: DeviceMetadata = DeviceMetadata(),
    val devices: List<UserDeviceData> = emptyList(), // Array of all user devices
    val createdAt: Date? = null,
    val updatedAt: Date? = null
) {
    companion object {
    }
}