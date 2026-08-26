package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ArtistThumbResolver
import com.jtech.zemer.utils.SyncUtils
import com.jtech.zemer.utils.WhitelistCache
import com.metrolist.innertube.models.PodcastItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val zemerRepository: ZemerSearchRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    val isSyncing = syncUtils.isWhitelistSyncing

    // The kid-flagged show catalog (/podcasts?kidZone=1, with the offline-subset fallback) for the
    // Podcasts tab. Null = loading (shimmer); empty = unreachable or genuinely none (the tab's
    // empty state; pull-to-refresh retries). Fail-soft - a fetch failure (the repository RETHROWS
    // when the server is unreachable and no snapshot exists) never affects the Artists tab.
    val kidPodcasts = MutableStateFlow<List<PodcastItem>?>(null)
    val isRefreshingPodcasts = MutableStateFlow(false)
    val podcastSearchQuery = MutableStateFlow("")

    init {
        fetchKidPodcasts()
    }

    fun fetchKidPodcasts() {
        viewModelScope.launch(Dispatchers.IO) {
            // Spinner only on a RE-fetch (pull-to-refresh over data already on screen) - the
            // initial load is the scaffold shimmer's job, matching the Artists tab, which never
            // auto-spins on open.
            val isRefresh = kidPodcasts.value != null
            if (isRefresh) isRefreshingPodcasts.value = true
            try {
                kidPodcasts.value = zemerRepository.kidZonePodcasts(zemerSearchOptions(context).copy(kidZone = true))
                    ?: kidPodcasts.value ?: emptyList()
            } catch (e: Exception) {
                // Unreachable server with no offline snapshot rethrows - keep the last list (or the
                // empty state) instead of crashing the screen open.
                kidPodcasts.value = kidPodcasts.value ?: emptyList()
            } finally {
                if (isRefresh) isRefreshingPodcasts.value = false
            }
        }
    }

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
