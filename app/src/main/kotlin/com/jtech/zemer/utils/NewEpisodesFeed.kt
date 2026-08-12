package com.jtech.zemer.utils

import android.content.Context
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared "New Episodes" feed state for the two podcast-library ViewModels
 * ([com.jtech.zemer.viewmodels.WhitelistedPodcastsViewModel] and
 * [com.jtech.zemer.viewmodels.LibraryPodcastsViewModel]) - the StateFlows and the fetch live in ONE
 * place so the two can't drift. The list itself comes from the shared [PodcastLibrarySources]. [fetch]
 * always resets the loading flag (try/finally) so a failed read can never leave the spinner stuck.
 */
class NewEpisodesFeed(
    private val repository: ZemerSearchRepository,
    private val context: Context,
    private val database: MusicDatabase,
) {
    private val _episodes = MutableStateFlow<List<SongItem>>(emptyList())
    val episodes: StateFlow<List<SongItem>> = _episodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun fetch(scope: CoroutineScope) {
        // Manual offline mode: the feed is a server fetch and its episodes stream - one gate here
        // covers every caller (home row, library podcasts, whitelisted podcasts browse).
        if (OfflineModeState.enabled) {
            _episodes.value = emptyList()
            return
        }
        scope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                _episodes.value = PodcastLibrarySources.whitelistedNewEpisodes(
                    repository,
                    zemerSearchOptions(context),
                    database,
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
