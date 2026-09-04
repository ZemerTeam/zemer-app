package com.jtech.zemer.lyrics.zemer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Port of zemer-search `harvester/kugou-harvest.mjs`'s download path. The server verified WHICH KuGou
 * lyric matches this recording (audio check) and serves a `kugou:<hash>:<krcId>` pointer; the app re-runs
 * the krcs search by hash (the accesskey rotates, so it is never in the pointer), takes the candidate with
 * that exact id — never another candidate's — and decodes the base64 `fmt=lrc` body.
 */
object KugouLrc {
    @Serializable data class Candidate(val id: Long = 0, val accesskey: String? = null)
    @Serializable data class Krcs(val candidates: List<Candidate> = emptyList())
    @Serializable data class Download(val content: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val TIMED = Regex("""^\[\d{2}:\d{2}(?:\.\d{2,3})?\].*\S""")

    fun searchUrl(hash: String) = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&hash=$hash"

    fun downloadUrl(id: Long, accessKey: String) =
        "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"

    /** The accesskey of the server-vetted candidate id in a krcs search body. */
    fun accessKey(krcsJson: String, krcId: Long): String? =
        runCatching { json.decodeFromString(Krcs.serializer(), krcsJson) }.getOrNull()
            ?.candidates?.firstOrNull { it.id == krcId }?.accesskey?.takeIf { it.isNotBlank() }

    /** Line-synced LRC from a download body: timed lines only (metadata/credit tags dropped), >= 4 or null. */
    fun lrc(downloadJson: String): String? {
        val content = runCatching { json.decodeFromString(Download.serializer(), downloadJson) }.getOrNull()?.content ?: return null
        val text = runCatching { Base64.getDecoder().decode(content).decodeToString() }.getOrNull() ?: return null
        val lines = text.lines().map { it.trim() }.filter { TIMED.matches(it) }
        return if (lines.size >= 4) lines.joinToString("\n") else null
    }
}
