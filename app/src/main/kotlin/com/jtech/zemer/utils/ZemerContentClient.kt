package com.jtech.zemer.utils

import com.jtech.zemer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
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
import timber.log.Timber
import java.io.IOException

/**
 * Read-only client for the self-hosted content mirror ([BuildConfig.CONTENT_MIRROR_URL], normally
 * content.zemer.io) — a periodically-refreshed plain-JSON copy of the world-readable Firestore content
 * collections. It is used **mirror-first** via [mirrorFirst]: ANY failure (disabled, unreachable,
 * non-2xx, empty/invalid body, parse error) throws and falls back to the existing Firebase SDK path, so
 * the worst case is exactly today's behavior.
 *
 * It is a self-contained `object` (not Hilt-injected) on purpose: two of its callers — [WhitelistFetcher]
 * and [IsraeliArtistRegistry] — are Kotlin `object`s that cannot receive a constructor-injected client,
 * so this mirrors how [WhitelistFetcher] self-obtains Firestore. Reads are public and unauthenticated;
 * never attach a token. Parsing is lenient so a new mirror field is forward-compatible.
 */
object ZemerContentClient {
    /** Empty URL = mirror disabled (debug force-Firebase); every call then throws → Firebase fallback. */
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
        }
    }

    private suspend fun getText(path: String, timeoutMs: Long): String {
        check(enabled) { "content mirror disabled" }
        val response: HttpResponse = client.get("$baseUrl$path") {
            timeout { requestTimeoutMillis = timeoutMs }
        }
        if (!response.status.isSuccess()) {
            throw IOException("content mirror $path -> HTTP ${response.status.value}")
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
     * never replace a good whitelist — [mirrorFirst] then falls back to Firebase (which has data).
     */
    suspend fun whitelist(): List<ContentWhitelistDoc> {
        val docs = json.decodeFromString(ListSerializer(ContentWhitelistDoc.serializer()), getText("/whitelist", WHITELIST_TIMEOUT_MS))
        if (docs.isEmpty()) error("content mirror: empty /whitelist")
        Timber.d("ZemerContentClient: /whitelist %d docs", docs.size)
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

    private const val CONNECT_TIMEOUT_MS = 3_000L
    private const val DEFAULT_TIMEOUT_MS = 4_000L
    private const val SMALL_TIMEOUT_MS = 4_000L

    // The whitelist is large (~70 KB gzipped); give it more headroom so a slow link doesn't false-fallback.
    private const val WHITELIST_TIMEOUT_MS = 8_000L
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
 * Try [mirror] first; on ANY throw fall back to [firebase] (the intact Firestore code that runs today),
 * so a down/broken mirror degrades to exactly today's behavior. [label] is for the Timber breadcrumb.
 */
suspend fun <T> mirrorFirst(
    label: String,
    mirror: suspend () -> T,
    firebase: suspend () -> T,
): T =
    runCatching { mirror() }
        .onSuccess { Timber.d("ZemerContentClient: %s served from content mirror", label) }
        .getOrElse { throwable ->
            Timber.w(throwable, "ZemerContentClient: %s mirror unavailable — falling back to Firebase", label)
            firebase()
        }
