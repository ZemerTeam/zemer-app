package com.jtech.zemer.sync

import com.jtech.zemer.auth.AuthState
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.utils.ContentFilterConfig
import com.jtech.zemer.utils.ContentFilterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that orchestrates content filter preference synchronization.
 * Handles automatic sync on app launch, authentication, and preference changes.
 */
@Singleton
class ContentFilterSyncService @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authManager: UserAuthManager
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<Result<Unit>?>(null)
    val lastSyncResult: StateFlow<Result<Unit>?> = _lastSyncResult.asStateFlow()

    private var _isApplyingServerPreferences = false

    init {
        // Listen for authentication state changes
        serviceScope.launch {
            authManager.authStateFlow.collect { authState ->
                handleAuthStateChange(authState)
            }
        }

        // Listen for preference changes
        serviceScope.launch {
            ContentFilterState.state.collect { config ->
                handlePreferenceChange(config)
            }
        }
    }

    /**
     * Initialize sync service - called from MainActivity
     */
    fun initialize() {
        serviceScope.launch {
            Log.d("ZemerSync", "Initializing sync service")
            Log.d("ZemerSync", "User signed in: ${authManager.isUserSignedIn}")
            Log.d("ZemerSync", "Has authenticated sync: ${userPreferencesRepository.hasAuthenticatedSync()}")

            // First, try to auto-restore preferences by device ID (no auth required)
            if (!userPreferencesRepository.hasAuthenticatedSync()) {
                try {
                    Log.d("ZemerSync", "Attempting auto-restore by device ID")
                    val result = userPreferencesRepository.fetchDevicePreferencesByDeviceId()
                    if (result.isSuccess) {
                        Log.d("ZemerSync", "Auto-restore successful!")
                        _isApplyingServerPreferences = true
                        val config = result.getOrThrow()
                        Log.d("ZemerSync", "Applying config: $config")

                        // Mark as auto-restored and save the email from the device preferences
                        userPreferencesRepository.markAutoRestored(config)

                        ContentFilterState.current = config
                        userPreferencesRepository.saveToDataStore(config)
                        _isApplyingServerPreferences = false
                        return@launch
                    } else {
                        Log.d("ZemerSync", "Auto-restore failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.d("ZemerSync", "Auto-restore exception: ${e.message}")
                    e.printStackTrace()
                    // Auto-restore failed, continue with normal flow
                }
            } else {
                Log.d("ZemerSync", "Skipping auto-restore - has authenticated sync")
            }

            // If user is signed in, perform normal sync
            if (authManager.isUserSignedIn) {
                Log.d("ZemerSync", "User is signed in, performing normal sync")
                performInitialSync()
            } else {
                Log.d("ZemerSync", "User is not signed in, skipping normal sync")
            }
        }
    }

    /**
     * Perform manual sync - push current preferences to server
     */
    suspend fun performManualSync(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.SYNCING

                val result = userPreferencesRepository.syncToServer()

                _syncState.value = SyncState.IDLE
                _lastSyncResult.value = result

                result
            } catch (e: Exception) {
                _syncState.value = SyncState.ERROR(e.message ?: "Unknown error")
                _lastSyncResult.value = Result.failure(e)
                Result.failure(e)
            }
        }
    }

    /**
     * Pull preferences from server and apply locally
     */
    suspend fun pullFromServer(): Result<ContentFilterConfig> {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.SYNCING
                _isApplyingServerPreferences = true

                val result = userPreferencesRepository.syncFromServer()

                _syncState.value = SyncState.IDLE
                _lastSyncResult.value = result.map { }

                result
            } catch (e: Exception) {
                _syncState.value = SyncState.ERROR(e.message ?: "Unknown error")
                _lastSyncResult.value = Result.failure(e)
                Result.failure(e)
            } finally {
                _isApplyingServerPreferences = false
            }
        }
    }

    /**
     * Sync to server only
     */
    suspend fun syncToServer(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.SYNCING

                val result = userPreferencesRepository.syncToServer()

                _syncState.value = SyncState.IDLE
                _lastSyncResult.value = result

                result
            } catch (e: Exception) {
                _syncState.value = SyncState.ERROR(e.message ?: "Unknown error")
                _lastSyncResult.value = Result.failure(e)
                Result.failure(e)
            }
        }
    }

    /**
     * Sync from server only
     */
    suspend fun syncFromServer(): Result<ContentFilterConfig> {
        return withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.SYNCING

                val result = userPreferencesRepository.syncFromServer()

                _syncState.value = SyncState.IDLE
                _lastSyncResult.value = result.map { }

                result
            } catch (e: Exception) {
                _syncState.value = SyncState.ERROR(e.message ?: "Unknown error")
                _lastSyncResult.value = Result.failure(e)
                Result.failure(e)
            }
        }
    }

    /**
     * Enable/disable sync
     */
    suspend fun setSyncEnabled(enabled: Boolean) {
        userPreferencesRepository.setSyncEnabled(enabled)
    }

    /**
     * Check if sync is enabled
     */
    suspend fun isSyncEnabled(): Boolean {
        return userPreferencesRepository.isSyncEnabled()
    }

    /**
     * Get sync status flow
     */
    fun getSyncStatusFlow(): Flow<SyncStatus> {
        return userPreferencesRepository.getSyncStatusFlow()
    }

    /**
     * Get combined sync information
     */
    fun getSyncInfoFlow(): Flow<SyncInfo> {
        return combine(
            syncState,
            lastSyncResult,
            getSyncStatusFlow()
        ) { state, result, status ->
            SyncInfo(
                syncState = state,
                lastSyncResult = result,
                syncStatus = status,
                deviceCount = 0, // Simplified for now
                hasAuthenticatedSync = userPreferencesRepository.hasAuthenticatedSync()
            )
        }
    }

    /**
     * Get user's devices
     */
    suspend fun getUserDevices(): Result<List<UserDevice>> {
        return userPreferencesRepository.getUserDevices()
    }

    /**
     * Delete device preferences
     */
    suspend fun deleteDevicePreferences(): Result<Unit> {
        return userPreferencesRepository.deleteDevicePreferences()
    }

    /**
     * Handle authentication state changes
     */
    private suspend fun handleAuthStateChange(authState: AuthState) {
        when (authState) {
            is AuthState.SignedIn -> {
                // User signed in - perform initial sync
                performInitialSync()
            }
            is AuthState.SignedOut -> {
                // User signed out - clear sync state
                _syncState.value = SyncState.IDLE
                _lastSyncResult.value = null
            }
            else -> {
                // Loading or error states - do nothing
            }
        }
    }

    /**
     * Handle preference changes
     */
    private suspend fun handlePreferenceChange(config: ContentFilterConfig) {
        // Only sync if user is authenticated, sync is enabled, and we're not applying server preferences
        if (authManager.isUserSignedIn &&
            userPreferencesRepository.isSyncEnabled() &&
            !_isApplyingServerPreferences) {
            // Debounce sync to avoid too frequent uploads
            // In a real implementation, you might want to add proper debouncing
            syncToServer()
        }
    }

    /**
     * Perform initial sync when app starts or user signs in
     */
    private suspend fun performInitialSync() {
        try {
            _syncState.value = SyncState.SYNCING
            _isApplyingServerPreferences = true

            val result = userPreferencesRepository.performSync()

            _syncState.value = SyncState.IDLE
            _lastSyncResult.value = result.map { }
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR(e.message ?: "Sync failed")
            _lastSyncResult.value = Result.failure(e)
        } finally {
            _isApplyingServerPreferences = false
        }
    }

    /**
     * Reset sync state
     */
    fun resetSyncState() {
        _syncState.value = SyncState.IDLE
        _lastSyncResult.value = null
    }
}

/**
 * Sealed class representing sync states
 */
sealed class SyncState {
    object IDLE : SyncState()
    object SYNCING : SyncState()
    data class ERROR(val message: String) : SyncState()

    val isIdle: Boolean get() = this is IDLE
    val isSyncing: Boolean get() = this is SYNCING
    val isError: Boolean get() = this is ERROR
    val errorMessage: String? get() = (this as? ERROR)?.message
}

/**
 * Data class containing comprehensive sync information
 */
data class SyncInfo(
    val syncState: SyncState,
    val lastSyncResult: Result<Unit>?,
    val syncStatus: SyncStatus,
    val deviceCount: Int,
    val hasAuthenticatedSync: Boolean
) {
    val isCurrentlySyncing: Boolean get() = syncState.isSyncing
    val hasSyncError: Boolean get() = syncState.isError || lastSyncResult?.isFailure == true
    val lastSyncError: Throwable? get() = lastSyncResult?.exceptionOrNull()
    val canSync: Boolean get() = syncStatus != SyncStatus.NOT_AUTHENTICATED && syncStatus != SyncStatus.DISABLED
}