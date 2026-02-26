package com.jtech.zemer.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.R
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.utils.PodcastWhitelistCache
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    val database: MusicDatabase,
) : ViewModel() {
    private val podcastId = savedStateHandle.get<String>("podcastId")!!

    val podcast = MutableStateFlow<PodcastItem?>(null)
    val episodes = MutableStateFlow<List<EpisodeItem>>(emptyList())

    // Track library state from database
    val libraryPodcast = podcast.flatMapLatest { p ->
        p?.let { database.podcast(it.id) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

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

            YouTube.podcast(podcastId)
                .onSuccess { podcastPage ->
                    Timber.d("Success! Podcast: ${podcastPage.podcast.title}, Episodes: ${podcastPage.episodes.size}")
                    podcast.value = podcastPage.podcast
                    episodes.value = podcastPage.episodes
                    _isLoading.value = false
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to load podcast: ${throwable.message}")
                    _error.value = throwable.message ?: "Failed to load podcast"
                    _isLoading.value = false
                    reportException(throwable)
                }
        }
    }

    /**
     * Toggle saving podcast to library.
     * Uses YouTube.savePodcast() which calls the like/like endpoint with playlistId.
     * Server-first: API call happens before local database update.
     * For anonymous users, only updates local database (skips API sync).
     */
    fun toggleSubscription(context: Context) {
        val currentPodcast = podcast.value ?: return
        val existingEntity = libraryPodcast.value
        val isCurrentlySaved = existingEntity?.inLibrary == true

        Timber.d("[PODCAST_LIB] toggleSubscription called - podcastId: ${currentPodcast.id}")
        Timber.d("[PODCAST_LIB] isCurrentlySaved: $isCurrentlySaved, isAnonLogin: ${YouTube.isAnonLogin}")

        viewModelScope.launch(Dispatchers.IO) {
            // For anonymous users, just update local
            if (YouTube.isAnonLogin) {
                Timber.d("[PODCAST_LIB] Anonymous login - updating local only")
                database.transaction {
                    if (existingEntity != null) {
                        updatePodcast(existingEntity.toggleBookmark())
                    } else {
                        insertPodcast(
                            PodcastEntity(
                                id = currentPodcast.id,
                                title = currentPodcast.title,
                                author = currentPodcast.author?.name,
                                thumbnailUrl = currentPodcast.thumbnail,
                                bookmarkedAt = LocalDateTime.now(),
                            )
                        )
                    }
                }
                return@launch
            }

            // Server-first: call API, then update local on success
            YouTube.savePodcast(currentPodcast.id, !isCurrentlySaved)
                .onSuccess {
                    Timber.d("[PODCAST_LIB] savePodcast API success! Updating local database.")
                    database.transaction {
                        if (existingEntity != null) {
                            updatePodcast(existingEntity.toggleBookmark())
                        } else {
                            insertPodcast(
                                PodcastEntity(
                                    id = currentPodcast.id,
                                    title = currentPodcast.title,
                                    author = currentPodcast.author?.name,
                                    thumbnailUrl = currentPodcast.thumbnail,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            )
                        }
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "[PODCAST_LIB] savePodcast API failed")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (isCurrentlySaved) R.string.error_podcast_unsubscribe
                            else R.string.error_podcast_subscribe,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    /**
     * Legacy method - now calls toggleSubscription
     */
    fun toggleLibrary(context: Context) = toggleSubscription(context)

    fun retry() {
        fetchPodcastData()
    }
}
