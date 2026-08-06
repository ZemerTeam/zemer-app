package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.R
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.utils.PodcastWhitelistCache
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlinePodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val zemerRepository: ZemerSearchRepository,
    val database: MusicDatabase,
) : ViewModel() {
    private val podcastId = savedStateHandle.get<String>("podcastId")!!

    val podcast = MutableStateFlow<PodcastItem?>(null)
    val episodes = MutableStateFlow<List<EpisodeItem>>(emptyList())

    // Track library state from database
    val libraryPodcast = podcast.flatMapLatest { p ->
        p?.let { database.podcast(it.id) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // In-progress episodes -> saved position (ms), so a row can show how much time is left.
    val resumePositions = database.episodeResumePositions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Server paging cursor (`nextOffset`): the offset of the NEXT episode page, or null at the end.
    private var nextOffset: Int? = null
    private var isLoadingMore = false

    // Check if podcast is in our whitelist
    val isWhitelisted: Boolean
        get() = PodcastWhitelistCache.isAllowed(podcastId)

    init {
        Timber.d("OnlinePodcastViewModel init with podcastId: $podcastId")
        fetchPodcastData()
    }

    private fun fetchPodcastData() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("fetchPodcastData called for: $podcastId")
            _isLoading.value = true
            _error.value = null
            nextOffset = null

            // Discovery is now the whitelist-pure Zemer server (`/podcast`), not InnerTube. Playback is
            // unchanged: each episode plays by its YouTube videoId through the existing pipeline. A null
            // page = the show is unknown / fully filtered out under the current flags → the not-available
            // state (matching the corpus artist/album 404 behavior).
            try {
                val page = zemerRepository.podcast(podcastId, offset = 0, zemerSearchOptions(context))
                if (page == null) {
                    _error.value = context.getString(R.string.podcast_not_available)
                } else {
                    podcast.value = page.podcast
                    episodes.value = page.episodes
                    nextOffset = page.continuation?.toIntOrNull()
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to load podcast: ${throwable.message}")
                _error.value = throwable.message ?: context.getString(R.string.podcast_not_available)
                reportException(throwable)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Append the next episode page when the list nears its end. No-op once the server reports no more
     * (`nextOffset == null`) or while a page is already in flight (single-in-flight guard).
     */
    fun loadMoreEpisodes() {
        val offset = nextOffset ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val page = zemerRepository.podcast(podcastId, offset, zemerSearchOptions(context)) ?: return@launch
                episodes.value = (episodes.value + page.episodes).distinctBy { it.id }
                nextOffset = page.continuation?.toIntOrNull()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                reportException(throwable)
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * Toggle saving the podcast channel to the library - OPTIMISTIC, exactly like a song like.
     * The local DB row (which drives the heart) flips immediately; the server write
     * (YouTube.savePodcast, the save/unsave endpoint with playlistId) runs in the background and the
     * local toggle is reverted only if it fails. Anonymous users are local-only (no server library).
     */
    fun toggleSubscription() {
        val currentPodcast = podcast.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Read the CURRENT persisted row (NOT the possibly-stale stateIn value): a fast tap before
            // the flow re-emits would otherwise drop the write (IGNORE insert) or sync the wrong way.
            val existingEntity = database.podcast(currentPodcast.id).first()
            val isCurrentlySaved = existingEntity?.inLibrary == true

            // OPTIMISTIC (like a song's like): flip the local heart immediately (upsert = REPLACE so a
            // stale null can't drop it), then sync in the background - revert only if the write fails.
            val optimistic = existingEntity?.toggleBookmark()
                ?: PodcastEntity(
                    id = currentPodcast.id,
                    title = currentPodcast.title,
                    author = currentPodcast.author?.name,
                    thumbnailUrl = currentPodcast.thumbnail,
                    // The host CHANNEL is what the whitelist filter keys off (PodcastLibrarySources); without
                    // it a freshly-subscribed show would fail that channel check until a sync backfills it.
                    channelId = currentPodcast.channelId ?: currentPodcast.author?.id,
                    bookmarkedAt = LocalDateTime.now(),
                )
            database.transaction { upsertPodcast(optimistic) }

            // Only a PERSONAL account may touch the remote library. The "anonymous" login is a shared
            // pooled account (SAPISID present, dataSyncId cleared) - pushing a save there would leak the
            // subscription across every anon user, so anon stays local-only. Same gate music uses.
            if (!isPersonalAccountSignedIn) return@launch

            YouTube.savePodcast(currentPodcast.id, !isCurrentlySaved).onFailure { e ->
                Timber.e(e, "[PODCAST_LIB] savePodcast failed - reverting the optimistic toggle")
                database.transaction {
                    if (existingEntity != null) upsertPodcast(existingEntity)
                    else upsertPodcast(optimistic.copy(bookmarkedAt = null))
                }
                withContext(Dispatchers.Main) {
                    context.toast(
                        if (isCurrentlySaved) R.string.error_podcast_unsubscribe
                        else R.string.error_podcast_subscribe
                    )
                }
            }
        }
    }

    fun retry() {
        fetchPodcastData()
    }
}
