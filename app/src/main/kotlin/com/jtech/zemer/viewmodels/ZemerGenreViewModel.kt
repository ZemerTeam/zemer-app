package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import coil3.imageLoader
import coil3.request.ImageRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.search.ZemerResultMapper.ZemerGenrePage
import com.jtech.zemer.search.ZemerResultMapper.headerCovers
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs one genre detail screen (`/genres?id=<slug>`). The id is a stable server slug (never a
 * YouTube id). Page 0 carries the header + the capped artist/album/single shelves; [loadMore] pages
 * ONLY the songs/videos tracklist (the server's `limit`/`offset`/`nextOffset` contract), appending
 * to the accumulated lists. Same guards as the curated detail: a fresh full load per screen open, a
 * full reload on content-flag change, and a response fetched under stale flags is dropped.
 */
@HiltViewModel
class ZemerGenreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ZemerSearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val genreId = savedStateHandle.get<String>("genreId")!!

    sealed interface UiState {
        data object Loading : UiState

        /**
         * [page] is page 0 (header + capped shelves); [songs]/[videos] are the tracklist accumulated
         * across pages. [nextOffset] null = complete; [loadingMore] makes [loadMore] idempotent
         * and shows the end shimmer while a page is in flight.
         */
        data class Loaded(
            val page: ZemerGenrePage,
            val songs: List<SongItem>,
            val videos: List<SongItem>,
            val nextOffset: Int?,
            val loadingMore: Boolean,
        ) : UiState

        /** 404: unknown slug, or nothing survives this viewer's flags — back out gracefully. */
        data object NotFound : UiState
        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
        // Full reload on a flag change: shelves AND the accumulated tracklist were all fetched
        // under the old flags, so an incremental fix-up is impossible; start over.
        reloadOnContentFlagChange { load() }
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val options = zemerSearchOptions(context)
            runCatching { repository.genre(genreId, options) }
                .onSuccess { page ->
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        // Warm the header mosaic NOW, in parallel with the first frame - the
                        // covers otherwise start downloading only once the header composes,
                        // which read as a visibly late pop-in.
                        page?.let(::preloadHeaderCovers)
                        _state.value = when (page) {
                            null -> UiState.NotFound
                            else -> {
                                // Cross-list dedup, same rule as loadMore: a track the server
                                // returns in BOTH page-0 arrays (the corpus reclassifies isVideo)
                                // must render once, under Songs — never twice with keys
                                // song_X/video_X.
                                val songIds = page.songs.mapTo(HashSet()) { it.id }
                                UiState.Loaded(
                                    page = page,
                                    songs = page.songs,
                                    videos = page.videos.filterNot { it.id in songIds },
                                    nextOffset = page.nextOffset,
                                    loadingMore = false,
                                )
                            }
                        }
                    }
                }
                .onFailure {
                    reportException(it)
                    // Same still-current guard as the success path: a slow fetch that fails AFTER
                    // the flags changed has already been superseded by the collector's reload —
                    // its Error must not clobber the fresher state.
                    if (zemerOptionsStillCurrent(options, ContentFilterState.current)) {
                        _state.value = UiState.Error
                    }
                }
        }
    }

    /** Fire-and-forget coil warm-up for the header mosaic (see the call site in [load]). */
    private fun preloadHeaderCovers(page: ZemerGenrePage) {
        page.headerCovers().forEach { url ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }

    /**
     * Fetch the next tracklist page. No-op unless loaded with a known [UiState.Loaded.nextOffset]
     * and not already fetching. Fail-soft: a failed page just clears [UiState.Loaded.loadingMore]
     * (the screen's near-edge trigger re-fires when the user scrolls back across the prefetch
     * threshold) — never an error state over already-shown tracks. A
     * result is discarded if the flags or the state moved while it was in flight (a flag change
     * triggers the full reload above).
     */
    fun loadMore() {
        val current = _state.value as? UiState.Loaded ?: return
        val offset = current.nextOffset ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch(Dispatchers.IO) {
            val options = zemerSearchOptions(context)
            runCatching { repository.genre(genreId, options, offset) }
                .onSuccess { page ->
                    val latest = _state.value as? UiState.Loaded ?: return@onSuccess
                    // Only append onto the exact state this fetch started from; anything else
                    // (full reload, another page landed) supersedes this result.
                    if (latest.nextOffset != offset || !zemerOptionsStillCurrent(options, ContentFilterState.current)) return@onSuccess
                    _state.value = when (page) {
                        // The genre vanished mid-scroll (curation/flags changed server-side).
                        null -> UiState.NotFound
                        else -> {
                            // Dedup within AND across the two lists: the live corpus can
                            // reclassify a track's isVideo between page fetches, and one id must
                            // never render twice (the screen's keyed rows) or double-count.
                            val songs = (latest.songs + page.songs).distinctBy { it.id }
                            val songIds = songs.mapTo(HashSet()) { it.id }
                            latest.copy(
                                songs = songs,
                                videos = (latest.videos + page.videos)
                                    .distinctBy { it.id }
                                    .filterNot { it.id in songIds },
                                nextOffset = page.nextOffset,
                                loadingMore = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    reportException(error)
                    val latest = _state.value as? UiState.Loaded ?: return@onFailure
                    if (latest.nextOffset == offset) _state.value = latest.copy(loadingMore = false)
                }
        }
    }
}
