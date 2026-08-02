package com.jtech.zemer.statuses

import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
 * Session-scoped access to BOTH status platforms ([StatusesApi] = JewishStatus, [YidStatusApi] =
 * YidStatus) plus the SHARED feed state both the Home-row and story-viewer ViewModels read — [creators]
 * and [seen] live here once instead of being loaded/exposed independently in each VM. The merged
 * creators list feeds the (uniform) Home row; each creator carries its [StatusCreator.source] so the
 * See-all screen can group by platform and [posts] routes to the right backend.
 *
 * Both platforms are fetched CONCURRENTLY and independently fail-soft: one platform being down still
 * shows the other. Fetches are cached and never invalidated in-session (a process restart re-fetches);
 * an all-empty result is NOT cached, so a transient outage retries on the next refresh.
 */
@Singleton
class StatusesRepository @Inject constructor(
    private val seenStore: StatusSeenStore,
) {
    // Each platform has its own mutex + cache so they load INDEPENDENTLY and PROGRESSIVELY: the fast
    // platform (JewishStatus) publishes immediately without waiting on the multi-MB YidStatus feed, a
    // single platform's failure keeps the other alive, and a failed source retries on the next refresh
    // (neither is null-cached). The per-source mutex dedupes concurrent loads of the same platform.
    private val jewishMutex = Mutex()
    private val yidMutex = Mutex()
    @Volatile private var jewishCache: List<StatusCreator>? = null
    @Volatile private var yidCache: YidFeed? = null

    // Posts use a concurrent map with NO lock across the fetch, so preloading the next creator never
    // waits behind the current one; a rare duplicate concurrent fetch for one creator is harmless.
    // YidStatus posts are primed here from its one-shot feed; JewishStatus posts are fetched per creator.
    private val postsCache = ConcurrentHashMap<String, List<StatusPost>>()
    private val sourceById = ConcurrentHashMap<String, StatusSource>()

    // Shared feed state (single source for both VMs).
    private val _creators = MutableStateFlow<List<StatusCreator>>(emptyList())
    val creators: StateFlow<List<StatusCreator>> = _creators.asStateFlow()

    /** The persisted "seen" post ids (WhatsApp read state). Delegates to the shared store. */
    val seen: Flow<Set<String>> get() = seenStore.seen

    /**
     * Load both platforms into [creators], each on its own coroutine so the row updates progressively as
     * each lands. Fail-soft per source: a platform's failure leaves its cache null (retried next refresh)
     * and never blanks the other.
     */
    suspend fun refreshCreators(): Unit = coroutineScope {
        launch { loadJewish() }
        launch { loadYid() }
    }

    /** One creator's posts (chronological), routed by source. Cached per creator. */
    suspend fun posts(creatorId: String): List<StatusPost> =
        postsCache[creatorId] ?: run {
            // YidStatus posts are primed from its feed; if a YidStatus creator misses the cache there is
            // nothing to fetch per-creator (the statuses table is not publicly readable). Only JewishStatus
            // fetches per creator - guard so a YidStatus id never hits the JewishStatus endpoint.
            if (sourceById[creatorId] == StatusSource.YID_STATUS) return emptyList()
            withContext(Dispatchers.IO) { fetchStatusPosts(creatorId) }.also { postsCache[creatorId] = it }
        }

    /** The already-cached posts for a creator, or null if not fetched yet (no network). */
    fun cachedPosts(creatorId: String): List<StatusPost>? = postsCache[creatorId]

    /** Record a status as viewed (persisted). */
    suspend fun markSeen(postId: String) = seenStore.markSeen(listOf(postId))

    private suspend fun loadJewish() = jewishMutex.withLock {
        if (jewishCache != null) return@withLock republish()
        val loaded = runCatching { withContext(Dispatchers.IO) { fetchStatusCreators() } }
            .onFailure { reportException(it) }.getOrNull() ?: return@withLock // retry next refresh
        loaded.forEach { sourceById[it.id] = StatusSource.JEWISH_STATUS }
        jewishCache = loaded
        republish()
    }

    private suspend fun loadYid() = yidMutex.withLock {
        if (yidCache != null) return@withLock republish()
        val feed = runCatching { withContext(Dispatchers.IO) { fetchYidStatusFeed() } }
            .onFailure { reportException(it) }.getOrNull() ?: return@withLock // retry next refresh
        feed.creators.forEach { sourceById[it.id] = StatusSource.YID_STATUS }
        feed.postsByCreator.forEach { (id, posts) -> postsCache[id] = posts }
        yidCache = feed
        republish()
    }

    /**
     * Publish the merge of whatever each platform has loaded so far (JewishStatus first, then YidStatus),
     * dropping cross-platform duplicate creators (same person on both) - see [mergeStatusCreators].
     */
    @Synchronized
    private fun republish() {
        _creators.value = mergeStatusCreators(jewishCache ?: emptyList(), yidCache?.creators ?: emptyList())
    }
}
