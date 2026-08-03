package com.jtech.zemer.viewmodels

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

/** One creator's saved statuses (newest-saved first), a cube page in the saved viewer. */
data class SavedCreatorStatuses(
    val creatorId: String,
    val statuses: List<StatusDownload>,
)

/**
 * Backs the local saved-status viewer. Exposes ALL saved statuses grouped by creator so the viewer can
 * cube-swipe between creators exactly like the live one; within each creator they are newest-saved first
 * (the library grid order), and creators are ordered by their most recent save. The nav route supplies
 * the creator + status to open at.
 */
@HiltViewModel
class SavedStatusViewModel @Inject constructor(
    manager: StatusDownloadManager,
) : ViewModel() {
    val creators: StateFlow<List<SavedCreatorStatuses>> =
        manager.downloads
            .map { all ->
                all.groupBy { it.creatorId }
                    .map { (id, list) -> SavedCreatorStatuses(id, list.sortedByDescending { it.savedAt }) }
                    .sortedByDescending { it.statuses.firstOrNull()?.savedAt ?: 0L }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
