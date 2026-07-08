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
import com.jtech.zemer.sync.models.UserDeviceData
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.sanitizeEmailForDocumentId
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
import com.jtech.zemer.constants.ContentFiltersAutoRestoredKey
import com.jtech.zemer.constants.ContentFiltersRestoredEmailKey
import com.jtech.zemer.constants.ContentFiltersLockedKey
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
        private const val USER_PREFERENCES_COLLECTION = "devicePreferences"
        private const val DEVICE_PREFERENCES_SUBCOLLECTION = "devicePreferences"
    }

    // Helper function to get document ID - now using Firebase UID instead of email
    private fun getDocumentId(): String {
        return authManager.currentUserId ?: throw IllegalStateException("User not authenticated")
    }

    /**
     * Classify Firebase errors for better debugging
     */
    private fun classifyFirebaseError(e: Exception): String {
        return when {
            e is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "Firestore unavailable"
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied"
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> "Document not found"
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Request timeout"
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED -> "User not authenticated"
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.CANCELLED -> "Request cancelled"
                    else -> "Firestore error: ${e.code}"
                }
            }
            e is com.google.firebase.auth.FirebaseAuthException -> {
                "Authentication error: ${e.errorCode}"
            }
            e.message?.contains("authentication", ignoreCase = true) == true -> {
                "Authentication required"
            }
            e.message?.contains("network", ignoreCase = true) == true -> {
                "Network error"
            }
            else -> e.message ?: "Unknown error"
        }
    }

    // Local preference keys for sync management (stored in sync DataStore)
    private val lastSyncTimeKey = longPreferencesKey("last_content_filter_sync_time")
    private val deviceIdKey = stringPreferencesKey("current_device_id")
    private val syncEnabledKey = booleanPreferencesKey("content_filter_sync_enabled")

    /**
     * Fetch device preferences from Firestore (NEW STRUCTURE: Single document per user with devices array)
     */
    suspend fun fetchDevicePreferences(): Result<ContentFilterConfig> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))

            val deviceId = deviceIdGenerator.getDeviceId()

            // NEW: Fetch single document by UID directly from devicePreferences collection
            val document = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(getDocumentId())
                .get()
                .await()

            if (document.exists() == true) {
                val entity = document.toObject(DevicePreferencesEntity::class.java)
                    ?: return Result.failure(Exception("Failed to parse preferences document"))

                // Find current device in devices array, fallback to main contentFilters
                val deviceData = entity.devices.find { it.deviceId == deviceId }
                val config = deviceData?.contentFilters?.toConfig() ?: entity.contentFilters.toConfig()

                // Update sync timestamp
                updateLastSyncTime()

                // Store device ID locally
                storeDeviceId(deviceId)

                Result.success(config)
            } else {
                // No preferences exist for this user yet
                Result.failure(Exception("No user preferences found"))
            }
        } catch (e: Exception) {
            val errorClassification = classifyFirebaseError(e)
            Log.e("ZemerSync", "Fetch preferences failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch device preferences by device ID only (no authentication required)
     * This searches for preferences across all users by device ID (NEW STRUCTURE)
     */
    suspend fun fetchDevicePreferencesByDeviceId(): Result<ContentFilterConfig> {
        return try {
            val deviceId = deviceIdGenerator.getDeviceId()
            Log.d("ZemerSync", "Fetching preferences for device ID: $deviceId")

            // NEW: Query all user documents in userPreferences collection for this device ID in the devices array
            // Since Firestore doesn't support array queries across all documents directly,
            // we need to fetch all user documents and search in memory
            val query = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .get()
                .await()

            Log.d("ZemerSync", "Query result size: ${query.documents.size}")

            var foundConfig: ContentFilterConfig? = null

            for (document in query.documents) {
                val entity = document.toObject(DevicePreferencesEntity::class.java)
                if (entity != null) {
                    // Search for this device in the devices array
                    val deviceData = entity.devices.find { it.deviceId == deviceId }
                    if (deviceData != null) {
                        foundConfig = deviceData.contentFilters.toConfig()
                        Log.d("ZemerSync", "Found device in user document: ${document.id}")
                        break
                    }
                }
            }

            if (foundConfig != null) {
                // Update sync timestamp
                updateLastSyncTime()

                // Store device ID locally
                storeDeviceId(deviceId)

                Result.success(foundConfig)
            } else {
                // No preferences exist for this device yet
                Log.d("ZemerSync", "No device preferences found for device ID: $deviceId")
                Result.failure(Exception("No device preferences found"))
            }
        } catch (e: Exception) {
            val errorClassification = classifyFirebaseError(e)
            Log.e("ZemerSync", "Auto-restore fetch failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
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
            val userEmail = authManager.currentUserEmail ?: userId // Use userId as fallback for anonymous users

            val deviceId = deviceIdGenerator.getDeviceId()
            val deviceInfo = deviceIdGenerator.getDeviceInfo()

            val contentFilters = config.toDeviceContentFilters()
            val deviceInfoEntity = deviceInfo.toDeviceMetadata()

            val currentDeviceData = UserDeviceData(
                deviceId = deviceId,
                deviceInfo = deviceInfoEntity,
                contentFilters = contentFilters,
                createdAt = Date(),
                lastSyncTime = System.currentTimeMillis()
            )

            Log.d("ZemerSync", "Uploading device preferences for user: $userId, email: $userEmail, device: $deviceId")
            
            // Check if user document already exists
            val existingDoc = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(getDocumentId())
                .get()
                .await()

            if (existingDoc.exists()) {
                // Update existing document - add/update this device
                val existingPrefs = existingDoc.toObject(DevicePreferencesEntity::class.java)
                val updatedDevices = (existingPrefs?.devices ?: emptyList()).filter { it.deviceId != deviceId } + currentDeviceData

                val updatedDoc = existingPrefs?.copy(
                    devices = updatedDevices,
                    updatedAt = Date()
                ) ?: throw Exception("Failed to parse existing preferences")

                firestore
                    .collection(USER_PREFERENCES_COLLECTION)
                    .document(getDocumentId())
                    .set(updatedDoc)
                    .await()

                Log.d("ZemerSync", "Updated existing document with ${updatedDevices.size} devices")
            } else {
                // Create new user document
                val newDoc = DevicePreferencesEntity(
                    userId = userId,
                    userEmail = userEmail,
                    contentFilters = contentFilters,
                    deviceInfo = deviceInfoEntity,
                    devices = listOf(currentDeviceData),
                    createdAt = Date(),
                    updatedAt = Date()
                )

                firestore
                    .collection(USER_PREFERENCES_COLLECTION)
                    .document(getDocumentId())
                    .set(newDoc)
                    .await()

                Log.d("ZemerSync", "Created new document for user: $userId")
            }

            Log.d("ZemerSync", "Successfully uploaded to: devicePreferences/$userId")

            // Update sync timestamp
            updateLastSyncTime()

            // Store device ID locally
            storeDeviceId(deviceId)

            Result.success(Unit)
        } catch (e: Exception) {
            val errorClassification = classifyFirebaseError(e)
            Log.e("ZemerSync", "Upload failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Update existing device preferences in Firestore (NEW STRUCTURE)
     */
    suspend fun updateDevicePreferences(config: ContentFilterConfig): Result<Unit> {
        return try {
            // No email needed for updating preferences - we use UID as document ID
            val deviceId = deviceIdGenerator.getDeviceId()

            // NEW: Fetch the user document and update the specific device in the devices array
            val document = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(getDocumentId())
                .get()
                .await()

            if (document.exists()) {
                val entity = document.toObject(DevicePreferencesEntity::class.java)
                    ?: return Result.failure(Exception("Failed to parse user preferences"))

                // Update the specific device in the devices array
                val updatedDevices = entity.devices.map { deviceData ->
                    if (deviceData.deviceId == deviceId) {
                        deviceData.copy(
                            contentFilters = config.toDeviceContentFilters(),
                            deviceInfo = deviceData.deviceInfo.copy(lastSeen = Date()),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    } else {
                        deviceData
                    }
                }

                // Update the document with the modified devices array
                firestore
                    .collection(USER_PREFERENCES_COLLECTION)
                    .document(getDocumentId())
                    .update(
                        "devices", updatedDevices,
                        "updatedAt", Date()
                    )
                    .await()
            } else {
                // Document doesn't exist, create it
                uploadDevicePreferences(config)
            }

            // Update sync timestamp
            updateLastSyncTime()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all devices for the current user (NEW STRUCTURE)
     */
    suspend fun getUserDevices(): Result<List<UserDevice>> {
        return try {
            // No email needed - we use UID as document ID
            val currentDeviceId = deviceIdGenerator.getDeviceId()

            // NEW: Fetch single user document by UID
            val document = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .document(getDocumentId())
                .get()
                .await()

            if (document.exists()) {
                val entity = document.toObject(DevicePreferencesEntity::class.java)
                if (entity != null) {
                    val devices = entity.devices.map { deviceData ->
                        UserDevice(
                            deviceId = deviceData.deviceId,
                            deviceName = deviceData.deviceInfo.deviceName,
                            lastSeen = deviceData.deviceInfo.lastSeen,
                            firstSeen = deviceData.deviceInfo.firstSeen,
                            appVersion = deviceData.deviceInfo.appVersion,
                            isCurrentDevice = deviceData.deviceId == currentDeviceId
                        )
                    }
                    Result.success(devices)
                } else {
                    Result.failure(Exception("Failed to parse user preferences"))
                }
            } else {
                Result.success(emptyList()) // No devices found
            }
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

            // Temporarily remove sync check to test if that's the issue
            // if (!isSyncEnabled()) {
            //     return Result.failure(Exception("Sync is disabled"))
            // }

            // Get current local configuration from DataStore
            val prefs = mainDataStore.data.first()
            val currentConfig = ContentFilterConfig(
                filtersEnabled = prefs[EnableContentFiltersKey] ?: true,
                allowFemaleSingers = prefs[AllowFemaleSingersKey] ?: false,
                blockVideos = prefs[BlockVideosKey] ?: false,
                femalePasscodeHash = prefs[FemalePasscodeHashKey]
            )

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
            Log.d("ZemerSync", "Starting performSync...")

            // Always sync from server first (server wins)
            Log.d("ZemerSync", "Attempting syncFromServer...")
            val serverResult = syncFromServer()

            if (serverResult.isSuccess) {
                Log.d("ZemerSync", "Server sync successful, returning server config")
                return serverResult
            }

            Log.d("ZemerSync", "No server preferences found, uploading local preferences...")
            val uploadResult = syncToServer()

            if (uploadResult.isSuccess) {
                Log.d("ZemerSync", "Upload successful, returning local config")
                Result.success(ContentFilterState.current)
            } else {
                Log.e("ZemerSync", "Upload failed: ${uploadResult.exceptionOrNull()?.message}")
                Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            Log.e("ZemerSync", "performSync failed", e)
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
     * Save configuration to local DataStore (main settings DataStore)
     */
    suspend fun saveToDataStore(config: ContentFilterConfig) {
        mainDataStore.edit { preferences ->
            preferences[EnableContentFiltersKey] = config.filtersEnabled
            preferences[AllowFemaleSingersKey] = config.allowFemaleSingers
            preferences[BlockVideosKey] = config.blockVideos
            config.femalePasscodeHash?.let { hash ->
                preferences[FemalePasscodeHashKey] = hash
            }
            // Note: AllowChasidishKey is excluded since chasidish is now for recommendations only
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

    /**
     * Mark preferences as auto-restored and save the email (NEW STRUCTURE)
     */
    suspend fun markAutoRestored(config: com.jtech.zemer.utils.ContentFilterConfig) {
        try {
            // Get the email from the user preferences that were just fetched
            val deviceId = deviceIdGenerator.getDeviceId()

            // NEW: Search in userPreferences collection for this device ID
            val query = firestore
                .collection(USER_PREFERENCES_COLLECTION)
                .get()
                .await()

            var userEmail: String? = null

            for (document in query.documents) {
                val entity = document.toObject(DevicePreferencesEntity::class.java)
                if (entity != null) {
                    // Search for this device in the devices array
                    val deviceData = entity.devices.find { it.deviceId == deviceId }
                    if (deviceData != null) {
                        userEmail = entity.userEmail
                        break
                    }
                }
            }

            syncDataStore.edit { preferences ->
                preferences[ContentFiltersAutoRestoredKey] = true
                preferences[ContentFiltersRestoredEmailKey] = userEmail ?: ""
                preferences[ContentFiltersLockedKey] = true // Auto-lock when restored
            }
        } catch (e: Exception) {
            Log.d("ZemerSync", "Error marking auto-restored: ${e.message}")
        }
    }

    /**
     * Check if content filters were auto-restored
     */
    suspend fun isAutoRestored(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[ContentFiltersAutoRestoredKey] ?: false
        }.first()
    }

    /**
     * Get the restored email
     */
    suspend fun getRestoredEmail(): String? {
        return syncDataStore.data.map { preferences ->
            val email = preferences[ContentFiltersRestoredEmailKey]
            if (email.isNullOrEmpty()) null else email
        }.first()
    }

    /**
     * Check if content filters are locked
     */
    suspend fun isLocked(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[ContentFiltersLockedKey] ?: false
        }.first()
    }

    /**
     * Set content filters lock state
     */
    suspend fun setLocked(locked: Boolean) {
        syncDataStore.edit { preferences ->
            preferences[ContentFiltersLockedKey] = locked
        }
    }

    /**
     * Clear auto-restore state (for testing or migration)
     */
    suspend fun clearAutoRestoreState() {
        syncDataStore.edit { preferences ->
            preferences.remove(ContentFiltersAutoRestoredKey)
            preferences.remove(ContentFiltersRestoredEmailKey)
            preferences.remove(ContentFiltersLockedKey)
        }
    }

    /**
     * Public access to deviceIdGenerator for checking device ID generation status
     */
    fun getDeviceIdGenerator(): DeviceIdGenerator {
        return deviceIdGenerator
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