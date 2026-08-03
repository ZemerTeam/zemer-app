package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusDownload
import com.jtech.zemer.statuses.StatusDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Status downloads library screen: the saved-status index (from the shared
 * [StatusDownloadManager]) plus removal. Filtering/sorting/grouping is pure and lives in
 * `StatusDownloadsView`, applied by the screen against [downloads].
 */
@HiltViewModel
class StatusDownloadsViewModel @Inject constructor(
    private val manager: StatusDownloadManager,
) : ViewModel() {
    val downloads: StateFlow<List<StatusDownload>> =
        manager.downloads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun remove(download: StatusDownload) {
        viewModelScope.launch { manager.remove(download) }
    }

    /** Bulk-remove a selection of saved statuses (gallery file + index record for each). */
    fun removeAll(items: List<StatusDownload>) {
        viewModelScope.launch { items.forEach { manager.remove(it) } }
    }
}
