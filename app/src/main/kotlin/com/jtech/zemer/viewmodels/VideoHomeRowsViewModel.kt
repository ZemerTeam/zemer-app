package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
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
 * Backs the Home Videos tab's ranked rows — **Trending Videos**, **New Videos**, **Top Video
 * Artists** (`/video-home-rows`, handoff `zemer-app-video-home-rows-request.md`). Isolated from
 * [HomeViewModel] (the [PodcastHomeRowsViewModel] pattern) so a fetch failure — including the
 * endpoint not being deployed yet — only leaves these rows empty/hidden; the tab keeps its
 * `topVideos` lead row. Screen-open [refresh], reload on content-flag change, fetches serialized
 * behind a [Mutex], stale-flag responses dropped, a failure keeps the previous rows.
 */
@HiltViewModel
class VideoHomeRowsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
) : ViewModel() {
    private val _trending = MutableStateFlow<List<SongItem>>(emptyList())
    val trending: StateFlow<List<SongItem>> = _trending.asStateFlow()

    private val _newVideos = MutableStateFlow<List<SongItem>>(emptyList())
    val newVideos: StateFlow<List<SongItem>> = _newVideos.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artists: StateFlow<List<ArtistItem>> = _artists.asStateFlow()

    private val refreshMutex = Mutex()

    init {
        reloadOnContentFlagChange { refreshNow() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val options = zemerSearchOptions(context)
        runCatching { repository.videoHomeRows(options) }
            .onSuccess { fetched: ZemerResultMapper.VideoHomeRows ->
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    _trending.value = fetched.trending
                    _newVideos.value = fetched.newVideos
                    _artists.value = fetched.artists
                    // Publish the full rows so their "See all" screens show exactly what the rows show.
                    VideoHomeSeeAllStore.publishRows(
                        trending = fetched.trending,
                        newVideos = fetched.newVideos,
                        artists = fetched.artists,
                    )
                }
            }
            // Quiet while the endpoint is not yet deployed would hide real errors too; reportException
            // matches PodcastHomeRowsViewModel (non-fatal breadcrumb, rows stay fail-soft either way).
            .onFailure { reportException(it) }
    }
}
