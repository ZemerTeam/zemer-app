package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.PodcastLibrarySources
import com.jtech.zemer.utils.SyncUtils
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingNewEpisodes.value = true
            _newEpisodes.value = PodcastLibrarySources.whitelistedNewEpisodes(
                zemerRepository,
                zemerSearchOptions(context),
                database,
            )
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

    // requestPodcastThumbnail is gone: the server `/podcasts` browse list carries a ready-to-load
    // thumbnail on every row, synced straight into the whitelist table, so there is no per-row art
    // fetch to make anymore.
}
