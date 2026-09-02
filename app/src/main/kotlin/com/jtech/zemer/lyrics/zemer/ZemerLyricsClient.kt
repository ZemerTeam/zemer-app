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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        val plain: String? = null,        // operator-hosted text (booklet/manual)
        val syncedLrc: String? = null,
        val synced: Boolean = false,
    )

    @Serializable
    data class Resolved(val videoId: String, val lang: String? = null, val verified: Boolean = false, val hasSynced: Boolean = false, val sources: List<Source> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

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
}
