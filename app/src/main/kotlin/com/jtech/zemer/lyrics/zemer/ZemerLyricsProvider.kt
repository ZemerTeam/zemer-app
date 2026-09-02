package com.jtech.zemer.lyrics.zemer

import android.content.Context
import com.jtech.zemer.lyrics.LyricsProvider
import com.jtech.zemer.lyrics.model.LyricsUnavailableException

/**
 * First provider in the chain. Resolves the videoId through the Zemer server, then fetches the text from
 * the sources the server vouches for — in this preference order:
 *   jkaraoke (line-synced LRC, timings gated by duration on the server) > jyrics (curated plain) >
 *   operator-hosted booklet/manual text.
 * Every body goes through the same parser the server used to verify it (golden-pinned ports), so what the
 * user sees is exactly what was cross-checked.
 */
object ZemerLyricsProvider : LyricsProvider {
    override val name = "Zemer"

    override fun isEnabled(context: Context) = true

    private val lastSource = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Sub-source ("jkaraoke"/"jyrics"/"booklet") that answered the last getLyrics for [videoId], if any. */
    fun sourceLabel(videoId: String): String? = lastSource[videoId]

    /** Bodies in preference order for a resolved entry (fetchers injected for tests). */
    suspend fun bodies(
        resolved: ZemerLyricsClient.Resolved,
        fetch: suspend (String) -> String? = ZemerLyricsClient::fetchText,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (s in resolved.sources.sortedBy { rank(it) }) {
            val body = when (s.type) {
                "jkaraoke" -> s.feedUrl?.let { fetch(it) }?.let { page -> s.songId?.let { id -> JkaraokeLrc.fromFeedPage(page, id)?.synced } }
                "jyrics" -> s.url?.let { fetch(it) }?.let { JyricsParser.parse(it).plain.takeIf { p -> p.lines().count { l -> l.isNotBlank() } >= 4 } }
                "booklet", "manual", "canonical" -> s.syncedLrc?.takeIf { it.isNotBlank() } ?: s.plain?.takeIf { it.isNotBlank() }
                else -> null
            }
            if (body != null) out += s.type to body
        }
        return out
    }

    private fun rank(s: ZemerLyricsClient.Source) = when (s.type) { "jkaraoke" -> 0; "jyrics" -> 1; "booklet" -> 2; "manual" -> 2; "canonical" -> 3; else -> 9 }

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> = runCatching {
        val resolved = ZemerLyricsClient.resolve(id) ?: throw LyricsUnavailableException
        val best = bodies(resolved).firstOrNull() ?: throw LyricsUnavailableException
        lastSource[id] = best.first + (if (resolved.verified) " ✓" else "")
        best.second
    }

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, album: String?, callback: (String) -> Unit) {
        val resolved = ZemerLyricsClient.resolve(id) ?: return
        bodies(resolved).forEach { callback(it.second) }
    }
}
