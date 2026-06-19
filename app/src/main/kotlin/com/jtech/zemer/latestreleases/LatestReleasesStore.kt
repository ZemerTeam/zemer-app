package com.jtech.zemer.latestreleases

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * The "latest releases" feed: recent releases from kosher (whitelisted) artists, precomputed
 * server-side and served as a small JSON. Mirrors the shape produced by the VPS builder
 * (flipphoneguy-api `/zemer/recent-releases.json`); `ignoreUnknownKeys` keeps the app forward-
 * compatible if the server adds fields.
 */
@Serializable
data class LatestReleasesFeed(
    val generatedAt: String? = null,
    val whitelistVersion: String? = null,
    val windowDays: Int = 0,
    val count: Int = 0,
    val releases: List<LatestRelease> = emptyList(),
)

@Serializable
data class LatestRelease(
    val artistId: String,
    val artistName: String,
    val title: String,
    val browseId: String,
    val playlistId: String,
    val thumbnail: String,
    val year: Int? = null,
    val uploadDate: String,
    val trackCount: Int? = null,
    val sampleVideoId: String? = null,
)

/**
 * Owns the latest-releases feed at runtime: a last-good copy cached on disk for instant display,
 * refreshed from the server with a conditional (ETag) request. Modelled on the cipher
 * [com.zemer.cipher.PlayerConfigStore].
 *
 * The feed is an EXTERNAL dependency, so it must never break the rest of the UI. The contract:
 *  - [cachedReleases] returns the on-disk copy immediately (so the Home section shows without a
 *    blank gap, like the DB-backed sections), but ONLY if it was last fetched within [MAX_STALE_MS]
 *    — a server that has been unreachable for 3 days lets stale releases disappear rather than
 *    lingering forever;
 *  - [refresh] does the network work: a conditional GET retried at most [MAX_ATTEMPTS] times, then
 *    GIVES UP until the next launch (no background retry loop). 304 keeps the cache; any failure
 *    keeps the last-good copy; only a fresh 200 replaces it;
 *  - both paths return an empty list rather than throwing, so callers gate on `isNotEmpty()`.
 *
 * An `object` with `internal var` seams (cache dir / clock / network) so the cache-expiry and
 * retry/give-up logic is unit-testable without a server or an Android runtime.
 */
object LatestReleasesStore {
    private const val TAG = "Zemer_LatestReleases"
    private const val FEED_URL = "https://api.flipphoneguy.duckdns.org/zemer/recent-releases.json"
    private const val MAX_ATTEMPTS = 3

    // A cached feed older than this (since its last successful fetch) is treated as gone: the server
    // is presumed down and we don't want ancient "latest" releases shown indefinitely.
    private const val MAX_STALE_MS = 3 * 24 * 60 * 60 * 1000L

    private const val CACHE_FILE = "latest_releases.json"
    private const val META_FILE = "latest_releases.meta"

    private val parser = Json { ignoreUnknownKeys = true }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cached: List<LatestRelease>? = null

    @Volatile
    private var gaveUp = false

    private val mutex = Mutex()

    private val httpClient: HttpClient by lazy { HttpClient(CIO) }

    /** Result of one network attempt. Kept internal so tests can drive it without Ktor. */
    internal sealed interface FetchOutcome {
        data class Success(val body: String, val etag: String?) : FetchOutcome
        data object NotModified : FetchOutcome
        data object Failure : FetchOutcome
    }

    // Test seams.
    internal var cacheDirForTest: File? = null
    internal var nowProvider: () -> Long = System::currentTimeMillis
    internal var fetcher: suspend (etag: String?) -> FetchOutcome = ::httpFetch
    internal var retryDelayMs = 1500L

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Instant path: the in-memory copy, else the on-disk copy if it is still within [MAX_STALE_MS].
     * Never touches the network. Returns empty (and clears expired files) when there is no fresh-
     * enough cache.
     */
    fun cachedReleases(): List<LatestRelease> {
        cached?.let { return it }
        val disk = readValidDiskCache() ?: return emptyList()
        cached = disk.releases
        return disk.releases
    }

