package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Backs the Home Podcasts tab's telemetry-ranked rows — **Top Podcasts** + **Trending Episodes**, the
 * podcast analogue of [HomeViewModel]'s Featured rows. Isolated from [HomeViewModel]
 * (the LatestReleases/Stations pattern, same as [PodcastGenresHomeViewModel]) so a `/podcast-home-rows`
 * failure only hides the two rows (empty lists), never the rest of Home. Screen-open [refresh], reload
 * on content-flag change, fetches serialized behind a [Mutex], stale-flag responses dropped, a failure
 * keeps the previous rows.
 */
@HiltViewModel
class PodcastHomeRowsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _featured = MutableStateFlow<List<PodcastItem>>(emptyList())
    val featured: StateFlow<List<PodcastItem>> = _featured.asStateFlow()

    private val _topPodcasts = MutableStateFlow<List<PodcastItem>>(emptyList())
    val topPodcasts: StateFlow<List<PodcastItem>> = _topPodcasts.asStateFlow()

    private val _trendingEpisodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    val trendingEpisodes: StateFlow<List<EpisodeItem>> = _trendingEpisodes.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        reloadOnContentFlagChange { refreshNow() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val options = zemerSearchOptions(context)
        runCatching { repository.podcastHomeRows(options) }
            .onSuccess { fetched: ZemerResultMapper.PodcastHomeRows ->
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    _featured.value = fetched.featured
                    _topPodcasts.value = fetched.topPodcasts
                    _trendingEpisodes.value = fetched.trendingEpisodes
                    // Publish the full rows so their "See all" screens show exactly what the rows show.
                    PodcastHomeSeeAllStore.publish(
                        PodcastHomeSeeAllData(
                            featured = fetched.featured,
                            topPodcasts = fetched.topPodcasts,
                            trendingEpisodes = fetched.trendingEpisodes,
                        )
                    )
                }
            }
            .onFailure { reportException(it) }
    }
}
