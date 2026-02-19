package com.jtech.zemer.viewmodels

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.App
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val syncUtils: SyncUtils,
) : ViewModel() {

    /**
     * Logout user and clear all synced content to prevent data mixing between accounts.
     */
    fun logoutAndClearSyncedContent(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear all YouTube Music synced content first
            syncUtils.clearAllSyncedContent()

            // Then clear account preferences
            App.forgetAccount(context)

            // Clear cookie in UI
            onCookieChange("")
        }
    }

    /**
     * Clear all library data including songs, albums, artists, playlists.
     */
    fun clearAllLibraryData() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.clearAllLibraryData()
        }
    }

    /**
     * Atomically save all token credentials to DataStore and restart the app.
     * This prevents race conditions where the app restarts before all credentials are saved.
     */
    fun saveTokenAndRestart(
        context: Context,
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Atomically write all credentials to DataStore in a single transaction
            context.dataStore.edit { preferences ->
                preferences[InnerTubeCookieKey] = cookie
                preferences[VisitorDataKey] = visitorData
                preferences[DataSyncIdKey] = dataSyncId
                preferences[AccountNameKey] = accountName
                preferences[AccountEmailKey] = accountEmail
                preferences[AccountChannelHandleKey] = accountChannelHandle
            }

            // Restart app on Main thread after DataStore write completes
            withContext(Dispatchers.Main) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            }
        }
    }
}
