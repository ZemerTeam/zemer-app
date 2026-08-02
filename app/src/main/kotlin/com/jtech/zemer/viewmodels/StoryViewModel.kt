package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusPost
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
 * The full-screen story viewer's ViewModel. Reads the SAME shared [StatusesRepository] state the Home
 * row loaded (creators + seen), so opening a creator is instant. If the shared cache is empty (process
 * death re-entering the viewer directly), it re-fetches; [loadAttempted] flips true once that finishes
 * (success OR failure) so the viewer can close instead of spinning forever when the feed is down.
 */
@HiltViewModel
class StoryViewModel @Inject constructor(
    private val repository: StatusesRepository,
) : ViewModel() {
    val creators: StateFlow<List<StatusCreator>> = repository.creators

    val seenPostIds: StateFlow<Set<String>> =
        repository.seen.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _loadAttempted = MutableStateFlow(false)
    val loadAttempted: StateFlow<Boolean> = _loadAttempted.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.creators.value.isEmpty()) repository.refreshCreators()
            _loadAttempted.value = true
        }
    }

    /** One creator's posts, from the shared session cache (fetched on first open); [] on failure. */
    suspend fun loadPosts(creatorId: String): List<StatusPost> =
        runCatching { repository.posts(creatorId) }.getOrDefault(emptyList())

    /** Record a status as viewed (persisted) — WhatsApp "seen", drives the muted ring. */
    fun markSeen(postId: String) {
        viewModelScope.launch { repository.markSeen(postId) }
    }
}
