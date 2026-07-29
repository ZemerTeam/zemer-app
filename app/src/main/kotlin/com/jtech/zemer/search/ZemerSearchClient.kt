package com.jtech.zemer.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the deployed Zemer search service (search.zemer.io). One request shape —
 * `GET /search` — feeds every screen; the caller picks `k` (per-category result cap) to suit the
 * summary, a single filter, or the as-you-type dropdown.
 *
 * Content-type is not assumed: the body is read as text and decoded with a lenient,
 * unknown-key-tolerant [Json], so a missing/odd `Content-Type` or a new server field never breaks it.
 */
/**
 * The lenient reader for every Zemer response. Pulled out of the client so its exact config is
 * unit-testable. `ignoreUnknownKeys` forward-compats new server fields; `isLenient` tolerates an
 * odd/missing content-type; `coerceInputValues` falls an explicit JSON `null` on a non-null defaulted
 * field back to its default — kotlinx applies defaults only for ABSENT keys, so without this a
 * `"categories": null` / `"videos": null` would throw and fail the WHOLE response (the strict-
 * deserialization "No results" trap), instead of degrading gracefully.
 */
internal val zemerResponseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * THE single encoding of the send-always / fail-closed content-flag contract, appended by every
 * request builder ([zemerSearchParameters], [ZemerSearchClient.playlist]/[ZemerSearchClient.album],
 * [zemerCuratedPlaylistsParameters]). The server is default-OPEN — an omitted flag means "don't
 * filter that category" — so ALL flags are emitted on every request regardless of value; omitting a
 * false flag would silently leak that category the moment a server default ever changed.
 * [includeKidZone] adds `kidZone=0` for the endpoints that take it: those surfaces are never
 * reachable from inside the KidZone tab, but the flag is still sent explicitly.
 */
internal fun zemerContentFlagParameters(
    allowFemale: Boolean,
    blockVideos: Boolean,
    includeKidZone: Boolean = false,
): List<Pair<String, String>> = buildList {
    add("allowFemale" to if (allowFemale) "1" else "0")
    add("blockVideos" to if (blockVideos) "1" else "0")
    if (includeKidZone) add("kidZone" to "0")
}

/**
 * The exact query parameters every `/search` request carries, in order. Pulled out of the HTTP call
 * so the fail-closed contract ([zemerContentFlagParameters]) is unit-testable without a live request.
 */
internal fun zemerSearchParameters(
    query: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    k: Int,
): List<Pair<String, String>> =
    listOf("q" to query) + zemerContentFlagParameters(allowFemale, blockVideos) + ("k" to k.toString())

/**
 * The exact query parameters a `/zemer-playlists` request carries, in order ([id] = null for the
 * list view). Extracted so the fail-closed flag contract ([zemerContentFlagParameters]) is
 * unit-testable without a live request.
 */