    /**
     * Refreshes from the server (conditional on the stored ETag), retrying up to [MAX_ATTEMPTS]
     * times before giving up until the next launch. Returns the best-known list afterwards: the
     * freshly fetched releases on success, otherwise whatever the cache still holds. Never throws.
     */
    suspend fun refresh(): List<LatestRelease> = mutex.withLock {
        if (gaveUp) return cached ?: cachedReleases()

        val etag = readMeta()?.first
        repeat(MAX_ATTEMPTS) { attempt ->
            val outcome = try {
                fetcher(etag)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Refresh attempt ${attempt + 1}/$MAX_ATTEMPTS threw: ${e.message}")
                FetchOutcome.Failure
            }
            when (outcome) {
                is FetchOutcome.Success -> return applyFetched(outcome.body, outcome.etag)
                is FetchOutcome.NotModified -> {
                    Timber.tag(TAG).d("Feed unchanged (304)")
                    writeMeta(etag.orEmpty(), nowProvider())
                    return cachedReleases()
                }
                is FetchOutcome.Failure -> {
                    Timber.tag(TAG).w("Refresh attempt ${attempt + 1}/$MAX_ATTEMPTS failed")
                    if (attempt < MAX_ATTEMPTS - 1) delay(retryDelayMs)
                }
            }
        }

        gaveUp = true
        Timber.tag(TAG).w("Gave up after $MAX_ATTEMPTS attempts; not retrying until next launch")
        return cached ?: cachedReleases()
    }

    private fun applyFetched(body: String, etag: String?): List<LatestRelease> {
        val feed = try {
            parser.decodeFromString<LatestReleasesFeed>(body)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Fetched feed unparseable — keeping previous: ${e.message}")
            return cached ?: cachedReleases()
        }
        cached = feed.releases
        Timber.tag(TAG).d("Fetched ${feed.releases.size} releases (window ${feed.windowDays}d, v${feed.whitelistVersion})")
        try {
            cacheFile()?.let { writeAtomic(it, body) }
            writeMeta(etag.orEmpty(), nowProvider())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not persist feed (kept in memory): ${e.message}")
        }
        return feed.releases
    }

    private suspend fun httpFetch(etag: String?): FetchOutcome {
        val response: HttpResponse = httpClient.get(FEED_URL) {
            if (!etag.isNullOrEmpty()) header(HttpHeaders.IfNoneMatch, etag)
        }
        if (response.status == HttpStatusCode.NotModified) return FetchOutcome.NotModified
        if (!response.status.isSuccess()) {
            Timber.tag(TAG).w("Feed fetch HTTP ${response.status.value}")
            return FetchOutcome.Failure
        }
        return FetchOutcome.Success(response.bodyAsText(), response.headers[HttpHeaders.ETag])
    }

    /** Reads the disk copy, or null if missing, corrupt, or older than [MAX_STALE_MS] (deletes it then). */
    private fun readValidDiskCache(): LatestReleasesFeed? {
        val meta = readMeta()
        val lastFetchMs = meta?.second
        if (lastFetchMs == null || nowProvider() - lastFetchMs > MAX_STALE_MS) {
            if (lastFetchMs != null) Timber.tag(TAG).d("Cached feed too stale (>3d) — dropping")
            clearDiskCache()
            return null
        }
        return try {
            val text = cacheFile()?.takeIf { it.exists() }?.readText() ?: return null
            parser.decodeFromString<LatestReleasesFeed>(text)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Cached feed unreadable — dropping: ${e.message}")
            clearDiskCache()
            null
        }
    }

    private fun clearDiskCache() {
        cacheFile()?.delete()
        metaFile()?.delete()
    }

    private fun cacheDir(): File? {
        cacheDirForTest?.let { return it.apply { if (!exists()) mkdirs() } }
        val context = appContext ?: return null
        return File(context.filesDir, "latest_releases").apply { if (!exists()) mkdirs() }
    }

    private fun cacheFile(): File? = cacheDir()?.let { File(it, CACHE_FILE) }

    private fun metaFile(): File? = cacheDir()?.let { File(it, META_FILE) }

    /** Meta file: line 1 = ETag (may be empty), line 2 = lastFetchMs. */
    private fun readMeta(): Pair<String, Long>? {
        return try {
            val lines = metaFile()?.takeIf { it.exists() }?.readText()?.split("\n") ?: return null
            if (lines.size < 2) return null
            lines[0] to (lines[1].toLongOrNull() ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeMeta(etag: String, lastFetchMs: Long) {
        try {
            metaFile()?.let { writeAtomic(it, "$etag\n$lastFetchMs") }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not write feed meta: ${e.message}")
        }
    }

    private fun writeAtomic(file: File, content: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
    }

    /** Test-only: clears in-memory state between cases. */
    internal fun resetForTest() {
        cached = null
        gaveUp = false
    }
}
