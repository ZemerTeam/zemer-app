package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.constants.ArtistSortType
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.utils.ArtistThumbResolver
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
    private val thumbResolver: ArtistThumbResolver,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    val isSyncing = syncUtils.isWhitelistSyncing

    // Null until the first DB emission so the screen can shimmer instead of flashing "empty".
    val allArtists: kotlinx.coroutines.flow.StateFlow<List<com.jtech.zemer.db.entities.Artist>?> =
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
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            // Force a full refresh when the user taps the refresh icon so it behaves
            // like a manual “pull new whitelist” action even if the version hasn't changed.
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    // Fallback for a missing/rotted synced thumbnail — the shared app-wide resolver (bounded,
    // cooldown-retried, column-targeted write). See ArtistThumbResolver.
    fun requestThumb(artistId: String) = thumbResolver.requestThumb(artistId)
}
