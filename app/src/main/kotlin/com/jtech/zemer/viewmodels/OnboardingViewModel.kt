package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.auth.UserAuthManager
import com.jtech.zemer.auth.AuthState
import com.jtech.zemer.auth.WebViewGoogleAuthManager
import com.jtech.zemer.sync.UserPreferencesRepository
import com.jtech.zemer.sync.ContentFilterSyncService
import com.jtech.zemer.sync.DeviceRestoreResult
import com.jtech.zemer.sync.retryDelayMs
import com.jtech.zemer.utils.ContentFilterConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    val authManager: UserAuthManager,
    val webAuthManager: WebViewGoogleAuthManager,
    private val syncService: ContentFilterSyncService
) : ViewModel() {

    data class UiState(
        val isCheckingAutoRestore: Boolean = false,
        // The device-id lookup had no definitive answer (server unreachable/erroring): onboarding is
        // blocked on a retry panel, never silently continued as a new user.
        val restoreFailed: Boolean = false,
        val hasServerPreferences: Boolean = false,
        val restoredConfig: ContentFilterConfig? = null,
        val contentFiltersAlreadySet: Boolean = false,
        val isSignedIn: Boolean = false
    )

    // Not signed in = a fresh install or data clear: start in Checking so the filter toggles never
    // render before the saved-settings lookup answers.
    private val _uiState = MutableStateFlow(UiState(isCheckingAutoRestore = !authManager.isUserSignedIn))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val authState = authManager.authStateFlow

    private var restoreJob: Job? = null

    init {
        checkInitialState()
        if (!authManager.isUserSignedIn) attemptAutoRestore()
    }

    suspend fun signInWithGoogle(idToken: String): Result<com.google.firebase.auth.FirebaseUser> {
        return authManager.signInWithGoogle(idToken).also { result ->
            if (result.isSuccess) {
                // Trigger a sync to upload current preferences to Firebase
                try {
                    syncService.performManualSync()
                } catch (e: Exception) {
                    // Don't fail the sign-in on a sync error
                }
            }
        }
    }

    suspend fun signInAnonymously() = webAuthManager.signInAnonymously().also { result ->
        if (result.isSuccess) {
            // Enable sync and perform initial sync
            try {
                syncService.setSyncEnabled(true)
                syncService.performManualSync()
            } catch (e: Exception) {
                // Don't fail the sign-in on a sync error
            }
        }
    }

    private fun checkInitialState() {
        viewModelScope.launch {
            val isSignedIn = authManager.isUserSignedIn
            val isAutoRestored = userPreferencesRepository.isAutoRestored()
            val contentFiltersSet = false // Simplified - will be handled properly later

            _uiState.update {
                it.copy(
                    isSignedIn = isSignedIn,
                    contentFiltersAlreadySet = contentFiltersSet
                )
            }
        }
    }

    /**
     * Auto-restore for unregistered users with server preferences. Keeps retrying with backoff until the
     * server gives a definitive answer: 200 restores (and locks), 404 continues as a new user.
     */
    fun attemptAutoRestore() {
        if (restoreJob?.isActive == true) return
        restoreJob = viewModelScope.launch { restoreUntilDefinitive() }
    }

    /** The panel's Retry: check again now, without waiting for the next backoff step. */
    fun retryAutoRestore() {
        restoreJob?.cancel()
        _uiState.update { it.copy(isCheckingAutoRestore = true, restoreFailed = false) }
        restoreJob = viewModelScope.launch { restoreUntilDefinitive() }
    }

    private suspend fun restoreUntilDefinitive() {
        if (authManager.isUserSignedIn) {
            _uiState.update { it.copy(isCheckingAutoRestore = false, restoreFailed = false, hasServerPreferences = false) }
            return
        }
        var attempt = 0
        while (true) {
            when (val result = userPreferencesRepository.fetchDevicePreferencesByDeviceId()) {
                is DeviceRestoreResult.Restored -> {
                    val config = result.config

                    // Mark as auto-restored and auto-lock
                    userPreferencesRepository.markAutoRestored(config)

                    // Apply locally
                    userPreferencesRepository.saveToDataStore(config)

                    _uiState.update {
                        it.copy(
                            isCheckingAutoRestore = false,
                            restoreFailed = false,
                            hasServerPreferences = true,
                            restoredConfig = config,
                            contentFiltersAlreadySet = true
                        )
                    }
                    return
                }
                DeviceRestoreResult.NoPrefs -> {
                    _uiState.update {
                        it.copy(isCheckingAutoRestore = false, restoreFailed = false, hasServerPreferences = false)
                    }
                    return
                }
                is DeviceRestoreResult.Failed -> {
                    // Auto-retries keep the panel up (no spinner flicker); only Retry shows the spinner.
                    _uiState.update { it.copy(isCheckingAutoRestore = false, restoreFailed = true) }
                    delay(retryDelayMs(restoreRetryDelayMs(attempt++), result.error))
                }
            }
        }
    }

    
    /**
     * Get the restored email for display purposes
     */
    fun getRestoredEmail(): String? {
        return try {
            // We need to make this synchronous, so we'll use a different approach
            // This would be better handled by making the UI collect the restored email as state
            null // For now, return null - can be implemented later
        } catch (e: Exception) {
            null
        }
    }

}

/** Backoff between automatic restore retries: 2s, 4s, 8s, 16s, then every 30s. */
internal fun restoreRetryDelayMs(attempt: Int): Long = minOf(2_000L shl attempt.coerceIn(0, 4), 30_000L)
