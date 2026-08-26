package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.utils.ArtistThumbResolver
import com.jtech.zemer.utils.SyncUtils
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
class KidZoneViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val thumbResolver: ArtistThumbResolver,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    val isSyncing = syncUtils.isWhitelistSyncing

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    // Null until the first DB emission so the screen can shimmer instead of flashing "empty".
    val allArtists: kotlinx.coroutines.flow.StateFlow<List<Artist>?> =
        combine(
            database.allKidsArtistsByName(),
            searchQuery
        ) { artists: List<Artist>, query ->
            Timber.d("KidZoneVM: Total kids artists from DB: ${artists.size}, Search query: '$query'")
            val filteredByQuery =
                if (query.isBlank()) artists
                // Match the other-script altName too (see WhitelistedArtistsViewModel).
                else artists.filter { artist ->
                    artist.artist.name.contains(query, ignoreCase = true) ||
                        WhitelistCache.get(artist.id)?.altName?.contains(query, ignoreCase = true) == true
                }

            Timber.d("KidZoneVM: Filtered result: ${filteredByQuery.size} artists")
            filteredByQuery
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Fallback for a missing/rotted synced thumbnail — the shared app-wide resolver (bounded,
    // cooldown-retried, column-targeted write). See ArtistThumbResolver.
    fun requestThumb(artistId: String) = thumbResolver.requestThumb(artistId)
}
