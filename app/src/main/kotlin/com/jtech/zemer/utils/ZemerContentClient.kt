package com.jtech.zemer.utils

import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sync.ContentServerException
import com.jtech.zemer.sync.isCloudflareChallenge
import com.jtech.zemer.tracking.Tracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

/**
 * Read-only client for the self-hosted content mirror ([BuildConfig.CONTENT_MIRROR_URL], normally
 * content.zemer.io) - the source of truth for the content allow-sets. ANY failure (disabled,
 * unreachable, non-2xx, empty/invalid body, parse error) throws, and the caller keeps its last-good data.
 *
 * It is a self-contained `object` (not Hilt-injected) on purpose: two of its callers - [WhitelistFetcher]
 * and [IsraeliArtistRegistry] - are Kotlin `object`s that cannot receive a constructor-injected client.
 * Reads are public and unauthenticated;
 * never attach a token. Parsing is lenient so a new mirror field is forward-compatible.
 */
object ZemerContentClient {
    /** Empty URL = mirror disabled; every call then throws. */
    private val baseUrl: String = BuildConfig.CONTENT_MIRROR_URL.trimEnd('/')
    val enabled: Boolean get() = baseUrl.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = DEFAULT_TIMEOUT_MS
            }
            // Gzip: the whitelist is ~432 KB raw / ~70 KB gzipped, so this is the main speed lever.
            install(ContentEncoding) { gzip() }
            install(com.jtech.zemer.net.ZemerHeaders.KtorPlugin)
        }
    }

    private suspend fun getText(path: String, timeoutMs: Long, stallMs: Long? = null): String {
        check(enabled) { "content mirror disabled" }
        val response: HttpResponse = client.get("$baseUrl$path") {
            timeout { contentReadTimeouts(timeoutMs, stallMs) }
        }
        if (!response.status.isSuccess()) {
            throw ContentServerException(response.status.value, "content mirror $path -> HTTP ${response.status.value}", response.isCloudflareChallenge())
        }
        return response.bodyAsText()
    }

    /** `/whitelist/version` → the monotonic gate the version-skip compares against (same value the app derives today). */
    suspend fun version(): Long {
        val dto = json.decodeFromString(ContentVersionDto.serializer(), getText("/whitelist/version", SMALL_TIMEOUT_MS))
        val gate = dto.gate ?: error("content mirror: missing gate in /whitelist/version")
        Timber.d("ZemerContentClient: /whitelist/version gate=%d", gate)
        return gate
    }

    /**
     * `/whitelist` → already whitelist docs. **Throws on an empty list** so an empty/broken mirror can
     * never replace a good whitelist (the caller keeps its last-good table).
     */
    /**
     * [reuseRecent]: accept a download from the last minute (Home's profiles, read right after the sync's). The sync
     * never does: it saves the version it just read alongside this list, so the list must be at least that new.
     */
    suspend fun whitelist(reuseRecent: Boolean = false): List<ContentWhitelistDoc> = whitelistMutex.withLock {
        if (reuseRecent) {
            whitelistMemo?.takeIf { System.nanoTime() - it.first < WHITELIST_MEMO_NS }?.let { return@withLock it.second }
        }
        val docs = json.decodeFromString(ListSerializer(ContentWhitelistDoc.serializer()), getText("/whitelist", WHITELIST_TIMEOUT_MS, WHITELIST_STALL_MS))
        if (docs.isEmpty()) error("content mirror: empty /whitelist")
        Timber.d("ZemerContentClient: /whitelist %d docs", docs.size)
        whitelistMemo = System.nanoTime() to docs
        docs
    }

    private val whitelistMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var whitelistMemo: Pair<Long, List<ContentWhitelistDoc>>? = null

    /**
     * `/podcastChannelsWhitelist/version` → the podcast CHANNEL allow-set gate (the direct parallel to
     * `/whitelist/version`). The whitelist is channel-level now; the mirror is the authoritative allow-set
     * + version gate, same as the artist whitelist.
     */
    suspend fun podcastsWhitelistVersion(): Long {
        val dto = json.decodeFromString(ContentVersionDto.serializer(), getText("/podcastChannelsWhitelist/version", SMALL_TIMEOUT_MS))
        val gate = dto.gate ?: error("content mirror: missing gate in /podcastChannelsWhitelist/version")
        Timber.d("ZemerContentClient: /podcastChannelsWhitelist/version gate=%d", gate)
        return gate
    }

    /** `/podcastChannelsWhitelist` → the whitelisted host-channel docs. Throws on empty so the caller keeps its last-good table. */
    suspend fun podcastsWhitelist(): List<ContentPodcastDoc> {
        val docs = json.decodeFromString(ListSerializer(ContentPodcastDoc.serializer()), getText("/podcastChannelsWhitelist", WHITELIST_TIMEOUT_MS, WHITELIST_STALL_MS))
        if (docs.isEmpty()) error("content mirror: empty /podcastChannelsWhitelist")
        Timber.d("ZemerContentClient: /podcastChannelsWhitelist %d docs", docs.size)
        return docs
    }

    /**
     * `/blockedContentIds` → id→reason map, rebuilt from the server's pre-bucketed `{global, female}`
     * (disabled entries already dropped, unknown reasons already folded into `global` — which the app
     * already treats as "hide for everyone", so this is behavior-preserving). Empty is a legitimate state.
     */
    suspend fun blockedIds(): Map<String, String> {
        val dto = json.decodeFromString(ContentBlockedDto.serializer(), getText("/blockedContentIds", SMALL_TIMEOUT_MS))
        val map = buildMap {
            dto.female.forEach { id -> id.trim().takeIf { it.isNotEmpty() }?.let { put(it, "female") } }
            dto.global.forEach { id -> id.trim().takeIf { it.isNotEmpty() }?.let { put(it, "global") } }
        }
        Timber.d("ZemerContentClient: /blockedContentIds %d overrides (female=%d, global=%d)", map.size, dto.female.size, dto.global.size)
        return map
    }

    /**
     * `/israeliArtists` → id set. **Shape-agnostic**: the collection is empty upstream today, so we accept
     * BOTH `["id", ...]` and `[{"id"/"artistId": ...}, ...]` — whichever it serves once populated — rather
     * than trusting an unverified shape. Empty is legitimate (same as today).
     */
    suspend fun israeliArtists(): Set<String> {
        val ids = json.parseToJsonElement(getText("/israeliArtists", SMALL_TIMEOUT_MS)).jsonArray
            .mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull
                    is JsonObject -> (element["id"] ?: element["artistId"])?.jsonPrimitive?.contentOrNull
                    else -> null
                }?.trim()?.takeIf { it.isNotEmpty() }
            }
            .toSet()
        Timber.d("ZemerContentClient: /israeliArtists %d ids", ids.size)
        return ids
    }

    /**
     * `/status-sources/version` → the monotonic INTEGER gate for the Music Status source config (same
     * version-gate pattern as `/whitelist/version`). Poll this; re-fetch [statusSourcesRaw] only when it
     * increases. Throws on a missing version or any non-2xx (e.g. the 503 the mirror serves for an
     * invalid config) so the caller keeps its last-good config (or the feature stays hidden if none has
     * ever synced - there is no baked-in fallback).
     */
    suspend fun statusSourcesVersion(): Long {
        val dto = json.decodeFromString(StatusSourcesVersionDto.serializer(), getText("/status-sources/version", SMALL_TIMEOUT_MS))
        val version = dto.version ?: error("content mirror: missing version in /status-sources/version")
        Timber.d("ZemerContentClient: /status-sources/version version=%d", version)
        return version
    }

    /**
     * `/status-sources` → the raw typed-descriptor config JSON, parsed by
     * [com.jtech.zemer.statuses.parseStatusSourcesConfig] (org.json, shared with the unit tests). Kept as
     * raw text here so the parse/validate/fail-soft logic lives in one place (the `statuses` package).
     */
    suspend fun statusSourcesRaw(): String = getText("/status-sources", SMALL_TIMEOUT_MS)

    private const val CONNECT_TIMEOUT_MS = 3_000L
    private const val DEFAULT_TIMEOUT_MS = 4_000L
    private const val SMALL_TIMEOUT_MS = 4_000L

    // The allow-lists are large (the artist list ~210 KB gzipped) and one read per launch, and a fresh install cannot
    // start without the artist list: a fixed 8 s cap failed every link under ~210 kbit/s. A slow link that keeps
    // delivering is given time; a stalled one still fails fast.
    private const val WHITELIST_TIMEOUT_MS = 180_000L
    private const val WHITELIST_STALL_MS = 15_000L
    private const val WHITELIST_MEMO_NS = 60_000_000_000L
}

