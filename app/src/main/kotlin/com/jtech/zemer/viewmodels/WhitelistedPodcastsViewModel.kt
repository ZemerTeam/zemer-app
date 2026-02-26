package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.utils.PodcastWhitelistCache
import com.jtech.zemer.utils.SyncUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WhitelistedPodcastsViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    // Expose sync progress from SyncUtils
    val syncProgress = syncUtils.podcastWhitelistSyncProgress
    val isSyncing = syncUtils.isPodcastWhitelistSyncing

    // Subscribed podcasts from database (synced with YouTube Music)
    // Filter by whitelist for extra safety
    val subscribedPodcasts = database.subscribedPodcasts()
        .map { podcasts -> podcasts.filter { PodcastWhitelistCache.isAllowed(it.id) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // New Episodes from official API (VLRDPN)
    private val _newEpisodes = MutableStateFlow<List<SongItem>>(emptyList())
    val newEpisodes: StateFlow<List<SongItem>> = _newEpisodes.asStateFlow()

    private val _isLoadingNewEpisodes = MutableStateFlow(false)
    val isLoadingNewEpisodes: StateFlow<Boolean> = _isLoadingNewEpisodes.asStateFlow()

    val allPodcasts =
        combine(
            database.allWhitelistedPodcastsByName(),
            searchQuery
        ) { podcasts: List<PodcastWhitelistEntity>, query ->
            Timber.d("WhitelistedPodcastsVM: Total whitelisted podcasts from DB: ${podcasts.size}, Search query: '$query'")
            val filteredByQuery =
                if (query.isBlank()) podcasts
                else podcasts.filter { podcast -> podcast.podcastName.contains(query, ignoreCase = true) }

            Timber.d("WhitelistedPodcastsVM: Filtered result: ${filteredByQuery.size} podcasts")
            filteredByQuery
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Fetch new episodes when screen is opened
        fetchNewEpisodes()
        // Sync subscribed podcasts from YouTube Music
        syncSubscribedPodcasts()
    }

    fun fetchNewEpisodes() {
        // Skip for anonymous users
        if (YouTube.isAnonLogin) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingNewEpisodes.value = true
            YouTube.newEpisodes().onSuccess { allEpisodes ->
                // Filter to only episodes from whitelisted podcasts
                _newEpisodes.value = allEpisodes.filter { episode ->
                    val podcastId = episode.album?.id
                    podcastId != null && PodcastWhitelistCache.isAllowed(podcastId)
                }
            }
            _isLoadingNewEpisodes.value = false
        }
    }

    fun syncSubscribedPodcasts() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncPodcastSubscriptions()
        }
    }

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncPodcastWhitelist(forceSync = true)
        }
    }

    private val thumbRequests = mutableSetOf<String>()

    fun requestPodcastThumbnail(podcastId: String) {
        synchronized(thumbRequests) {
            if (!thumbRequests.add(podcastId)) return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                YouTube.podcast(podcastId).onSuccess { podcastPage ->
                    val thumb = podcastPage.podcast.thumbnail
                    if (!thumb.isNullOrBlank()) {
                        database.getPodcastWhitelistEntry(podcastId)?.let { existing ->
                            database.upsertPodcastWhitelist(existing.copy(thumbnailUrl = thumb))
                        }
                    }
                }
            }.onFailure {
                Timber.e(it, "Failed to fetch podcast thumbnail for $podcastId")
                thumbRequests.remove(podcastId)
            }
        }
    }
}
