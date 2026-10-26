package com.jtech.zemer.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.utils.reportException
import com.jtech.zemer.sync.models.DevicePreferencesEntity
import com.jtech.zemer.sync.models.DeviceContentFilters
import com.jtech.zemer.sync.models.DeviceMetadata
import com.jtech.zemer.sync.models.UserDeviceData
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.DeviceIdGenerator
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jtech.zemer.constants.EnableContentFiltersKey
import com.jtech.zemer.constants.AllowFemaleSingersKey
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.BlockPodcastsKey
import com.jtech.zemer.constants.FemalePasscodeHashKey
import com.jtech.zemer.constants.ContentFiltersAutoRestoredKey
import com.jtech.zemer.constants.ContentFiltersRestoredEmailKey
import com.jtech.zemer.constants.ContentFiltersLockedKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import com.jtech.zemer.di.SyncDataStore
import com.jtech.zemer.di.MainDataStore
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private fun ContentFilterConfig.toDeviceContentFilters(): DeviceContentFilters {
    return DeviceContentFilters(
        enableContentFilters = filtersEnabled,
        allowFemaleSingers = allowFemaleSingers,
        blockVideos = blockVideos,
        blockPodcasts = blockPodcasts,
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
 * Repository for managing user preferences sync between local DataStore and the content server's `/prefs`.
 * Handles device-specific content filter preferences with server-wins conflict resolution.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ContentServerApi,
    @SyncDataStore private val syncDataStore: DataStore<Preferences>,
    @MainDataStore private val mainDataStore: DataStore<Preferences>,
    private val authManager: UserAuthManager,
    private val deviceIdGenerator: DeviceIdGenerator
) {
    // The account email of the last device-id restore, saved by [markAutoRestored] as the restore label.
    @Volatile
    private var lastRestoredEmail: String? = null

    // The server keys the document by the ID token's uid.
    private suspend fun <T> withIdToken(block: suspend (String) -> T): T =
        withTokenRetry({ authManager.getIdToken(forceRefresh = it).getOrThrow() }, block)

    /**
     * Classify sync errors for better debugging
     */
    private fun classifySyncError(e: Exception): String {
        return when {
            e is ContentServerException -> {
                when (e.status) {
                    400 -> "Rejected by server"
                    401 -> "User not authenticated"
                    404 -> "Document not found"
                    429 -> "Rate limited"
                    in 500..599 -> "Server unavailable"
                    else -> "Server error: ${e.status}"
                }
            }
            e is io.ktor.client.plugins.HttpRequestTimeoutException -> "Request timeout"
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
     * Fetch device preferences from the server (single document per user with a devices array)
     */
    suspend fun fetchDevicePreferences(): Result<ContentFilterConfig> {
        return try {
            val userId = authManager.currentUserId
                ?: return Result.failure(Exception("User not authenticated"))

            val deviceId = deviceIdGenerator.getDeviceId()

            // Fetch the single document keyed by the token's UID
            val entity = withIdToken { api.getPrefs(it) }

            if (entity != null) {
                // Find current device in devices array, fallback to main contentFilters
                val deviceData = entity.devices.find { it.deviceId == deviceId }
                val config = deviceData?.contentFilters?.toConfig() ?: entity.contentFilters.toConfig()

                updateLastSyncTime()

                storeDeviceId(deviceId)

                Result.success(config)
            } else {
                Result.failure(Exception("No user preferences found"))
            }
        } catch (e: Exception) {
            val errorClassification = classifySyncError(e)
            Log.e("ZemerSync", "Fetch preferences failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
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

            // The server looks this device ID up in every account's devices array
            val restore = api.devicePrefs(deviceId)
            val foundConfig = restore?.contentFilters?.toConfig()
            lastRestoredEmail = restore?.userEmail

            if (foundConfig != null) {
                Log.d("ZemerSync", "Found device in an account document")
                updateLastSyncTime()

                storeDeviceId(deviceId)

                Result.success(foundConfig)
            } else {
                Log.d("ZemerSync", "No device preferences found for device ID: $deviceId")
                Result.failure(Exception("No device preferences found"))
            }
        } catch (e: Exception) {
            val errorClassification = classifySyncError(e)
            Log.e("ZemerSync", "Auto-restore fetch failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
            reportException(e)
            Result.failure(e)
        }
    }

    /**
     * Upload device preferences to the server
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
            val existingPrefs = withIdToken { api.getPrefs(it) }

            if (existingPrefs != null) {
                // Update existing document - add/update this device
                val updatedDevices = existingPrefs.devices.filter { it.deviceId != deviceId } + currentDeviceData

                val updatedDoc = existingPrefs.copy(
                    devices = updatedDevices,
                    updatedAt = Date()
                )

                withIdToken { api.putPrefs(it, updatedDoc) }

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

                withIdToken { api.putPrefs(it, newDoc) }

                Log.d("ZemerSync", "Created new document for user: $userId")
            }

            Log.d("ZemerSync", "Successfully uploaded to: devicePreferences/$userId")

            updateLastSyncTime()

            storeDeviceId(deviceId)

            Result.success(Unit)
        } catch (e: Exception) {
            val errorClassification = classifySyncError(e)
            Log.e("ZemerSync", "Upload failed: $errorClassification")
            Log.e("ZemerSync", "Original error: ${e.message}")
            reportException(e)
            Result.failure(e)
        }
    }

    /**
     * Update existing device preferences on the server
     */
    suspend fun updateDevicePreferences(config: ContentFilterConfig): Result<Unit> {
        return try {
            // No email needed for updating preferences - we use UID as document ID
            val deviceId = deviceIdGenerator.getDeviceId()

            // Fetch the user document and update the specific device in the devices array
            val entity = withIdToken { api.getPrefs(it) }

            if (entity != null) {
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

                withIdToken { api.putPrefs(it, entity.copy(devices = updatedDevices, updatedAt = Date())) }
            } else {
                // Document doesn't exist, create it
                uploadDevicePreferences(config)
            }

            updateLastSyncTime()

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
            // No email needed - we use UID as document ID
            val currentDeviceId = deviceIdGenerator.getDeviceId()

            val entity = withIdToken { api.getPrefs(it) }

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
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isSyncEnabled(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[syncEnabledKey] ?: true
        }.first()
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        syncDataStore.edit { preferences ->
            preferences[syncEnabledKey] = enabled
        }
    }

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

            // Get current local configuration from DataStore
            val prefs = mainDataStore.data.first()
            val currentConfig = ContentFilterConfig(
                filtersEnabled = prefs[EnableContentFiltersKey] ?: true,
                allowFemaleSingers = prefs[AllowFemaleSingersKey] ?: false,
                blockVideos = prefs[BlockVideosKey] ?: false,
                blockPodcasts = prefs[BlockPodcastsKey] ?: false,
                femalePasscodeHash = prefs[FemalePasscodeHashKey]
            )

            // Check if device preferences exist
            val existingPrefs = fetchDevicePreferences()

            if (existingPrefs.isSuccess) {
                updateDevicePreferences(currentConfig)
            } else {
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

    private suspend fun updateLastSyncTime() {
        syncDataStore.edit { preferences ->
            preferences[lastSyncTimeKey] = System.currentTimeMillis()
        }
    }

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
            preferences[BlockPodcastsKey] = config.blockPodcasts
            config.femalePasscodeHash?.let { hash ->
                preferences[FemalePasscodeHashKey] = hash
            }
        }
    }

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
     * Mark preferences as auto-restored and save the email
     */
    suspend fun markAutoRestored(config: com.jtech.zemer.utils.ContentFilterConfig) {
        try {
            // The email of the account the device-id restore just found
            val userEmail = lastRestoredEmail

            syncDataStore.edit { preferences ->
                preferences[ContentFiltersAutoRestoredKey] = true
                preferences[ContentFiltersRestoredEmailKey] = userEmail ?: ""
                preferences[ContentFiltersLockedKey] = true // Auto-lock when restored
            }
        } catch (e: Exception) {
            Log.d("ZemerSync", "Error marking auto-restored: ${e.message}")
        }
    }

    suspend fun isAutoRestored(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[ContentFiltersAutoRestoredKey] ?: false
        }.first()
    }

    suspend fun getRestoredEmail(): String? {
        return syncDataStore.data.map { preferences ->
            val email = preferences[ContentFiltersRestoredEmailKey]
            if (email.isNullOrEmpty()) null else email
        }.first()
    }

    suspend fun isLocked(): Boolean {
        return syncDataStore.data.map { preferences ->
            preferences[ContentFiltersLockedKey] ?: false
        }.first()
    }

    suspend fun setLocked(locked: Boolean) {
        syncDataStore.edit { preferences ->
            preferences[ContentFiltersLockedKey] = locked
        }
    }

    }

/**
 * Runs [block] with a token from [token]; a 401 (expired/revoked token) retries ONCE with a
 * force-refreshed token. Any other failure, or a second 401, propagates.
 */
internal suspend fun <T> withTokenRetry(token: suspend (forceRefresh: Boolean) -> String, block: suspend (String) -> T): T =
    try {
        block(token(false))
    } catch (e: ContentServerException) {
        if (e.status != 401) throw e
        block(token(true))
    }

data class UserDevice(
    val deviceId: String,
    val deviceName: String,
    val lastSeen: Date?,
    val firstSeen: Date?,
    val appVersion: String,
    val isCurrentDevice: Boolean
)

sealed class SyncStatus {
    object NOT_AUTHENTICATED : SyncStatus()
    object DISABLED : SyncStatus()
    object NEVER_SYNCED : SyncStatus()
    data class SYNCED(val lastSyncTime: Long) : SyncStatus()

    val isSynced: Boolean get() = this is SYNCED
}