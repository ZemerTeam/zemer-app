package com.jtech.zemer.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import timber.log.Timber
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
/** `429` from the share endpoint: per-device or server-wide daily cap — "try again later", never a retry loop. */
class ZemerRateLimitedException : IOException("Zemer share rate limit reached")

/**
 * `403`/`404` from an owner-token PUT/DELETE: the share no longer exists or the token is rejected
 * (a pre-token share, a taken-down link, cleared app data on the server side of the pair). NOT an
 * IOException - it is a definitive server verdict, not a transport failure: the caller's move is
 * to clear the stored credentials (and, for a share tap, mint a fresh link), never to retry.
 */
class ZemerShareGoneException : Exception("Zemer share gone or owner token rejected")

/**
 * A definitive HTTP failure from an owner-token PUT (400/429/5xx - anything but success and the
 * 403/404 gone cases). Deliberately NOT an IOException: the auto-updater classifies IOException as
 * "server unreachable, defer silently", and a server that is answering with an error (contract
 * drift, rate limit) must be REPORTED, not deferred forever.
 */
class ZemerShareHttpException(val status: Int) : Exception("Zemer share update returned HTTP $status")

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
/** Apply a name->value param list to a request, replacing the repeated `forEach { parameter(..) }`. */
private fun HttpRequestBuilder.applyParams(params: List<Pair<String, String>>) =
    params.forEach { (name, value) -> parameter(name, value) }

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
 * The exact query parameters a `/genres` request carries, in order ([id] = null for the catalog).
 * Extracted so the fail-closed flag contract ([zemerContentFlagParameters]) is unit-testable without
 * a live request. `limit`/`k` are deliberately not sent — the server defaults are the contract;
 * [offset] rides along only when paging past the first tracklist page.
 */