/** `/whitelist/version`. `gate` is the monotonic epoch-ms (or `update`) the app version-gates on. */
@Serializable
data class ContentVersionDto(
    val update: String? = null,
    val updatedAtMs: Long? = null,
    val gate: Long? = null,
)

/** `/blockedContentIds` — already bucketed by reason, disabled entries already dropped server-side. */
@Serializable
data class ContentBlockedDto(
    val global: List<String> = emptyList(),
    val female: List<String> = emptyList(),
)

/** `/status-sources/version`. `version` is the monotonic integer the app version-gates on; `updatedAt` is informational. */
@Serializable
data class StatusSourcesVersionDto(
    val version: Long? = null,
    val updatedAt: String? = null,
)

/**
 * One `/whitelist` document. **Every boolean is nullable** on purpose: the mirror omits fields a doc
 * lacks (e.g. `isKids`/`isChasid`/`isGenZ` are sparse), and the same doc feeds two mappings with
 * different null semantics — the whitelist-entity mapping coerces `?: false`, while `HomeArtistProfile`
 * keeps null (`isAmerican` may be genuinely unknown). Defaulting to `false` here would erase that.
 */
@Serializable
data class ContentWhitelistDoc(
    val id: String = "",
    @SerialName("artistId") val artistId: String? = null,
    val name: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    // Additive display-name split (2026-08-21): `name` keeps the legacy value (for ~49 artists a dual
    // "English - עברית" dash form) so old installs are untouched; `displayName` is the clean
    // single-script name to render, `altName` the same name in the other script (matched by Library
    // search, the browse filters, and artistByName). Contract:
    // handoff-docs/zemer-whitelist-display-names.md.
    val displayName: String? = null,
    val altName: String? = null,
    val isFemale: Boolean? = null,
    val isChasid: Boolean? = null,
    val isGenZ: Boolean? = null,
    val isKids: Boolean? = null,
    val isKidZone: Boolean? = null,
    val isAmerican: Boolean? = null,
    val isFamous: Boolean? = null,
    val isDJ: Boolean? = null,
    val isGroup: Boolean? = null,
    // Per-artist channel image (yt3/lh3 URL), resolved once server-side so devices don't each fetch it.
    val thumbnail: String? = null,
)

