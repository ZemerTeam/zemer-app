package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerResultMapper
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.IsraeliArtistRegistry
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.RankedContentGate
import com.jtech.zemer.utils.WhitelistCache
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
import timber.log.Timber
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
        // Manual offline mode: the ranked video rows are live-only — clear so they hide, no fetch
        // (the Videos tab keeps its downloaded-videos lead row from HomeViewModel).
        if (OfflineModeState.enabled) {
            _trending.value = emptyList()
            _newVideos.value = emptyList()
            _artists.value = emptyList()
            return@withLock
        }
        val options = zemerSearchOptions(context)
        IsraeliArtistRegistry.ensureLoaded()
        runCatching { repository.videoHomeRows(options) }
            .onSuccess { fetched: ZemerResultMapper.VideoHomeRows ->
                Timber.d(
                    "NET: /video-home-rows -> trending=%d new=%d artists=%d",
                    fetched.trending.size, fetched.newVideos.size, fetched.artists.size,
                )
                if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                    // The client-only ranked gate (female/Israeli/kids — no wire flag exists), same
                    // rule HomeViewModel applies to the topVideos lead row on this tab.
                    val entryById = WhitelistCache.snapshot().associateBy { it.artistId }
                    fun blocked(ids: List<String>) = RankedContentGate.isBlockedRanked(
                        ids = ids,
                        allowFemale = options.allowFemale,
                        flagsOf = { id -> entryById[id]?.let { RankedContentGate.Flags(it.isFemale, it.isKids) } },
                    )
                    val trending = fetched.trending.filterNot { blocked(it.artists.mapNotNull { a -> a.id }) }
                    val newVideos = fetched.newVideos.filterNot { blocked(it.artists.mapNotNull { a -> a.id }) }
                    val artists = fetched.artists.filterNot { blocked(listOfNotNull(it.id)) }
                    _trending.value = trending
                    _newVideos.value = newVideos
                    _artists.value = artists
                    // Publish the full rows so their "See all" screens show exactly what the rows show.
                    VideoHomeSeeAllStore.publishRows(
                        trending = trending,
                        newVideos = newVideos,
                        artists = artists,
                    )
                }
            }
            // Quiet while the endpoint is not yet deployed would hide real errors too; reportException
            // matches PodcastHomeRowsViewModel (non-fatal breadcrumb, rows stay fail-soft either way).
            .onFailure { reportException(it) }
    }
}