internal fun zemerCuratedPlaylistsParameters(
    id: String?,
    allowFemale: Boolean,
    blockVideos: Boolean,
): List<Pair<String, String>> = buildList {
    if (id != null) add("id" to id)
    addAll(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
}

/**
 * Resolves a server-relative asset path against [ZemerSearchClient.BASE_URL]. The curated-playlists
 * endpoint returns its generated covers as relative paths ("/zemer-playlists/cover?id=…") so the
 * asset stays host-agnostic server-side; absolute URLs (track art on i.ytimg.com) pass through.
 */
internal fun resolveZemerUrl(url: String?): String? =
    url?.let { if (it.startsWith("/")) ZemerSearchClient.BASE_URL + it else it }

@Singleton
class ZemerSearchClient @Inject constructor() {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    suspend fun search(
        query: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
        k: Int,
    ): ZemerSearchResponse {
        val response: HttpResponse = client.get("$BASE_URL/search") {
            zemerSearchParameters(query, allowFemale, blockVideos, k).forEach { (name, value) ->
                parameter(name, value)
            }
            // The Community chip asks for a large k (a few hundred rows); give that heavier response more
            // headroom than the default ceiling, while the as-you-type / filter calls keep the tighter
            // default so a genuinely hung request still fails fast.
            if (k > LARGE_REQUEST_K) timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        // CIO does not throw on non-2xx; guard so an error page (HTML/5xx) is a clean failure rather
        // than a confusing JSON parse error fed from the error body.
        if (!response.status.isSuccess()) {
            throw IOException("Zemer search returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerSearchResponse.serializer(), response.bodyAsText())
    }

    /**
     * Fetch a single playlist already filtered to the whitelist + content flags by the server, so the
     * opened list matches the search card exactly. The content flags are sent explicitly (same
     * fail-closed contract as [search] — the server is default-OPEN). A large playlist is filtered
     * server-side at open time, so this uses the larger request ceiling.
     */
    suspend fun playlist(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerPlaylistResponse {
        val response: HttpResponse = client.get("$BASE_URL/playlist") {
            parameter("id", id)
            zemerContentFlagParameters(allowFemale, blockVideos).forEach { (name, value) ->
                parameter(name, value)
            }
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer playlist returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPlaylistResponse.serializer(), response.bodyAsText())
    }

    /**
     * Fetch a single album already scoped to the whitelist + content flags by the server (an entirely
     * blocked album is a 404). The flags are sent explicitly (same fail-closed contract as [search] —
     * the server is default-OPEN); `kidZone` is always off because the album screen is only reachable
     * from search, never from inside KidZone. The server fetches the album upstream on a cold cache,
     * so this user-initiated one-shot open gets the larger request ceiling. Returns null on `404` —
     * the album is gone from the whitelist/corpus — a typed signal the caller uses to delete a stale
     * local copy (an [IOException] message-match would silently rot).
     */
    suspend fun album(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerAlbumResponse? {
        val response: HttpResponse = client.get("$BASE_URL/album") {
            parameter("id", id)
            zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true).forEach { (name, value) ->
                parameter(name, value)
            }
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer album returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerAlbumResponse.serializer(), response.bodyAsText())
    }

    /**
     * Fetch an artist's whole catalog, already whitelist-scoped + content-filtered by the server for the
     * flags sent (same fail-closed, default-OPEN contract as [search]/[album]). `kidZone` is included so a
     * KidZone-opened artist stays scoped. Returns null on `404` — the artist is filtered out entirely or
     * absent from the corpus — so the caller can fall back to the InnerTube artist path for it. The server
     * reads the corpus (no upstream fetch), but the open is user-initiated so it gets the larger ceiling.
     */
    suspend fun artist(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerArtistResponse? {
        val response: HttpResponse = client.get("$BASE_URL/artist") {
            parameter("id", id)
            zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true).forEach { (name, value) ->
                parameter(name, value)
            }
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer artist returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerArtistResponse.serializer(), response.bodyAsText())
    }

    /**
     * Corpus-native radio: the first page of a whitelist-pure continuation seeded by [kind] (`artist` /
     * `album` / `song`, with [seed] the channelId/browseId/videoId) or `shuffle` (no seed) for Radio mode.
     * Content flags are sent explicitly (kidZone included), same as the other endpoints.
     */
    suspend fun radio(
        kind: String,
        seed: String?,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerRadioResponse {
        val response: HttpResponse = client.get("$BASE_URL/radio") {
            parameter("kind", kind)
            seed?.let { parameter("seed", it) }
            zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true).forEach { (name, value) ->
                parameter(name, value)
            }
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer radio returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerRadioResponse.serializer(), response.bodyAsText())
    }

    /**
     * The next radio page: the opaque [continuation] token from a prior [radio]/[radioContinuation] carries
     * the seed + flags + position, so nothing else is sent. The queue is endless (token rarely null).
     */
    suspend fun radioContinuation(continuation: String): ZemerRadioResponse {
        val response: HttpResponse = client.get("$BASE_URL/radio") {
            parameter("continuation", continuation)
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer radio returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerRadioResponse.serializer(), response.bodyAsText())
    }

    /**
     * The hand-curated "Zemer Playlists" list, ready to render: order, counts, covers and runtimes are
     * all server-computed for the flags sent (see [zemerCuratedPlaylistsParameters]). An empty list is
     * normal — nothing curated yet.
     */
    suspend fun curatedPlaylists(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerCuratedPlaylistsResponse {
        val response = curatedPlaylistsRequest(id = null, allowFemale, blockVideos)
        if (!response.status.isSuccess()) {
            throw IOException("Zemer curated playlists returned HTTP ${response.status.value}")
        }
        val decoded = zemerResponseJson.decodeFromString(ZemerCuratedPlaylistsResponse.serializer(), response.bodyAsText())
        return decoded.copy(playlists = decoded.playlists.map { it.withAbsoluteThumbnail() })
    }

    /**
     * One curated playlist's tracks, filtered server-side for the flags sent. Returns null on 404 —
     * the playlist doesn't exist (or no track survives these flags), which the caller handles by
     * backing out and refreshing the list rather than as an error.
     */
    suspend fun curatedPlaylist(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerCuratedPlaylistResponse? {
        val response = curatedPlaylistsRequest(id, allowFemale, blockVideos)
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer curated playlist returned HTTP ${response.status.value}")
        }
        val decoded = zemerResponseJson.decodeFromString(ZemerCuratedPlaylistResponse.serializer(), response.bodyAsText())
        return decoded.copy(playlist = decoded.playlist.withAbsoluteThumbnail())
    }

    private fun ZemerCuratedPlaylist.withAbsoluteThumbnail(): ZemerCuratedPlaylist =
        copy(thumbnail = resolveZemerUrl(thumbnail))

    private suspend fun curatedPlaylistsRequest(
        id: String?,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): HttpResponse =
        client.get("$BASE_URL/zemer-playlists") {
            zemerCuratedPlaylistsParameters(id, allowFemale, blockVideos).forEach { (name, value) ->
                parameter(name, value)
            }
        }

    /**
     * The telemetry-ranked home rows (`GET /home-rows`). The content flags are sent explicitly (same
     * fail-closed contract as every Zemer request — the server is default-OPEN); `kidZone=0` is always
     * sent because the home tab is never reachable from inside the KidZone tab. The server returns the
     * ranked top-N per row, already whitelist-scoped + content-filtered for the flags sent.
     */
    suspend fun homeRows(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerHomeRowsResponse {
        val response: HttpResponse = client.get("$BASE_URL/home-rows") {
            zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true).forEach { (name, value) ->
                parameter(name, value)
            }
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer home-rows returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerHomeRowsResponse.serializer(), response.bodyAsText())
    }

    companion object {
        const val BASE_URL = "https://search.zemer.io"
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        // Requests above this k (the Community chip's K_COMMUNITY) get the larger ceiling below.
        private const val LARGE_REQUEST_K = 100
        private const val LARGE_REQUEST_TIMEOUT_MS = 20_000L
    }
}
