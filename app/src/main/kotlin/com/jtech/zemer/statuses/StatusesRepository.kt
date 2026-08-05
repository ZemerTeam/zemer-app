package com.jtech.zemer.statuses

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.StatusSourcesConfigKey
import com.jtech.zemer.constants.StatusSourcesVersionKey
import com.jtech.zemer.utils.ZemerContentClient
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Session-scoped access to the status platforms plus the SHARED feed state both the Home-row and
 * story-viewer ViewModels read — [creators] and [seen] live here once instead of being loaded/exposed
 * independently in each VM. Which platforms are fetched (and their categories/keywords) is driven by the
 * server config in [StatusSourcesCache] (synced from the content mirror; the feature is simply hidden
 * until the first successful sync - there is no baked-in fallback), grouped by handler [StatusProviderType]:
 *  - [StatusProviderType.SUPABASE_CATEGORY] providers (JewishStatus shape) load via [loadJewish];
 *  - [StatusProviderType.KEYWORD_FEED] providers (YidStatus shape) load via [loadYid].
 * Each creator carries its [StatusCreator.source] so the See-all screen groups by platform, and each is
 * mapped to the provider it came from ([providerByCreator]) so [posts] fetches with the right backend.
 *
 * Both families are fetched CONCURRENTLY and independently fail-soft: one platform being down still shows
 * the other. The caches self-refresh so newly-posted statuses appear:
 *  - screen open / re-entry re-fetches a family whose cache is older than [STALE_MS];
 *  - pull-to-refresh forces both (`refreshCreators(force = true)`);
 *  - opening a creator re-fetches THAT creator's posts immediately ([refreshPosts]).
 * An all-empty / failed fetch keeps the previous cache (never blanks the row). The source config is
 * refreshed (version-gated) on the same path, non-blocking, and applies to subsequent loads.
 */
