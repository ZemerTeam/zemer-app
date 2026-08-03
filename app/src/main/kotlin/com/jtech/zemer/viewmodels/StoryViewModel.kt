package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.jtech.zemer.constants.HideImageStatusKey
import com.jtech.zemer.constants.HideTextStatusKey
import com.jtech.zemer.statuses.StatusContentFilter
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusDownload
import com.jtech.zemer.statuses.StatusDownloadManager
import com.jtech.zemer.statuses.StatusPost
import com.jtech.zemer.statuses.StatusesRepository
import com.jtech.zemer.statuses.applyStatusFilter
import com.jtech.zemer.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The full-screen story viewer's ViewModel. Reads the SAME shared [StatusesRepository] state the Home
 * row loaded (creators + seen), so opening a creator is instant. If the shared cache is empty (process
 * death re-entering the viewer directly), it re-fetches; [loadAttempted] flips true once that finishes
 * (success OR failure) so the viewer can close instead of spinning forever when the feed is down.
 */
@HiltViewModel
class StoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StatusesRepository,
    private val downloadManager: StatusDownloadManager,
) : ViewModel() {
    val creators: StateFlow<List<StatusCreator>> = repository.creators

    val seenPostIds: StateFlow<Set<String>> =
        repository.seen.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Ids the user has already saved to their device, for the download FAB's "already saved" state.
    val savedStatusIds: StateFlow<Set<String>> =
        downloadManager.savedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Save the given status to the gallery + index it. For a text status the caller passes
     * [renderTextBitmap] (a theme-colored render); it is invoked only for text. Fail-soft Result.
     */
    suspend fun saveStatus(
        post: StatusPost,
        creator: StatusCreator,
        renderTextBitmap: (() -> Bitmap)? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<StatusDownload> = downloadManager.save(post, creator, renderTextBitmap, onProgress)

    // The user's status content filter (Settings -> Appearance), reactive so a settings change re-filters
    // the posts the viewer shows. Text-only is hidden by default; image is shown by default. Held as a
    // StateFlow so [cachedPosts] can read the current value synchronously off the DataStore hot path.
    //
    // Initial value is NULL ("not read yet"), deliberately NOT a provisional default: seeding a guessed
    // default (hideImage = false) and letting DataStore flip it a few ms later re-ran the viewer's driver
    // mid-open and restarted playback - visible only when the user's real setting differs from the guess,
    // i.e. exactly when hide-image is ON. The driver waits for the first (real) non-null value, so its
    // first load already uses the persisted filter.
    val contentFilter: StateFlow<StatusContentFilter?> =
        context.dataStore.data
            .map {
                StatusContentFilter(
                    hideText = it[HideTextStatusKey] ?: true,
                    hideImage = it[HideImageStatusKey] ?: false,
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun List<StatusPost>.filtered(): List<StatusPost> =
        contentFilter.value?.let { applyStatusFilter(it) } ?: this

    private val _loadAttempted = MutableStateFlow(false)
    val loadAttempted: StateFlow<Boolean> = _loadAttempted.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.creators.value.isEmpty()) repository.refreshCreators()
            _loadAttempted.value = true
        }
    }

    // Each accessor applies the content filter, so every consumer (driver, cube preview, resume math)
    // sees the same visible list and a fully-filtered creator ends up with no posts and is auto-skipped.

    /** One creator's posts, from the shared session cache (fetched on first open); [] on failure. */
    suspend fun loadPosts(creatorId: String): List<StatusPost> =
        runCatching { repository.posts(creatorId) }.getOrDefault(emptyList()).filtered()

    /** Re-fetch one creator's posts NOW, so the creator the user just tapped shows its newest statuses
     *  immediately (JewishStatus; YidStatus returns its cached feed posts). Fail-soft. */
    suspend fun refreshPosts(creatorId: String): List<StatusPost> =
        runCatching { repository.refreshPosts(creatorId) }.getOrDefault(emptyList()).filtered()

    /** The already-cached posts for a creator, or null if not fetched yet - seeds the cube preview face
     *  without a load flash for neighbors that are already prefetched. */
    fun cachedPosts(creatorId: String): List<StatusPost>? = repository.cachedPosts(creatorId)?.filtered()

    /**
     * The current persisted seen set, AWAITED (not the [seenPostIds] StateFlow snapshot, which is still
     * emptySet() for the first frames after the viewer opens while DataStore loads) so resuming at the
     * first-unseen status is correct on open.
     */
    suspend fun seenSnapshot(): Set<String> = repository.seen.first()

    /** Record a status as viewed (persisted) — WhatsApp "seen", drives the muted ring. */
    fun markSeen(postId: String) {
        viewModelScope.launch { repository.markSeen(postId) }
    }
}
