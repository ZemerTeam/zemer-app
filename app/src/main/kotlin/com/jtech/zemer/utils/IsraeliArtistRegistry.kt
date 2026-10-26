package com.jtech.zemer.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Central registry for artists that should be treated as Israeli and excluded from surfaced content.
 *
 * The data is read from the content server (`/israeliArtists`). The registry is
 * cached in memory after the first load to avoid repeated network requests and can be reused across
 * view models to ensure consistent filtering.
 */
object IsraeliArtistRegistry {
    @Volatile
    private var cachedIds: Set<String> = emptySet()

    // Separate from cachedIds: an empty set is a valid loaded answer (the live list is empty), and
    // keying "loaded" off non-empty re-fetched it on every call. Only a successful load sets it, so a
    // failure is still retried on the next call.
    @Volatile
    private var loaded = false

    // After a failed load, callers skip the fetch until this time: the filter path calls ensureLoaded()
    // per pass, and a slow/unreachable server must not add a network wait to every one of them.
    @Volatile
    private var retryAfterMs = 0L

    private val mutex = Mutex()

    fun isIsraeli(artistId: String?): Boolean {
        if (artistId == null) return false
        return cachedIds.contains(artistId)
    }

    suspend fun ensureLoaded() = ensureLoaded(fetch = ::fetchIds)

    internal suspend fun ensureLoaded(now: () -> Long = System::currentTimeMillis, fetch: suspend () -> Set<String>) {
        if (loaded || now() < retryAfterMs) return

        mutex.withLock {
            if (loaded || now() < retryAfterMs) return

            runCatching {
                val ids = fetch()
                cachedIds = ids
                loaded = true
                Timber.d("IsraeliArtistRegistry: Loaded ${ids.size} artist ids")
            }.onFailure {
                retryAfterMs = now() + RETRY_COOLDOWN_MS
                Timber.w(it, "IsraeliArtistRegistry: Failed to load artist ids")
            }
        }
    }

    private suspend fun fetchIds(): Set<String> =
        runCatching { ZemerContentClient.israeliArtists() }
            .onFailure { reportContentFetchFailure("israeliArtists", it) }
            .getOrThrow()

    internal fun resetForTest() {
        cachedIds = emptySet()
        loaded = false
        retryAfterMs = 0L
    }

    internal const val RETRY_COOLDOWN_MS = 60_000L
}
