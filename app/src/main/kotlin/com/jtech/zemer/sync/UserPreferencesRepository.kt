package com.jtech.zemer.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.sync.models.DevicePreferencesEntity
import com.jtech.zemer.sync.models.DeviceContentFilters
import com.jtech.zemer.sync.models.DeviceMetadata
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.DeviceIdGenerator
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.AllowChasidishKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.FemalePasscodeHashKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.jtech.zemer.di.SyncDataStore
import com.jtech.zemer.di.MainDataStore
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

// Extension functions for easier access
private fun ContentFilterConfig.toDeviceContentFilters(): DeviceContentFilters {
    return DeviceContentFilters(
        enableContentFilters = filtersEnabled,
        allowFemaleSingers = allowFemaleSingers,
        promoteChasidish = promoteChasidish,
        blockVideos = blockVideos,
        femalePasscodeHash = femalePasscodeHash
    )
}

private fun com.jtech.zemer.utils.DeviceInfo.toDeviceMetadata(): DeviceMetadata {
    return DeviceMetadata(
        deviceName = deviceName,
        manufacturer = manufacturer,
        model = model,
        androidVersion = androidVersion,
        sdkVersion = sdkVersion,
        appVersion = appVersion,
        lastSeen = Date()
    )
}

/**
 * Repository for managing user preferences sync between local DataStore and Firestore.
 * Handles device-specific content filter preferences with server-wins conflict resolution.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    @SyncDataStore private val syncDataStore: DataStore<Preferences>,
    @MainDataStore private val mainDataStore: DataStore<Preferences>,
    private val authManager: UserAuthManager,
    private val deviceIdGenerator: DeviceIdGenerator
) {
    companion object {
        private const val USER_PREFERENCES_COLLECTION = "userPreferences"
        private const val DEVICE_PREFERENCES_SUBCOLLECTION = "devicePreferences"
    }

    // Local preference keys for sync management (stored in sync DataStore)
    private val lastSyncTimeKey = longPreferencesKey("last_content_filter_sync_time")
    private val deviceIdKey = stringPreferencesKey("current_device_id")
    private val syncEnabledKey = booleanPreferencesKey("content_filter_sync_enabled")

    /**
     * Fetch device preferences from Firestore
     */
    suspend fun fetchDevicePreferences(): Result<ContentFilterConfig> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))

            val deviceId = deviceIdGenerator.getDeviceId()

            val document = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(userId)
                .collection(DEVICE_PREFERENCES_SUBCOLLECTION)
                .document(deviceId)
                .get()
                .await()

            if (document.exists() == true) {
                val devicePreferences = document.toObject(DevicePreferencesEntity::class.java)
                    ?: return Result.failure(Exception("Failed to parse device preferences"))

                val config = devicePreferences.contentFilters.toConfig()

                // Update sync timestamp
                updateLastSyncTime()

                // Store device ID locally
                storeDeviceId(deviceId)

                Result.success(config)
            } else {
                // No preferences exist for this device yet
                Result.failure(Exception("No device preferences found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch device preferences by device ID only (no authentication required)
     * This searches for preferences across all users by device ID
     */
    suspend fun fetchDevicePreferencesByDeviceId(): Result<ContentFilterConfig> {
        return try {
            val deviceId = deviceIdGenerator.getDeviceId()
            Log.d("ZemerSync", "Fetching preferences for device ID: $deviceId")

            // Query all user documents for this device ID
            val query = firestore
                .collectionGroup(DEVICE_PREFERENCES_SUBCOLLECTION)
                .whereEqualTo("deviceId", deviceId)
                .limit(1)
                .get()
                .await()

            Log.d("ZemerSync", "Query result size: ${query.documents.size}")

            if (query.documents.isNotEmpty()) {
                val document = query.documents[0]
                Log.d("ZemerSync", "Found document: ${document.id} with data: ${document.data}")

                val devicePreferences = document.toObject(DevicePreferencesEntity::class.java)
                    ?: return Result.failure(Exception("Failed to parse device preferences"))

                val config = devicePreferences.contentFilters.toConfig()
                Log.d("ZemerSync", "Parsed config: $config")

                // Update sync timestamp
                updateLastSyncTime()

                // Store device ID locally
                storeDeviceId(deviceId)

                Result.success(config)
            } else {
                // No preferences exist for this device yet
                Log.d("ZemerSync", "No device preferences found for device ID: $deviceId")
                Result.failure(Exception("No device preferences found"))
            }
        } catch (e: Exception) {
            Log.d("ZemerSync", "Error fetching device preferences: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Upload device preferences to Firestore
     */
    suspend fun uploadDevicePreferences(config: ContentFilterConfig): Result<Unit> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = authManager.currentUserEmail
                ?: return Result.failure(Exception("User email not available"))

            val deviceId = deviceIdGenerator.getDeviceId()
            val deviceInfo = deviceIdGenerator.getDeviceInfo()

            val contentFilters = config.toDeviceContentFilters()
            val deviceInfoEntity = deviceInfo.toDeviceMetadata()

            val devicePreferences = DevicePreferencesEntity(
                deviceId = deviceId,
                userId = userId,
                userEmail = userEmail,
                contentFilters = contentFilters,
                deviceInfo = deviceInfoEntity,
                createdAt = Date(),
                updatedAt = Date()
            )

            Log.d("ZemerSync", "Uploading device preferences for user: $userId, device: $deviceId")
            Log.d("ZemerSync", "Device preferences data: $devicePreferences")

            val docRef = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(userId)
                .collection(DEVICE_PREFERENCES_SUBCOLLECTION)
                .document(deviceId)

            try {
                docRef.set(devicePreferences).await()
                Log.d("ZemerSync", "Successfully uploaded to: userPreferences/$userId/devicePreferences/$deviceId")
            } catch (firestoreException: Exception) {
                Log.d("ZemerSync", "Firestore upload failed: ${firestoreException.message}")
                Log.d("ZemerSync", "Firestore error details: ${firestoreException.javaClass.simpleName}")
                firestoreException.printStackTrace()
                return Result.failure(firestoreException)
            }

            // Update sync timestamp
            updateLastSyncTime()

            // Store device ID locally
            storeDeviceId(deviceId)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.d("ZemerSync", "General upload error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Update existing device preferences in Firestore
     */
    suspend fun updateDevicePreferences(config: ContentFilterConfig): Result<Unit> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))
            val userEmail = authManager.currentUserEmail
                ?: return Result.failure(Exception("User email not available"))

            val deviceId = deviceIdGenerator.getDeviceId()

            val contentFiltersUpdate = config.toDeviceContentFilters()
            val updates = mapOf(
                "userId" to userId,
                "userEmail" to userEmail,
                "contentFilters" to contentFiltersUpdate,
                "deviceInfo.lastSeen" to Date(),
                "updatedAt" to Date()
            )

            firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(userId)
                .collection(DEVICE_PREFERENCES_SUBCOLLECTION)
                .document(deviceId)
                .update(updates)
                .await()

            // Update sync timestamp
            updateLastSyncTime()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete device preferences from Firestore
     */
    suspend fun deleteDevicePreferences(): Result<Unit> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))

            val deviceId = deviceIdGenerator.getDeviceId()

            firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(userId)
                .collection(DEVICE_PREFERENCES_SUBCOLLECTION)
                .document(deviceId)
                .delete()
                .await()

            // Clear local sync data
            clearSyncData()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all devices for the current user
     */
    suspend fun getUserDevices(): Result<List<UserDevice>> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))

            val documents = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(userId)
                .collection(DEVICE_PREFERENCES_SUBCOLLECTION)
                .get()
                .await()

            val devices = documents.mapNotNull { document ->
                val devicePreferences = document.toObject(DevicePreferencesEntity::class.java)
                UserDevice(
                    deviceId = devicePreferences.deviceId,
                    deviceName = devicePreferences.deviceInfo.deviceName,
                    lastSeen = devicePreferences.deviceInfo.lastSeen,
                    firstSeen = devicePreferences.deviceInfo.firstSeen,
                    appVersion = devicePreferences.deviceInfo.appVersion,
                    isCurrentDevice = devicePreferences.deviceId == deviceIdGenerator.getDeviceId()
                )
            }

            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if sync is enabled
     */
    suspend fun isSyncEnabled(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[syncEnabledKey] ?: true // Default to enabled
        }.first()
    }

    /**
     * Set sync enabled/disabled
     */
    suspend fun setSyncEnabled(enabled: Boolean) {
        syncDataStore.edit { preferences ->
            preferences[syncEnabledKey] = enabled
        }
    }

    /**
     * Get last sync time
     */
    suspend fun getLastSyncTime(): Long {
        return syncDataStore.data.map { preferences ->
            preferences[lastSyncTimeKey] ?: 0L
        }.first()
    }

    /**
     * Check if user has authenticated sync setup
     */
    suspend fun hasAuthenticatedSync(): Boolean {
        return authManager.isUserSignedIn &&
               deviceIdGenerator.hasDeviceId() &&
               getLastSyncTime() > 0L
    }

    /**
     * Sync current local preferences to server
     */
    suspend fun syncToServer(): Result<Unit> {
        return try {
            if (!authManager.isUserSignedIn) {
                return Result.failure(Exception("User not authenticated"))
            }

            if (!isSyncEnabled()) {
                return Result.failure(Exception("Sync is disabled"))
            }

            // Get current local configuration
            val currentConfig = ContentFilterState.current

            // Check if device preferences exist
            val existingPrefs = fetchDevicePreferences()

            if (existingPrefs.isSuccess) {
                // Update existing preferences
                updateDevicePreferences(currentConfig)
            } else {
                // Create new preferences
                uploadDevicePreferences(currentConfig)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync from server to local (server-wins)
     */
    suspend fun syncFromServer(): Result<ContentFilterConfig> {
        return try {
            if (!authManager.isUserSignedIn) {
                return Result.failure(Exception("User not authenticated"))
            }

            if (!isSyncEnabled()) {
                return Result.failure(Exception("Sync is disabled"))
            }

            val serverConfig = fetchDevicePreferences()
            if (serverConfig.isSuccess) {
                // Apply server config to local state (server wins)
                ContentFilterState.current = serverConfig.getOrThrow()

                // Save to local DataStore as well
                saveToDataStore(serverConfig.getOrThrow())
            }

            serverConfig
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform two-way sync (server-wins conflict resolution)
     */
    suspend fun performSync(): Result<ContentFilterConfig> {
        return try {
            // Always sync from server first (server wins)
            val serverResult = syncFromServer()

            if (serverResult.isSuccess) {
                return serverResult
            }

            // If no server preferences exist, upload local preferences
            syncToServer()

            Result.success(ContentFilterState.current)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update last sync timestamp
     */
    private suspend fun updateLastSyncTime() {
        syncDataStore.edit { preferences ->
            preferences[lastSyncTimeKey] = System.currentTimeMillis()
        }
    }

    /**
     * Store device ID locally
     */
    private suspend fun storeDeviceId(deviceId: String) {
        syncDataStore.edit { preferences ->
            preferences[deviceIdKey] = deviceId
        }
    }

    /**
     * Clear sync-related data
     */
    private suspend fun clearSyncData() {
        syncDataStore.edit { preferences ->
            preferences.remove(lastSyncTimeKey)
            preferences.remove(deviceIdKey)
        }
    }

    /**
     * Save configuration to local DataStore (main settings DataStore)
     */
    suspend fun saveToDataStore(config: ContentFilterConfig) {
        mainDataStore.edit { preferences ->
            preferences[EnableContentFiltersKey] = config.filtersEnabled
            preferences[AllowFemaleSingersKey] = config.allowFemaleSingers
            preferences[AllowChasidishKey] = config.promoteChasidish
            preferences[BlockVideosKey] = config.blockVideos
            config.femalePasscodeHash?.let { hash ->
                preferences[FemalePasscodeHashKey] = hash
            }
        }
    }

    /**
     * Get sync status flow
     */
    fun getSyncStatusFlow(): Flow<SyncStatus> {
        return syncDataStore.data.map { preferences ->
            val lastSync = preferences[lastSyncTimeKey] ?: 0L
            val syncEnabled = preferences[syncEnabledKey] ?: true
            val isAuthenticated = authManager.isUserSignedIn

            when {
                !isAuthenticated -> SyncStatus.NOT_AUTHENTICATED
                !syncEnabled -> SyncStatus.DISABLED
                lastSync == 0L -> SyncStatus.NEVER_SYNCED
                else -> SyncStatus.SYNCED(lastSync)
            }
        }
    }
}

/**
 * Data class representing a user's device
 */
data class UserDevice(
    val deviceId: String,
    val deviceName: String,
    val lastSeen: Date?,
    val firstSeen: Date?,
    val appVersion: String,
    val isCurrentDevice: Boolean
)

/**
 * Sealed class representing sync status
 */
sealed class SyncStatus {
    object NOT_AUTHENTICATED : SyncStatus()
    object DISABLED : SyncStatus()
    object NEVER_SYNCED : SyncStatus()
    data class SYNCED(val lastSyncTime: Long) : SyncStatus()

    val isSynced: Boolean get() = this is SYNCED
    val needsSync: Boolean get() = this == NEVER_SYNCED
}