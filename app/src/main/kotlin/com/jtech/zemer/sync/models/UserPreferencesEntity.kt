package com.jtech.zemer.sync.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Simplified user preferences entity stored in Firestore.
 * One document per user email containing all their content filter preferences.
 */
@IgnoreExtraProperties
data class UserPreferencesEntity(
    val userEmail: String = "",
    val userId: String = "",
    val contentFilters: DeviceContentFilters = DeviceContentFilters(),
    val currentDevice: DeviceMetadata = DeviceMetadata(),
    val allDevices: List<DeviceMetadata> = emptyList(),
    val isLocked: Boolean = false,
    val createdAt: Date? = null,
    val updatedAt: Date? = null
) {
    companion object {
        const val FIELD_USER_EMAIL = "userEmail"
        const val FIELD_USER_ID = "userId"
        const val FIELD_CONTENT_FILTERS = "contentFilters"
        const val FIELD_CURRENT_DEVICE = "currentDevice"
        const val FIELD_ALL_DEVICES = "allDevices"
        const val FIELD_IS_LOCKED = "isLocked"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        /**
         * Create from local ContentFilterConfig
         */
        fun fromConfig(
            config: com.jtech.zemer.utils.ContentFilterConfig,
            userEmail: String,
            userId: String,
            currentDeviceInfo: com.jtech.zemer.utils.DeviceInfo,
            existingDevices: List<DeviceMetadata> = emptyList()
        ): UserPreferencesEntity {
            val updatedDevices = existingDevices.toMutableList()

            // Update current device info or add it if not exists
            val currentDeviceMetadata = com.jtech.zemer.sync.models.DeviceMetadata(
                deviceName = currentDeviceInfo.deviceName ?: "Unknown Device",
                manufacturer = currentDeviceInfo.manufacturer ?: "Unknown",
                model = currentDeviceInfo.model ?: "Unknown",
                androidVersion = currentDeviceInfo.androidVersion ?: "Unknown",
                sdkVersion = currentDeviceInfo.sdkVersion ?: 0,
                appVersion = currentDeviceInfo.appVersion ?: "1.0",
                lastSeen = java.util.Date() // Update last seen on every sync
            )
            val existingDeviceIndex = updatedDevices.indexOfFirst { it.deviceName == currentDeviceMetadata.deviceName }

            if (existingDeviceIndex >= 0) {
                updatedDevices[existingDeviceIndex] = currentDeviceMetadata
            } else {
                updatedDevices.add(currentDeviceMetadata)
            }

            return UserPreferencesEntity(
                userEmail = userEmail,
                userId = userId,
                contentFilters = com.jtech.zemer.sync.models.DeviceContentFilters(
                enableContentFilters = config.filtersEnabled,
                allowFemaleSingers = config.allowFemaleSingers,
                blockVideos = config.blockVideos,
                femalePasscodeHash = config.femalePasscodeHash
            ),
                currentDevice = currentDeviceMetadata,
                allDevices = updatedDevices,
                isLocked = false, // Will be set explicitly when locking
                createdAt = Date(),
                updatedAt = Date()
            )
        }
    }

    /**
     * Convert to local ContentFilterConfig
     */
    fun toConfig(): com.jtech.zemer.utils.ContentFilterConfig {
        return com.jtech.zemer.utils.ContentFilterConfig(
            filtersEnabled = contentFilters.enableContentFilters,
            allowFemaleSingers = contentFilters.allowFemaleSingers,
            blockVideos = contentFilters.blockVideos,
            femalePasscodeHash = contentFilters.femalePasscodeHash,
            isSynced = true
        )
    }
}