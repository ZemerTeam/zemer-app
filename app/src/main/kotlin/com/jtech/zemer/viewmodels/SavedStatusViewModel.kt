package com.jtech.zemer.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusDownload
import com.jtech.zemer.statuses.StatusDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the local saved-status viewer: one creator's saved statuses (newest-saved first, the same order
 * the library grid shows), so the pager can swipe within that creator. The creator id comes from the
 * nav route.
 */
@HiltViewModel
class SavedStatusViewModel @Inject constructor(
    manager: StatusDownloadManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val creatorId: String = savedStateHandle["creatorId"] ?: ""

    val items: StateFlow<List<StatusDownload>> =
        manager.downloads
            .map { all -> all.filter { it.creatorId == creatorId }.sortedByDescending { it.savedAt } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
