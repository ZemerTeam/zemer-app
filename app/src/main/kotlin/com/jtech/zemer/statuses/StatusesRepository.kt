package com.jtech.zemer.statuses

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped access to the JewishStatus feed ([StatusesApi]). Shared by the Home-row ViewModel and
 * the story-viewer ViewModel so creators + a creator's posts are each fetched at most once per session.
 *
 * Errors PROPAGATE to callers (the Home row is fail-soft and treats any throw as "no data"). The cache
 * is never invalidated in-session (a Stories feed refreshing mid-session isn't worth the complexity;
 * a process restart re-fetches). Reads run on [Dispatchers.IO].
 */
@Singleton
class StatusesRepository @Inject constructor() {

    // The creators mutex is deliberately held across the fetch: every caller wants the SAME one-shot
    // list, so blocking a concurrent caller until it is ready dedupes the 3-category fetch.
    private val creatorsMutex = Mutex()
    private var creatorsCache: List<StatusCreator>? = null

    // Posts use a concurrent map with NO lock across the fetch, so preloading the next creator never
    // waits behind the current one; a rare duplicate concurrent fetch for one creator is harmless.
    private val postsCache = ConcurrentHashMap<String, List<StatusPost>>()

    /** All music creators (deduped, newest first). Fetched once, then served from cache. */
    suspend fun creators(): List<StatusCreator> = creatorsMutex.withLock {
        creatorsCache ?: withContext(Dispatchers.IO) { fetchStatusCreators() }.also { creatorsCache = it }
    }

    /** One creator's posts (newest first). Cached per creator. */
    suspend fun posts(creatorId: String): List<StatusPost> =
        postsCache[creatorId]
            ?: withContext(Dispatchers.IO) { fetchStatusPosts(creatorId) }.also { postsCache[creatorId] = it }
}
