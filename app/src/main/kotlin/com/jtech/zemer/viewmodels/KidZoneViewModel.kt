package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.utils.SyncUtils
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
class KidZoneViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    val allArtists =
        combine(
            database.allKidsArtistsByName(),
            searchQuery
        ) { artists: List<Artist>, query ->
            Timber.d("KidZoneVM: Total kids artists from DB: ${artists.size}, Search query: '$query'")
            val filteredByQuery =
                if (query.isBlank()) artists
                else artists.filter { artist -> artist.artist.name.contains(query, ignoreCase = true) }

            Timber.d("KidZoneVM: Filtered result: ${filteredByQuery.size} artists")
            filteredByQuery
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