@Singleton
class StatusesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seenStore: StatusSeenStore,
) {
    // One mutex + cache per HANDLER FAMILY so they load INDEPENDENTLY and PROGRESSIVELY: the fast family
    // (JewishStatus) publishes immediately without waiting on the multi-MB YidStatus feed, one family's
    // failure keeps the other alive, and a failed source retries on the next refresh (neither is
    // null-cached). The per-family mutex dedupes concurrent loads.
    private val jewishMutex = Mutex()
    private val yidMutex = Mutex()
    @Volatile private var jewishCache: List<StatusCreator>? = null
    @Volatile private var yidCache: YidFeed? = null
    @Volatile private var jewishFetchedAt = 0L
    @Volatile private var yidFetchedAt = 0L

    // A cached family this old is re-fetched on the next screen open (not just on a full app restart).
    private companion object {
        const val STALE_MS = 5 * 60 * 1000L // 5 minutes
    }

    private fun isFresh(fetchedAt: Long) =
        fetchedAt != 0L && SystemClock.elapsedRealtime() - fetchedAt < STALE_MS

    // Posts use a concurrent map with NO lock across the fetch, so preloading the next creator never
    // waits behind the current one; a rare duplicate concurrent fetch for one creator is harmless.
    // KEYWORD_FEED posts are primed from the one-shot feed; SUPABASE_CATEGORY posts are fetched per creator.
    private val postsCache = ConcurrentHashMap<String, List<StatusPost>>()
    // creatorId -> the provider it came from, so per-creator post fetches use the right baseUrl/apiKey and
    // routing keys off the provider's TYPE (only SUPABASE_CATEGORY has a per-creator endpoint).
    private val providerByCreator = ConcurrentHashMap<String, StatusProvider>()

    // Shared feed state (single source for both VMs).
    private val _creators = MutableStateFlow<List<StatusCreator>>(emptyList())
    val creators: StateFlow<List<StatusCreator>> = _creators.asStateFlow()

    /** The persisted "seen" post ids (WhatsApp read state). Delegates to the shared store. */
    val seen: Flow<Set<String>> get() = seenStore.seen

    /**
     * Load both families into [creators], each on its own coroutine so the row updates progressively as
     * each lands, and refresh the server source config (version-gated, non-blocking) for subsequent loads.
     * [force] (pull-to-refresh) re-fetches unconditionally; otherwise a family is re-fetched only when its
     * cache is empty or older than [STALE_MS]. Fail-soft per source: a failure keeps the previous cache.
     */
    suspend fun refreshCreators(force: Boolean = false): Unit = coroutineScope {
        launch { syncStatusSources() } // refreshes config for the NEXT load; never blocks this one
        launch { loadJewish(force) }
        launch { loadYid(force) }
    }

    /** One creator's posts (chronological), routed by its provider. Cached per creator. */
    suspend fun posts(creatorId: String): List<StatusPost> =
        postsCache[creatorId] ?: run {
            val provider = providerByCreator[creatorId]
            // Only SUPABASE_CATEGORY fetches per creator; KEYWORD_FEED posts are feed-primed (its statuses
            // table is not publicly readable), and an unknown provider has nothing to fetch. Guard so a
            // feed creator never hits the per-creator endpoint.
            if (provider?.type != StatusProviderType.SUPABASE_CATEGORY) return emptyList()
            withContext(Dispatchers.IO) { fetchStatusPosts(provider.baseUrl, provider.apiKey, creatorId) }
                .also { postsCache[creatorId] = it }
        }

    /** The already-cached posts for a creator, or null if not fetched yet (no network). */
    fun cachedPosts(creatorId: String): List<StatusPost>? = postsCache[creatorId]

    /**
     * Re-fetch ONE creator's posts right now (called when the viewer opens a creator, so the one you
     * tapped shows its newest statuses immediately). Only SUPABASE_CATEGORY has a per-creator endpoint;
     * a KEYWORD_FEED creator returns whatever the feed cache holds. Fail-soft: on error keep the cached
     * list. Returns the up-to-date posts.
     */
    suspend fun refreshPosts(creatorId: String): List<StatusPost> {
        val provider = providerByCreator[creatorId]
        if (provider?.type != StatusProviderType.SUPABASE_CATEGORY) return postsCache[creatorId] ?: emptyList()
        return runCatching { withContext(Dispatchers.IO) { fetchStatusPosts(provider.baseUrl, provider.apiKey, creatorId) } }
            .onFailure { reportException(it) }
            .getOrNull()?.also { postsCache[creatorId] = it }
            ?: (postsCache[creatorId] ?: emptyList())
    }

    /** Record a status as viewed (persisted). */
    suspend fun markSeen(postId: String) = seenStore.markSeen(listOf(postId))

    /**
     * Version-gated refresh of the server-driven source config. Re-fetches [ZemerContentClient.statusSourcesRaw]
     * only when `/status-sources/version` reports a newer integer than what is installed, installs it into
     * [StatusSourcesCache], and persists the raw JSON + version so it survives restarts. Fully fail-soft:
     * an unreachable mirror, a 503 (invalid config), or an unparseable body leaves the last-good config
     * live (or the feature hidden if none has synced). Runs off the load's critical path (a config change
     * applies to the next refresh).
     */
    private suspend fun syncStatusSources() = withContext(Dispatchers.IO) {
        if (!ZemerContentClient.enabled) return@withContext
        runCatching {
            val remoteVersion = ZemerContentClient.statusSourcesVersion()
            if (remoteVersion <= StatusSourcesCache.syncedVersion) return@runCatching // already current
            val raw = ZemerContentClient.statusSourcesRaw()
            val config = parseStatusSourcesConfig(raw) ?: return@runCatching // invalid -> keep current (fallback)
            StatusSourcesCache.update(config)
            context.dataStore.edit {
                it[StatusSourcesConfigKey] = raw
                it[StatusSourcesVersionKey] = config.version
            }
        }.onFailure { reportException(it) }
    }

    private suspend fun loadJewish(force: Boolean) = jewishMutex.withLock {
        if (!force && jewishCache != null && isFresh(jewishFetchedAt)) return@withLock republish()
        val providers = StatusSourcesCache.current().providersOfType(StatusProviderType.SUPABASE_CATEGORY)
        if (providers.isEmpty()) { // no active supabase-category source (darked / none) -> honored empty
            jewishCache = emptyList()
            jewishFetchedAt = SystemClock.elapsedRealtime()
            return@withLock republish()
        }
        var anySuccess = false
        val byId = LinkedHashMap<String, StatusCreator>() // dedupe by id across providers, keep order
        withContext(Dispatchers.IO) {
            providers.forEach { provider ->
                runCatching { fetchStatusCreators(provider.baseUrl, provider.apiKey, provider.categoryIds) }
                    .onFailure { reportException(it) }
                    .getOrNull()?.let { list ->
                        anySuccess = true
                        list.forEach { c -> if (byId.putIfAbsent(c.id, c) == null) providerByCreator[c.id] = provider }
                    }
            }
        }
        if (!anySuccess) return@withLock republish() // every provider failed -> keep old cache
        val loaded = byId.values.toList()
        jewishCache = loaded
        jewishFetchedAt = SystemClock.elapsedRealtime()
        republish() // creators (and rings) appear NOW; rings refine once kinds land below

        // Resolve each recent status's KIND off the critical path (SUPABASE_CATEGORY recent ids carry no
        // kind), fetched per provider so each uses the right backend, then re-publish so the rings drop the
        // hidden kinds. Fail-soft; a failure just leaves the full ring. Only re-publish if the cache still
        // holds this exact load (no newer refresh landed meanwhile).
        val kinds = runCatching {
            withContext(Dispatchers.IO) {
                buildMap {
                    providers.forEach { provider ->
                        val ids = loaded.filter { providerByCreator[it.id] === provider }.flatMap { it.recentPostIds }
                        putAll(fetchJewishPostKinds(provider.baseUrl, provider.apiKey, ids))
                    }
                }
            }
        }.getOrDefault(emptyMap())
        if (kinds.isNotEmpty() && jewishCache === loaded) {
            jewishCache = loaded.map { c -> c.copy(recentPostKinds = c.recentPostIds.map { kinds[it].orEmpty() }) }
            republish()
        }
    }

    private suspend fun loadYid(force: Boolean) = yidMutex.withLock {
        if (!force && yidCache != null && isFresh(yidFetchedAt)) return@withLock republish()
        val providers = StatusSourcesCache.current().providersOfType(StatusProviderType.KEYWORD_FEED)
        if (providers.isEmpty()) { // no active keyword-feed source (darked / none) -> honored empty
            yidCache = YidFeed(emptyList(), emptyMap())
            yidFetchedAt = SystemClock.elapsedRealtime()
            return@withLock republish()
        }
        var anySuccess = false
        val byId = LinkedHashMap<String, StatusCreator>()
        val postsByCreator = HashMap<String, List<StatusPost>>()
        withContext(Dispatchers.IO) {
            providers.forEach { provider ->
                runCatching { fetchYidStatusFeed(provider.baseUrl, provider.apiKey, provider.musicKeywords) }
                    .onFailure { reportException(it) }
                    .getOrNull()?.let { feed ->
                        anySuccess = true
                        feed.creators.forEach { c -> if (byId.putIfAbsent(c.id, c) == null) providerByCreator[c.id] = provider }
                        postsByCreator.putAll(feed.postsByCreator)
                    }
            }
        }
        if (!anySuccess) return@withLock republish() // every provider failed -> keep old cache
        val feed = YidFeed(byId.values.toList(), postsByCreator)
        feed.postsByCreator.forEach { (id, posts) -> postsCache[id] = posts }
        yidCache = feed
        yidFetchedAt = SystemClock.elapsedRealtime()
        republish()
    }

    /**
     * Publish the merge of whatever each family has loaded so far (JewishStatus first, then YidStatus),
     * dropping cross-platform duplicate creators (same person on both) - see [mergeStatusCreators].
     */
    @Synchronized
    private fun republish() {
        _creators.value = mergeStatusCreators(jewishCache ?: emptyList(), yidCache?.creators ?: emptyList())
    }
}