internal fun zemerGenresParameters(
    id: String?,
    allowFemale: Boolean,
    blockVideos: Boolean,
    offset: Int = 0,
): List<Pair<String, String>> = buildList {
    if (id != null) add("id" to id)
    addAll(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
    if (offset > 0) add("offset" to offset.toString())
}

/**
 * The query parameters a facet see-all request carries (`/genres?id=&facet=…`), in order. Same
 * fail-closed flag contract; [limit]+[offset] page the one facet's full list.
 */
internal fun zemerGenreFacetParameters(
    id: String,
    facet: String,
    allowFemale: Boolean,
    blockVideos: Boolean,
    offset: Int,
    limit: Int,
): List<Pair<String, String>> = buildList {
    add("id" to id)
    add("facet" to facet)
    addAll(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
    if (offset > 0) add("offset" to offset.toString())
    add("limit" to limit.toString())
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
            applyParams(zemerSearchParameters(query, allowFemale, blockVideos, k))
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
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos))
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
        Timber.d("AlbumOpen: GET /album id=%s", id)
        val response: HttpResponse = client.get("$BASE_URL/album") {
            parameter("id", id)
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) {
            Timber.d("AlbumOpen: /album id=%s -> 404", id)
            return null
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer album returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerAlbumResponse.serializer(), response.bodyAsText()).also {
            Timber.d("AlbumOpen: /album id=%s -> 200 albumId=%s tracks=%d", id, it.album.id, it.tracks.size)
        }
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
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer artist returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerArtistResponse.serializer(), response.bodyAsText())
    }

    // --- Podcast discovery (handoff `zemer-app-podcasts-request.md`). Whitelist-pure + content-filtered
    // server-side; playback still runs on InnerTube by videoId. Server-first with the offline-snapshot
    // fallback in ZemerSearchRepository (only /podcast-home-rows is live-only). Content flags are sent
    // for parity (mostly no-ops for podcasts). The browse grid + whitelist allow-set come from the
    // content mirror (ZemerContentClient), not /podcasts. ---

    /** `GET /podcast?id=&offset=` — a SHOW + its episodes page. Null on 404 (unknown/filtered-out show). */
    suspend fun podcast(
        id: String,
        offset: Int,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerPodcastResponse? {
        val response: HttpResponse = client.get("$BASE_URL/podcast") {
            parameter("id", id)
            parameter("offset", offset.toString())
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcast returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPodcastResponse.serializer(), response.bodyAsText())
    }

    /**
     * `GET /podcast-channel?id=&offset=` — a host CHANNEL (its shows + latest episodes). Null on 404.
     * [offset] pages the channel-wide episode list; 0 is omitted so page-0 requests stay byte-identical
     * to the pre-paging contract.
     */
    suspend fun podcastChannel(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
        offset: Int = 0,
    ): ZemerPodcastChannelResponse? {
        val response: HttpResponse = client.get("$BASE_URL/podcast-channel") {
            parameter("id", id)
            if (offset > 0) parameter("offset", offset.toString())
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcast-channel returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPodcastChannelResponse.serializer(), response.bodyAsText())
    }

    /** `GET /podcasts/new-episodes?k=` — latest episodes across all whitelisted shows, newest-first. */
    suspend fun podcastsNewEpisodes(
        k: Int,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerNewEpisodesResponse {
        val response: HttpResponse = client.get("$BASE_URL/podcasts/new-episodes") {
            parameter("k", k.toString())
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcasts/new-episodes returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerNewEpisodesResponse.serializer(), response.bodyAsText())
    }

    /**
     * `GET /podcast-genres` — the flat podcast-genre catalog (mirrors `/genres`, minus `kind`). Reuses
     * the shared genre param builder (id=null + content flags). An empty list is a normal state.
     */
    suspend fun podcastGenres(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerPodcastGenresResponse {
        val response: HttpResponse = client.get("$BASE_URL/podcast-genres") {
            applyParams(zemerGenresParameters(id = null, allowFemale, blockVideos))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcast-genres returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPodcastGenresResponse.serializer(), response.bodyAsText())
    }

    /**
     * `GET /podcast-genres?id=<slug>` — one genre's flat show list. Null on 404 (unknown slug or all
     * shows filtered out for this viewer), which the caller handles by backing out.
     */
    suspend fun podcastGenre(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerPodcastGenrePageResponse? {
        val response: HttpResponse = client.get("$BASE_URL/podcast-genres") {
            applyParams(zemerGenresParameters(id, allowFemale, blockVideos))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcast-genre returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPodcastGenrePageResponse.serializer(), response.bodyAsText())
    }

    /**
     * Corpus-native radio: the first page of a whitelist-pure continuation seeded by [kind] (`artist` /
     * `album` / `song`, with [seed] the channelId/browseId/videoId) or `shuffle` (no seed) for Radio mode.
     * Content flags are sent explicitly (kidZone included), same as the other endpoints.
     */
    /**
     * Mints a share link for a user playlist (`POST /user-playlist`, issue #176). The server
     * validates members against the corpus AND drops globally-blocked ids (both folded into
     * `dropped`); all-invalid is a 400. 429 = rate-limited (per-device daily or server-wide cap) —
     * surfaced as [ZemerRateLimitedException] so the UI says "try again later" and never
     * retry-loops. [device] is the anonymous tracking uuid, used only for the rate limit.
     */
    suspend fun createUserPlaylist(title: String, videoIds: List<String>, device: String?, sharedBy: String?): ZemerUserPlaylistCreateResponse {
        val response: HttpResponse = client.post("$BASE_URL/user-playlist") {
            contentType(ContentType.Application.Json)
            setBody(
                zemerResponseJson.encodeToString(
                    ZemerUserPlaylistCreateRequest.serializer(),
                    ZemerUserPlaylistCreateRequest(title = title, videoIds = videoIds, device = device, sharedBy = sharedBy),
                ),
            )
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.TooManyRequests) throw ZemerRateLimitedException()
        if (!response.status.isSuccess()) {
            throw IOException("Zemer user-playlist create returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerUserPlaylistCreateResponse.serializer(), response.bodyAsText())
    }

    /**
     * Live-updating shares: `PUT /user-playlist/<id>` replaces the share's state in place - same
     * id, same URL - with create-identical validation (`kept`/`dropped` back, screened `sharedBy`).
     * Updates spend nothing from the rate-limit pool (server contract 2026-07-30). Throws
     * [ZemerShareGoneException] on 403/404 so the caller clears credentials / re-mints.
     */
    suspend fun updateUserPlaylist(id: String, ownerToken: String, title: String, videoIds: List<String>, sharedBy: String?): ZemerUserPlaylistCreateResponse {
        if (!isValidUserPlaylistShareId(id)) throw ZemerShareGoneException()
        val response: HttpResponse = client.put("$BASE_URL/user-playlist/$id") {
            contentType(ContentType.Application.Json)
            setBody(
                zemerResponseJson.encodeToString(
                    ZemerUserPlaylistUpdateRequest.serializer(),
                    ZemerUserPlaylistUpdateRequest(ownerToken = ownerToken, title = title, videoIds = videoIds, sharedBy = sharedBy),
                ),
            )
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.NotFound) {
            throw ZemerShareGoneException()
        }
        if (!response.status.isSuccess()) {
            throw ZemerShareHttpException(response.status.value)
        }
        return zemerResponseJson.decodeFromString(ZemerUserPlaylistCreateResponse.serializer(), response.bodyAsText())
    }

    /**
     * Withdraws a share (`DELETE /user-playlist/<id>`, token via `X-Owner-Token` - DELETE bodies
     * are unreliable across HTTP stacks, per the server contract): the link 404s everywhere
     * immediately. Throws [ZemerShareGoneException] on 403/404 (already gone - callers treat it
     * as done and clear the stored credentials either way).
     */
    suspend fun deleteUserPlaylist(id: String, ownerToken: String) {
        if (!isValidUserPlaylistShareId(id)) throw ZemerShareGoneException()
        val response: HttpResponse = client.delete("$BASE_URL/user-playlist/$id") {
            header("X-Owner-Token", ownerToken)
        }
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.NotFound) {
            throw ZemerShareGoneException()
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer user-playlist delete returned HTTP ${response.status.value}")
        }
    }

    /**
     * Opens a shared user playlist (`GET /user_playlist/<id>`). Null on 404 (unknown/mistyped id, or
     * a taken-down link). The receiver's content flags are sent explicitly (same fail-closed
     * contract as everywhere); `kidZone=0` is truthful — the app has no kids MODE, only a tab a deep
     * link never lands in. Members that left the corpus since sharing are dropped server-side.
     */
    suspend fun userPlaylist(id: String, allowFemale: Boolean, blockVideos: Boolean): ZemerUserPlaylistResponse? {
        // The id arrives from an untrusted deep link (and getPathSegments returns it DECODED): a
        // '/', '?', '#' or space interpolated raw into the path would hit a different endpoint or
        // truncate the fail-closed content-flag params. Constrain to the server's slug alphabet;
        // anything else is a mistyped/crafted link = not found.
        if (!isValidUserPlaylistShareId(id)) return null
        val response: HttpResponse = client.get("$BASE_URL/user_playlist/$id") {
            parameter("format", "json")
            zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true).forEach { (name, value) ->
                parameter(name, value)
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer user-playlist returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerUserPlaylistResponse.serializer(), response.bodyAsText())
    }

    /**
     * The Zemer Stations catalog (`GET /stations`) — the synchronized-broadcast home row's data. NO
     * content flags are sent: the pools are pre-filtered server-side to the strictest common
     * denominator (handoff §6), so there is nothing left to filter. Clock-dependent — never cached.
     */
    suspend fun stations(): ZemerStationsResponse {
        val response: HttpResponse = client.get("$BASE_URL/stations")
        if (!response.status.isSuccess()) {
            throw IOException("Zemer stations returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerStationsResponse.serializer(), response.bodyAsText())
    }

    /**
     * One station's tune-in payload (`GET /station?id=&next=`): the on-air track with its live offset
     * plus the next [next] scheduled entries (1..10). Null on `404` (unknown id) AND on `503`
     * (station offline: schedule exhausted server-side) — both mean "hide the card / stop with the
     * station-offline state; retry later". No content flags (see [stations]); never cached.
     */
    suspend fun station(id: String, next: Int = 5): ZemerStationTuneInResponse? {
        val response: HttpResponse = client.get("$BASE_URL/station") {
            parameter("id", id)
            parameter("next", next)
        }
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.ServiceUnavailable) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer station returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerStationTuneInResponse.serializer(), response.bodyAsText())
    }

    suspend fun radio(
        kind: String,
        seed: String?,
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerRadioResponse {
        val response: HttpResponse = client.get("$BASE_URL/radio") {
            parameter("kind", kind)
            seed?.let { parameter("seed", it) }
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
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
            applyParams(zemerCuratedPlaylistsParameters(id, allowFemale, blockVideos))
        }

    /**
     * The genre catalog (`GET /genres`) — the genres that currently have songs, most-populated first,
     * with counts computed against the flags sent (same fail-closed, default-OPEN contract as every
     * Zemer request; `kidZone=0` because no genre surface is reachable from inside the KidZone tab).
     * An empty list is a normal state — the genre surfaces just don't render.
     */
    suspend fun genres(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerGenresResponse {
        val response: HttpResponse = client.get("$BASE_URL/genres") {
            applyParams(zemerGenresParameters(id = null, allowFemale, blockVideos))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer genres returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerGenresResponse.serializer(), response.bodyAsText())
    }

    /**
     * One genre's page (`GET /genres?id=`), already filtered server-side for the flags sent; [offset]
     * pages the songs/videos tracklist (server `nextOffset` echo). Returns null on `404` — unknown
     * slug, or every member song is filtered out for this viewer — which the caller handles by
     * backing out, mirroring the curated-playlist detail. A user-initiated open of a large page, so
     * it gets the larger request ceiling.
     */
    suspend fun genre(
        id: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
        offset: Int = 0,
    ): ZemerGenrePageResponse? {
        val response: HttpResponse = client.get("$BASE_URL/genres") {
            applyParams(zemerGenresParameters(id, allowFemale, blockVideos, offset))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer genre returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerGenrePageResponse.serializer(), response.bodyAsText())
    }

    /**
     * One page of a genre's FULL facet list (`/genres?id=&facet=`) — the see-all screens page this
     * (albums/singles) beyond the summary's top-k. Returns null on 404 (unknown/empty genre); a bad
     * facet is a 400 (surfaced as an IOException, never reached with the app's fixed facet slugs).
     */
    suspend fun genreFacet(
        id: String,
        facet: String,
        allowFemale: Boolean,
        blockVideos: Boolean,
        offset: Int = 0,
        limit: Int = GENRE_FACET_LIMIT,
    ): ZemerGenreFacetResponse? {
        val response: HttpResponse = client.get("$BASE_URL/genres") {
            applyParams(zemerGenreFacetParameters(id, facet, allowFemale, blockVideos, offset, limit))
            timeout { requestTimeoutMillis = LARGE_REQUEST_TIMEOUT_MS }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("Zemer genre facet returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerGenreFacetResponse.serializer(), response.bodyAsText())
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
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer home-rows returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerHomeRowsResponse.serializer(), response.bodyAsText())
    }

    /**
     * The telemetry-ranked PODCAST home rows (`GET /podcast-home-rows`) — Top Podcasts + Trending
     * Episodes, the podcast analogue of [homeRows]. Same fail-closed flag contract; `kidZone=0` is sent
     * (the home tab is never reachable from inside the KidZone tab). Whitelist-pure + content-filtered
     * server-side. The server applies an alphabetical fallback while podcast telemetry is thin, so
     * `topPodcasts` is never empty when the server is reachable.
     */
    suspend fun podcastHomeRows(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerPodcastHomeRowsResponse {
        val response: HttpResponse = client.get("$BASE_URL/podcast-home-rows") {
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer podcast-home-rows returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerPodcastHomeRowsResponse.serializer(), response.bodyAsText())
    }

    /**
     * `GET /video-home-rows` — the Videos tab's ranked rows (live-only, like `/podcast-home-rows`).
     * Throws on any non-2xx (including 404 while the endpoint is not yet deployed); the ViewModel is
     * fail-soft, so the tab just keeps its lead row.
     */
    suspend fun videoHomeRows(
        allowFemale: Boolean,
        blockVideos: Boolean,
    ): ZemerVideoHomeRowsResponse {
        val response: HttpResponse = client.get("$BASE_URL/video-home-rows") {
            applyParams(zemerContentFlagParameters(allowFemale, blockVideos, includeKidZone = true))
        }
        if (!response.status.isSuccess()) {
            throw IOException("Zemer video-home-rows returned HTTP ${response.status.value}")
        }
        return zemerResponseJson.decodeFromString(ZemerVideoHomeRowsResponse.serializer(), response.bodyAsText())
    }

    companion object {
        const val BASE_URL = "https://search.zemer.io"
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        // Requests above this k (the Community chip's K_COMMUNITY) get the larger ceiling below.
        private const val LARGE_REQUEST_K = 100
        private const val LARGE_REQUEST_TIMEOUT_MS = 20_000L
        // The facet see-all page size (server max is 200) — one round-trip covers the largest
        // album/single facet, and a genre with more pages via the returned nextOffset.
        private const val GENRE_FACET_LIMIT = 200
    }
}

/**
 * The server's share-id slug alphabet (unguessable base62-ish ids, e.g. "Rtwwz3ZEA5Bzik"). Bounds
 * what a deep link can inject into the request path - see [ZemerSearchClient.userPlaylist].
 */
private val USER_PLAYLIST_SHARE_ID = Regex("[A-Za-z0-9_-]{1,64}")

internal fun isValidUserPlaylistShareId(id: String): Boolean = id.matches(USER_PLAYLIST_SHARE_ID)
