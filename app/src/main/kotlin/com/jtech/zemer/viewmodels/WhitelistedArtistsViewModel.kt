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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val isSyncing = syncUtils.isWhitelistSyncing

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
            // Force a full refresh when the user taps the refresh icon so it behaves
            // like a manual “pull new whitelist” action even if the version hasn't changed.
            syncUtils.syncArtistWhitelist(forceSync = true)
        }
    }

    private val thumbRequests = mutableSetOf<String>()

    // Fallback resolver, used ONLY when a synced thumbnail is missing (the ~3 without one) or its URL
    // fails to load (rotated/404). Thumbnails normally arrive with the whitelist sync, so this fires
    // rarely — bound it anyway so a bad server batch can't storm InnerTube like the old backfill did.
    private val thumbSemaphore = Semaphore(4)

    fun requestThumb(artistId: String) {
        synchronized(thumbRequests) {
            if (!thumbRequests.add(artistId)) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            thumbSemaphore.withPermit {
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
                }
                // Intentionally keep artistId in thumbRequests even on failure: re-requesting on every
                // recomposition is exactly the storm this replaces. A persistent failure just shows the
                // default avatar for this session; the set clears on next launch.
            }
        }
    }
}
