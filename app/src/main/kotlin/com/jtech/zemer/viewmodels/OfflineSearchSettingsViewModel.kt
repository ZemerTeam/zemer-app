package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.OfflineSubsetEnabledKey
import com.jtech.zemer.constants.OfflineSubsetPromoDismissedKey
import com.jtech.zemer.offline.OfflineSubsetSyncer
import com.jtech.zemer.offline.SubsetSyncStatus
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Search backup" settings screen AND the onboarding step: the opt-in toggle and the
 * status/download-now action. All state comes from the injected [OfflineSubsetSyncer] (the single
 * owner of the on-device snapshot); this ViewModel only writes the prefs and drives the syncer.
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
     * Persists the opt-in. Turning it on kicks off the first download on the SYNCER's own scope
     * ([OfflineSubsetSyncer.requestSync] — a viewModelScope launch dies with the screen, cancelling
     * the download the moment the user navigates on); turning it off wipes the snapshot.
     */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[OfflineSubsetEnabledKey] = enabled }
            if (enabled) {
                syncer.requestSync(force = true)
            } else {
                syncer.clear()
            }
        }
    }

    fun downloadNow() {
        syncer.requestSync(force = true)
    }

    /** The user declined the onboarding offer — also silence the one-time search-screen promo. */
    fun dismissPromo() {
        viewModelScope.launch {
            context.dataStore.edit { it[OfflineSubsetPromoDismissedKey] = true }
        }
    }
}
