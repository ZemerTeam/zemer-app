package com.jtech.zemer.statuses

import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped access to the JewishStatus feed ([StatusesApi]) plus the SHARED feed state both the
 * Home-row and story-viewer ViewModels read — [creators] and [seen] live here once instead of being
 * loaded/exposed independently in each VM (they can never disagree, and the fetch logic exists once).
 *
 * Fetches are cached and never invalidated in-session (a Stories feed refreshing mid-session isn't
 * worth the complexity; a process restart re-fetches). Reads run on [Dispatchers.IO]. [refreshCreators]
 * is fail-soft — a failure keeps the previous (possibly empty) list, so the Home row just stays hidden.
 */
@Singleton
class StatusesRepository @Inject constructor(
    private val seenStore: StatusSeenStore,
) {
    // The creators mutex is deliberately held across the fetch: every caller wants the SAME one-shot
    // list, so blocking a concurrent caller until it is ready dedupes the 3-category fetch.
    private val creatorsMutex = Mutex()
    private var creatorsCache: List<StatusCreator>? = null

    // Posts use a concurrent map with NO lock across the fetch, so preloading the next creator never
    // waits behind the current one; a rare duplicate concurrent fetch for one creator is harmless.
    private val postsCache = ConcurrentHashMap<String, List<StatusPost>>()

    // Shared feed state (single source for both VMs).
    private val _creators = MutableStateFlow<List<StatusCreator>>(emptyList())
    val creators: StateFlow<List<StatusCreator>> = _creators.asStateFlow()

    /** The persisted "seen" post ids (WhatsApp read state). Delegates to the shared store. */
    val seen: Flow<Set<String>> get() = seenStore.seen

    /** Load the creators list into [creators], fail-soft (a failure keeps the previous list). */
    suspend fun refreshCreators() {
        runCatching { creatorsOnce() }
            .onSuccess { _creators.value = it }
            .onFailure { reportException(it) }
    }

    /** One creator's posts (chronological). Cached per creator. */
    suspend fun posts(creatorId: String): List<StatusPost> =
        postsCache[creatorId]
            ?: withContext(Dispatchers.IO) { fetchStatusPosts(creatorId) }.also { postsCache[creatorId] = it }

    /** Record a status as viewed (persisted). */
    suspend fun markSeen(postId: String) = seenStore.markSeen(listOf(postId))

    private suspend fun creatorsOnce(): List<StatusCreator> = creatorsMutex.withLock {
        creatorsCache ?: withContext(Dispatchers.IO) { fetchStatusCreators() }.also { creatorsCache = it }
    }
}
