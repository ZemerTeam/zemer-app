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
import kotlin.math.pow

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

            // Auto-restore logic: Only auto-restore if we find server preferences but user isn't signed in locally
            // This happens on data clear/reinstall, NOT on first-time sign-in
            if (!authManager.isUserSignedIn) {
                try {
                    Log.d("ZemerSync", "User not signed in locally - checking for server preferences to auto-restore")
                    val result = userPreferencesRepository.fetchDevicePreferencesByDeviceId()
                    if (result.isSuccess) {
                        Log.d("ZemerSync", "Found server preferences - performing auto-restore and auto-lock")
                        _isApplyingServerPreferences = true
                        val config = result.getOrThrow()
                        Log.d("ZemerSync", "Applying config: $config")

                        // Mark as auto-restored and auto-lock settings
                        userPreferencesRepository.markAutoRestored(config)

                        ContentFilterState.current = config
                        userPreferencesRepository.saveToDataStore(config)
                        _isApplyingServerPreferences = false
                        return@launch
                    } else {
                        Log.d("ZemerSync", "No server preferences found - first-time user, no auto-restore")
                    }
                } catch (e: Exception) {
                    Log.d("ZemerSync", "Auto-restore exception: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.d("ZemerSync", "User already signed in locally - no auto-restore")
            }

            // If user is signed in, perform normal sync with retry mechanism
            if (authManager.isUserSignedIn) {
                Log.d("ZemerSync", "User is signed in, performing normal sync with retry")
                Log.d("ZemerSync", "Attempting sync with auth ready check...")

                // Set syncing state
                _syncState.value = SyncState.SYNCING

                val result = retryWithAuthTimeout {
                    userPreferencesRepository.performSync()
                }
                Log.d("ZemerSync", "Sync result: ${result.isSuccess}, error: ${result.exceptionOrNull()?.message}")
                _lastSyncResult.value = result.map { }

                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e("ZemerSync", "Initial sync failed", error)
                    _syncState.value = SyncState.ERROR("Initial sync failed: ${error?.message}")
                } else {
                    Log.d("ZemerSync", "Initial sync completed successfully")
                    _syncState.value = SyncState.IDLE
                }
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
                // Wait for auth to be fully ready (email loaded)
                if (waitForAuthReady()) {
                    performInitialSync()
                } else {
                    _syncState.value = SyncState.ERROR("Authentication timeout")
                    Log.e("ZemerSync", "Failed to get user email within timeout")
                }
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

            val result = retryWithAuthTimeout {
                userPreferencesRepository.performSync()
            }

            // After sync, always ensure current local preferences are uploaded to server
            // This handles the case where user set preferences during onboarding before signing in
            Log.d("ZemerSync", "Ensuring local preferences are uploaded after initial sync")
            val uploadResult = userPreferencesRepository.syncToServer()
            if (uploadResult.isSuccess) {
                Log.d("ZemerSync", "Local preferences uploaded successfully after sign-in")
            } else {
                Log.e("ZemerSync", "Failed to upload local preferences after sign-in", uploadResult.exceptionOrNull())
            }

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
     * Wait for authentication to be ready with timeout
     */
    private suspend fun waitForAuthReady(timeoutMs: Long = 10000L): Boolean {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (authManager.isUserSignedIn && authManager.currentUserEmail != null) {
                return true
            }
            kotlinx.coroutines.delay(100)
        }
        return false
    }

    /**
     * Retry operation with authentication timeout and exponential backoff
     */
    private suspend fun <T> retryWithAuthTimeout(
        maxRetries: Int = 5,
        timeoutMs: Long = 10000L,
        operation: suspend () -> T
    ): Result<T> {
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null

        for (attempt in 0 until maxRetries) {
            try {
                // Check if auth is ready
                val isSignedIn = authManager.isUserSignedIn
                val userEmail = authManager.currentUserEmail
                Log.d("ZemerSync", "Retry attempt ${attempt + 1}: isSignedIn=$isSignedIn, userEmail=$userEmail")

                if (isSignedIn && userEmail != null) {
                    Log.d("ZemerSync", "Auth is ready, executing operation")
                    return Result.success(operation())
                } else {
                    lastException = Exception("Authentication not ready (attempt ${attempt + 1}/$maxRetries)")
                    Log.d("ZemerSync", "Auth not ready: isSignedIn=$isSignedIn, userEmail=$userEmail")
                    if (System.currentTimeMillis() - startTime < timeoutMs) {
                        // Exponential backoff: 100ms, 200ms, 400ms, 800ms, 1600ms
                        val delay = (100L * (1 shl attempt)).coerceAtMost(1600L)
                        Log.d("ZemerSync", "Waiting ${delay}ms before retry")
                        kotlinx.coroutines.delay(delay)
                        continue
                    } else {
                        Log.d("ZemerSync", "Timeout reached, breaking")
                        break
                    }
                }
            } catch (e: Exception) {
                lastException = e
                Log.e("ZemerSync", "Exception in retry attempt ${attempt + 1}", e)
                if (e is com.google.firebase.auth.FirebaseAuthException) {
                    // Auth error - retry with backoff
                    if (System.currentTimeMillis() - startTime < timeoutMs) {
                        val delay = (100L * (1 shl attempt)).coerceAtMost(1600L)
                        Log.d("ZemerSync", "Auth error, waiting ${delay}ms before retry")
                        kotlinx.coroutines.delay(delay)
                        continue
                    }
                }
                break
            }
        }

        return Result.failure(lastException ?: Exception("Retry timeout after $timeoutMs ms"))
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