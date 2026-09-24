package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import com.jtech.zemer.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val syncUtils: SyncUtils,
) : ViewModel() {

    /**
     * Clear all library data (songs, albums, artists, playlists). Suspends until the wipe finishes -
     * the caller must be able to order the account-forget AFTER it and handle a failure, instead of a
     * fire-and-forget launch whose rethrow would crash the app and whose forget could race the wipe.
     */
    suspend fun clearAllLibraryData() {
        withContext(Dispatchers.IO) {
            syncUtils.clearAllLibraryData()
        }
    }
}
