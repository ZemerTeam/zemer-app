package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusPost
import com.jtech.zemer.statuses.StatusSeenStore
import com.jtech.zemer.statuses.StatusesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The full-screen story viewer's ViewModel. Reads the SAME session-cached creators + posts the Home
 * row loaded ([StatusesRepository]), so opening a creator is instant and advancing across creators
 * reuses cached posts. The story viewer is only reachable from the Home row (whose fetch warmed the
 * cache); if the creators list is somehow empty (process death), [load] re-fetches it.
 */
@HiltViewModel
class StoryViewModel @Inject constructor(
    private val repository: StatusesRepository,
    private val seenStore: StatusSeenStore,
) : ViewModel() {
    private val _creators = MutableStateFlow<List<StatusCreator>>(emptyList())
    val creators: StateFlow<List<StatusCreator>> = _creators.asStateFlow()

    // The persisted seen set — read as a snapshot to resume a creator at its first unseen status.
    val seenPostIds: StateFlow<Set<String>> =
        seenStore.seen.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        viewModelScope.launch {
            runCatching { repository.creators() }.onSuccess { _creators.value = it }
        }
    }

    /** One creator's posts, from the shared session cache (fetched on first open). */
    suspend fun loadPosts(creatorId: String): List<StatusPost> =
        runCatching { repository.posts(creatorId) }.getOrDefault(emptyList())

    /** Record a status as viewed (persisted) — WhatsApp "seen", drives the muted ring. */
    fun markSeen(postId: String) {
        viewModelScope.launch { seenStore.markSeen(listOf(postId)) }
    }
}