/**
 * One `/podcastChannelsWhitelist` document (the mirror's copy of a Firestore `podcastChannelsWhitelist`
 * doc) — a whitelisted podcast HOST CHANNEL. `id` is the `UC…` channel id; `thumbnailUrl` is the durable
 * channel avatar; `isFemale`/`isKidZone` are the wholly-female/kids channel flags; `showCount` is how
 * many of the channel's shows are known.
 */
@Serializable
data class ContentPodcastDoc(
    val id: String = "",
    val name: String? = null,
    val thumbnailUrl: String? = null,
    val isFemale: Boolean = false,
    val isKidZone: Boolean = false,
    val isVerified: Boolean = false,
    val showCount: Int = 0,
)

/**
 * Logs a failed content read and reports it as a `fetch_fail` event (once per [label] per session), so
 * we can see how many devices cannot reach the content server. A cancellation is rethrown, never reported.
 */
/**
 * A read's time limits: [totalMs] for the whole call, and with [stallMs] a large body fails only when no data
 * arrives for that long, so a slow link that keeps delivering finishes (JVM-tested in ContentReadTimeoutsTest).
 */
internal fun HttpTimeoutConfig.contentReadTimeouts(totalMs: Long, stallMs: Long?) {
    requestTimeoutMillis = totalMs
    if (stallMs != null) socketTimeoutMillis = stallMs
}

internal inline fun <T> contentRead(label: String, block: () -> T): Result<T> =
    runCatching(block).onFailure { reportContentFetchFailure(label, it) }

internal fun reportContentFetchFailure(label: String, t: Throwable) {
    // a cancelled read is not a failure: rethrown so the caller's runCatching never turns it into one
    if (t is CancellationException) throw t
    Timber.w(t, "ZemerContentClient: %s unavailable", label)
    if (ZemerContentClient.enabled) {
        Tracker.fetchFail(host = "content", path = label, kind = fetchFailKind(t))
    }
}

/**
 * Why a content-mirror read failed, for the `fetch_fail` telemetry event: `challenge` (Cloudflare bot
 * challenge), `http_<code>`, `timeout`, `io` (no network / DNS / connection), `non_json` (a 2xx body
 * that is not the expected JSON), `empty` (a valid but empty allow-set), else `other`.
 */
internal fun fetchFailKind(t: Throwable): String = when {
    t is ContentServerException && t.challenge -> "challenge"
    t is ContentServerException -> "http_${t.status}"
    t is HttpRequestTimeoutException || t is ConnectTimeoutException || t is SocketTimeoutException -> "timeout"
    t is IOException || t is UnresolvedAddressException -> "io"
    t is SerializationException || t is IllegalArgumentException -> "non_json"
    t is IllegalStateException && t.message?.startsWith("content mirror: empty") == true -> "empty"
    else -> "other"
}
