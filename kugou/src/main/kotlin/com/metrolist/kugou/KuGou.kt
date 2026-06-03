@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package com.metrolist.kugou

import com.metrolist.kugou.models.DownloadLyricsResponse
import com.metrolist.kugou.models.Keyword
import com.metrolist.kugou.models.SearchLyricsResponse
import com.metrolist.kugou.models.SearchSongResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import java.lang.Integer.min

private val client = HttpClient {
    expectSuccess = true
    install(ContentNegotiation) {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        json(json)
        json(json, ContentType.Text.Html)
        json(json, ContentType.Text.Plain)
    }
    install(ContentEncoding) {
        gzip()
        deflate()
    }
}

private const val PAGE_SIZE = 8
private const val HEAD_CUT_LIMIT = 30
private const val DURATION_TOLERANCE = 8

object KuGou {
    var useTraditionalChinese: Boolean = false

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> = runCatching {
        val keyword = generateKeyword(title, artist, album)
        getLyricsCandidate(keyword, duration)?.let { candidate ->
            Base64.Default.decode(downloadLyrics(candidate.id, candidate.accesskey).content)
                .decodeToString()
                .normalize()
        } ?: throw IllegalStateException("No lyrics candidate")
    }

    suspend fun getAllPossibleLyricsOptions(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val keyword = generateKeyword(title, artist, album)
        runCatching {
            searchSongs(keyword).data.info.forEach { song ->
                if (duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE) {
                    searchLyricsByHash(song.hash).candidates.firstOrNull()?.let { candidate ->
                        Base64.Default.decode(downloadLyrics(candidate.id, candidate.accesskey).content)
                            .decodeToString()
                            .normalize()
                            .let(callback)
                    }
                }
            }
        }
        searchLyricsByKeyword(keyword, duration).candidates.forEach { candidate ->
            Base64.Default.decode(downloadLyrics(candidate.id, candidate.accesskey).content)
                .decodeToString()
                .normalize()
                .let(callback)
        }
    }

    suspend fun getLyricsCandidate(keyword: Keyword, duration: Int): SearchLyricsResponse.Candidate? {
        runCatching {
            searchSongs(keyword).data.info.forEach { song ->
                if (duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE) {
                    val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
                    if (candidate != null) return candidate
                }
            }
        }
        return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
    }

    suspend fun searchSongs(keyword: Keyword): SearchSongResponse =
        client.get("https://mobileservice.kugou.com/api/v3/search/song") {
            parameter("version", 9108)
            parameter("plat", 0)
            parameter("pagesize", PAGE_SIZE)
            parameter("showtype", 0)
            url.encodedParameters.append("keyword", keyword.searchQuery().encodeURLParameter(spaceToPlus = false))
        }.body()

    private suspend fun searchLyricsByKeyword(keyword: Keyword, duration: Int): SearchLyricsResponse =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("duration", duration.takeIf { it != -1 }?.times(1000))
            url.encodedParameters.append("keyword", keyword.searchQuery().encodeURLParameter(spaceToPlus = false))
        }.body()

    private suspend fun searchLyricsByHash(hash: String): SearchLyricsResponse =
        client.get("https://lyrics.kugou.com/search") {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "pc")
            parameter("hash", hash)
        }.body()

    private suspend fun downloadLyrics(id: Long, accessKey: String): DownloadLyricsResponse =
        client.get("https://lyrics.kugou.com/download") {
            parameter("fmt", "lrc")
            parameter("charset", "utf8")
            parameter("client", "pc")
            parameter("ver", 1)
            parameter("id", id)
            parameter("accesskey", accessKey)
        }.body()

    private fun normalizeTitle(title: String) = title
        .replace("\\(.*\\)".toRegex(), "")
        .replace("（.*）".toRegex(), "")
        .replace("「.*」".toRegex(), "")
        .replace("『.*』".toRegex(), "")
        .replace("<.*>".toRegex(), "")
        .replace("《.*》".toRegex(), "")
        .replace("〈.*〉".toRegex(), "")
        .replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) = artist
        .replace(", ", "、")
        .replace(" & ", "、")
        .replace(".", "")
        .replace("和", "、")
        .replace("\\(.*\\)".toRegex(), "")
        .replace("（.*）".toRegex(), "")

    fun generateKeyword(title: String, artist: String, album: String? = null) =
        Keyword(normalizeTitle(title), normalizeArtist(artist), album)

    private fun Keyword.searchQuery() = buildString {
        append(title)
        append(" - ")
        append(artist)
        if (!album.isNullOrBlank()) append(" ").append(album)
    }

    private fun String.normalize(): String = lines()
        .filter { line -> line.matches(ACCEPTED_REGEX) }
        .let { lines ->
            var headCutLine = 0
            for (i in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                if (lines[i].matches(BANNED_REGEX)) {
                    headCutLine = i + 1
                    break
                }
            }

            val filteredLines = lines.drop(headCutLine)
            var tailCutLine = 0
            for (i in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                if (lines[lines.lastIndex - i].matches(BANNED_REGEX)) {
                    tailCutLine = i + 1
                    break
                }
            }
            filteredLines.dropLast(tailCutLine).joinToString("\n")
        }

    @Suppress("RegExpRedundantEscape")
    private val ACCEPTED_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\].*".toRegex()
    private val BANNED_REGEX = ".+].+[:：].+".toRegex()
}
