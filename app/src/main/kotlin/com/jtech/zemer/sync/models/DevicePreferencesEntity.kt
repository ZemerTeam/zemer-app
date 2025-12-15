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
    val femalePasscodeHash: String? = null
) {
    companion object {
        const val FIELD_ENABLE_CONTENT_FILTERS = "enableContentFilters"
        const val FIELD_ALLOW_FEMALE_SINGERS = "allowFemaleSingers"
        const val FIELD_BLOCK_VIDEOS = "blockVideos"
        const val FIELD_FEMALE_PASSCODE_HASH = "femalePasscodeHash"
    }

    /**
     * Convert from local ContentFilterConfig to Firestore model
     */
    fun fromConfig(config: com.jtech.zemer.utils.ContentFilterConfig): DeviceContentFilters {
        return DeviceContentFilters(
            enableContentFilters = config.filtersEnabled,
            allowFemaleSingers = config.allowFemaleSingers,
            blockVideos = config.blockVideos,
            femalePasscodeHash = config.femalePasscodeHash
        )
    }

    /**
     * Convert to local ContentFilterConfig
     */
    fun toConfig(): com.jtech.zemer.utils.ContentFilterConfig {
        return com.jtech.zemer.utils.ContentFilterConfig(
            filtersEnabled = enableContentFilters,
            allowFemaleSingers = allowFemaleSingers,
            blockVideos = blockVideos,
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
        const val FIELD_DEVICE_NAME = "deviceName"
        const val FIELD_MANUFACTURER = "manufacturer"
        const val FIELD_MODEL = "model"
        const val FIELD_ANDROID_VERSION = "androidVersion"
        const val FIELD_SDK_VERSION = "sdkVersion"
        const val FIELD_APP_VERSION = "appVersion"
        const val FIELD_FIRST_SEEN = "firstSeen"
        const val FIELD_LAST_SEEN = "lastSeen"
    }

    /**
     * Convert from local DeviceInfo to Firestore model
     */
    fun fromLocalInfo(localInfo: com.jtech.zemer.utils.DeviceInfo): DeviceMetadata {
        return DeviceMetadata(
            deviceName = localInfo.deviceName,
            manufacturer = localInfo.manufacturer,
            model = localInfo.model,
            androidVersion = localInfo.androidVersion,
            sdkVersion = localInfo.sdkVersion,
            appVersion = localInfo.appVersion,
            lastSeen = Date() // Update last seen on every sync
        )
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
        const val COLLECTION_NAME = "devicePreferences"
        const val FIELD_USER_ID = "userId"
        const val FIELD_USER_EMAIL = "userEmail"
        const val FIELD_CONTENT_FILTERS = "contentFilters"
        const val FIELD_DEVICE_INFO = "deviceInfo"
        const val FIELD_DEVICES = "devices"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}