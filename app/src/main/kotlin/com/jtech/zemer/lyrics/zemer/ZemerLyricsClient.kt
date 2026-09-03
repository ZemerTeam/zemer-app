package com.jtech.zemer.lyrics.zemer

import com.jtech.zemer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import com.jtech.zemer.lyrics.LyricsUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Zemer lyrics RESOLVER (search server `/lyrics/resolve`): for a corpus videoId it returns which
 * sources carry this song and where, plus verification facts. Third-party text is fetched by the app
 * from the source itself (provider model, like the YouTube Music lyrics tab); only operator-hosted text
 * (booklet/manual) comes inline. Whitelist purity is by construction: the server only knows corpus ids.
 */
object ZemerLyricsClient {
    @Serializable
    data class Source(
        val type: String,
        val url: String? = null,          // jyrics page
        val songId: Long? = null,         // jkaraoke
        val feedPage: Int? = null,
        val feedUrl: String? = null,
        val browseId: String? = null,     // youtube lyrics tab
        val trackId: Long? = null,        // zingmusic (server-vetted track id) / musixmatch
        val plain: String? = null,        // operator-hosted text (booklet/manual)
        val syncedLrc: String? = null,
        val synced: Boolean = false,
    )

    @Serializable
    data class Resolved(val videoId: String, val lang: String? = null, val verified: Boolean = false, val hasSynced: Boolean = false, val sources: List<Source> = emptyList())

    internal val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** One LRCLIB record (`https://lrclib.net/api/get/<id>`); the server hands out the id of a duration-matched row. */
    @Serializable
    data class LrcLibTrack(val id: Long = 0, val syncedLyrics: String? = null, val plainLyrics: String? = null, val instrumental: Boolean = false)

    /** LRC when LRCLIB has line times, else the plain text; null for an instrumental or an empty record. */
    fun lrclibBody(body: String): String? = runCatching { json.decodeFromString<LrcLibTrack>(body) }.getOrNull()
        ?.takeUnless { it.instrumental }
        ?.let { t -> t.syncedLyrics?.takeIf { it.isNotBlank() } ?: t.plainLyrics?.takeIf(LyricsUtils::hasLyricBody) }

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 15000; connectTimeoutMillis = 10000; socketTimeoutMillis = 15000 }
            expectSuccess = false
        }
    }

    var baseUrl: String = BuildConfig.ZEMER_LYRICS_BASE_URL.trimEnd('/')

    suspend fun resolve(videoId: String): Resolved? {
        val r = client.get("$baseUrl/lyrics/resolve") { url { parameters.append("videoId", videoId) }; header(HttpHeaders.Accept, "application/json") }
        return if (r.status == HttpStatusCode.OK) r.body<Resolved>() else null
    }

    suspend fun fetchText(url: String): String? {
        val r = client.get(url) { header(HttpHeaders.UserAgent, "Zemer/${BuildConfig.VERSION_NAME} lyrics"); header(HttpHeaders.Accept, "text/html,application/json") }
        return if (r.status == HttpStatusCode.OK) r.bodyAsText() else null
    }

    /**
     * zingmusic (jewishmusic.fm) public GraphQL: the lyrics HTML for ONE server-vetted track id. The server resolved
     * and verified which zingmusic track is this song; the app only fetches that id — no title matching here.
     */
    suspend fun zingLyricsHtml(trackId: Long): String? {
        val body = """{"query":"{ track(where:{id:$trackId}){ heLyrics enLyrics } }"}"""
        val r = client.post("https://jewishmusic.fm:8443/graphql") { header(HttpHeaders.ContentType, "application/json"); setBody(body) }
        if (r.status != HttpStatusCode.OK) return null
        val j = json.parseToJsonElement(r.bodyAsText()).jsonObject["data"]?.jsonObject?.get("track")?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        return j["heLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: j["enLyrics"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Send a user's lyrics (an edit they saved) to the Zemer server's submission queue. The server never serves a
     * submission on its own: it is admitted only when a second device agrees or the recording confirms it.
     * Fire-and-forget: failures are silent, the local edit is already saved on the device.
     */
    suspend fun submitLyrics(videoId: String, text: String, device: String, lang: String? = null): Boolean = runCatching {
        val body = buildString {
            append("{\"videoId\":").append(Json.encodeToString(kotlinx.serialization.serializer<String>(), videoId))
            append(",\"device\":").append(Json.encodeToString(kotlinx.serialization.serializer<String>(), device))
            append(",\"text\":").append(Json.encodeToString(kotlinx.serialization.serializer<String>(), text))
            if (lang != null) append(",\"lang\":").append(Json.encodeToString(kotlinx.serialization.serializer<String>(), lang))
            append("}")
        }
        val r = client.post("$baseUrl/lyrics/submit") { header(HttpHeaders.ContentType, "application/json"); setBody(body) }
        r.status == HttpStatusCode.OK
    }.getOrDefault(false)

    /** "Wrong lyrics" report: two distinct devices within 30 days make the server hide the row until re-verified. */
    suspend fun reportLyrics(videoId: String, device: String): Boolean = runCatching {
        val body = "{\"videoId\":" + Json.encodeToString(kotlinx.serialization.serializer<String>(), videoId) + ",\"device\":" + Json.encodeToString(kotlinx.serialization.serializer<String>(), device) + "}"
        client.post("$baseUrl/lyrics/report") { header(HttpHeaders.ContentType, "application/json"); setBody(body) }.status == HttpStatusCode.OK
    }.getOrDefault(false)

    @Serializable
    data class MusixmatchToken(val token: String, val issuedAt: Long = 0)

    /** The shared Musixmatch token brokered by the server (one clean IP issues it; every app reuses it). */
    suspend fun musixmatchToken(renew: String? = null): String? {
        val r = client.get("$baseUrl/lyrics/musixmatch-token") { if (renew != null) url { parameters.append("renew", renew) }; header(HttpHeaders.Accept, "application/json") }
        return if (r.status == HttpStatusCode.OK) r.body<MusixmatchToken>().token else null
    }
}
