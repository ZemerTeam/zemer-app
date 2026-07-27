package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.constants.OfflineSubsetWifiOnlyKey
import com.jtech.zemer.offline.OfflineSubsetSyncer
import com.jtech.zemer.offline.SubsetSyncStatus
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Offline search" settings screen: the opt-in + WiFi-only toggles and the
 * status/download-now action. All state comes from the injected [OfflineSubsetSyncer]
 * (the single owner of the on-device snapshot); this ViewModel only writes the two prefs
 * and drives the syncer's actions.
 */
@HiltViewModel
class OfflineSearchSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncer: OfflineSubsetSyncer,
) : ViewModel() {

    val status: StateFlow<SubsetSyncStatus> = syncer.status

    init {
        viewModelScope.launch { syncer.refresh() }
    }

    /**
     * Persists the opt-in. Turning it on kicks off the first download ([sync] with force so the
     * initial fetch runs even though the enabled flag has only just been written); turning it off
     * wipes the downloaded snapshot.
     */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[OfflineSubsetEnabledKey] = enabled }
            if (enabled) {
                syncer.sync(force = true)
            } else {
                syncer.clear()
            }
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[OfflineSubsetWifiOnlyKey] = wifiOnly }
        }
    }

    fun downloadNow() {
        viewModelScope.launch { syncer.sync(force = true) }
    }
}
