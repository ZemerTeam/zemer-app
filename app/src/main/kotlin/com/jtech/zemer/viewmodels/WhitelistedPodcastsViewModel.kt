package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.utils.NewEpisodesFeed
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.allowsFemale
import com.jtech.zemer.utils.PodcastLibrarySources
import com.jtech.zemer.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhitelistedPodcastsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val zemerRepository: ZemerSearchRepository,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    // Expose sync progress from SyncUtils
    val syncProgress = syncUtils.podcastWhitelistSyncProgress
    val isSyncing = syncUtils.isPodcastWhitelistSyncing

    // Subscribed podcasts (whitelist-filtered) - shared source so the filter can't drift between VMs.
    val subscribedPodcasts = PodcastLibrarySources.whitelistedSubscribedPodcasts(database)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // New Episodes feed (shared holder so this VM and LibraryPodcastsViewModel can't drift).
    private val newEpisodesFeed = NewEpisodesFeed(zemerRepository, context, database)
    val newEpisodes = newEpisodesFeed.episodes
    val isLoadingNewEpisodes = newEpisodesFeed.isLoading

    val allPodcasts =
        combine(
            database.allWhitelistedPodcastsByName(),
            searchQuery,
            ContentFilterState.state,
        ) { podcasts: List<PodcastWhitelistEntity>, query, filters ->
            podcasts
                // Female gate: hide a wholly-female host channel when female filtering is on, matching the
                // server, the offline snapshot, and the artist browse. (kidZone is always off for podcast
                // surfaces.) This browse grid reads the mirror whitelist directly, so it is the one podcast
                // discovery surface with no server filter in front of it — the gate must run here.
                .filter { !it.isFemale || filters.allowsFemale() }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Fetch new episodes when screen is opened
        fetchNewEpisodes()
        // Sync subscribed podcasts from YouTube Music
        syncSubscribedPodcasts()
    }

    fun fetchNewEpisodes() = newEpisodesFeed.fetch(viewModelScope)

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

    // requestPodcastThumbnail is gone: the server `/podcasts` browse list carries a ready-to-load
    // thumbnail on every row, synced straight into the whitelist table, so there is no per-row art
    // fetch to make anymore.
}
