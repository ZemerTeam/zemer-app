package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.ArtistSortType
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.WhitelistCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WhitelistedArtistsViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    // Expose sync progress from SyncUtils
    val syncProgress = syncUtils.whitelistSyncProgress

    val allArtists =
        combine(
            database.allWhitelistedArtistsByName(),
            ContentFilterState.state,
            searchQuery
        ) { artists: List<com.jtech.zemer.db.entities.Artist>, filters, query ->
            Timber.d("WhitelistedArtistsVM: Total whitelisted artists from DB: ${artists.size}, Search query: '$query'")
            val filteredByToggle = artists.filter { artist ->
                val entry = WhitelistCache.get(artist.id)
                entry == null || WhitelistCache.isAllowed(entry, filters)
            }
            val filteredByQuery =
                if (query.isBlank()) filteredByToggle
                else filteredByToggle.filter { artist -> artist.artist.name.contains(query, ignoreCase = true) }

            Timber.d("WhitelistedArtistsVM: Filtered result: ${filteredByQuery.size} artists")
            filteredByQuery
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncArtistWhitelist()  // Fixed: was calling syncArtistsSubscriptions() by mistake
        }
    }

    private val thumbRequests = mutableSetOf<String>()

    fun requestThumb(artistId: String) {
        synchronized(thumbRequests) {
            if (!thumbRequests.add(artistId)) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pageResult = com.metrolist.innertube.YouTube.artist(artistId)
                pageResult.onSuccess { artistPage ->
                    val thumb = artistPage.artist.thumbnail
                    if (!thumb.isNullOrBlank()) {
                        database.getArtistById(artistId)?.let { existing ->
                            database.update(existing.copy(thumbnailUrl = thumb))
                        }
                    }
                }
            }.onFailure {
                thumbRequests.remove(artistId)
            }
        }
    }
}
