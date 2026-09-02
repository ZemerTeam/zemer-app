package com.jtech.zemer.lyrics.zemer

import android.content.Context
import com.jtech.zemer.constants.EnableZemerLyricsKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.jtech.zemer.lyrics.LabeledLyrics
import com.jtech.zemer.lyrics.LyricsProvider
import com.jtech.zemer.lyrics.model.LyricsUnavailableException

/**
 * First provider in the chain. Resolves the videoId through the Zemer server, then fetches the text from
 * the sources the server vouches for — in this preference order:
 *   jkaraoke (line-synced LRC, timings gated by duration on the server) > jyrics / shironet (curated plain) >
 *   operator-hosted booklet/manual text.
 * Every body goes through the same parser the server used to verify it (golden-pinned ports), so what the
 * user sees is exactly what was cross-checked.
 */
object ZemerLyricsProvider : LyricsProvider {
    override val name = "Zemer"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableZemerLyricsKey] ?: true

    /**
     * Bodies in preference order for a resolved entry (fetchers injected for tests). With [firstOnly] the
     * walk stops at the first source that yields a body, so the auto-fetch path does not download and
     * parse every source when only the best one is shown.
     */
    suspend fun bodies(
        resolved: ZemerLyricsClient.Resolved,
        fetch: suspend (String) -> String? = ZemerLyricsClient::fetchText,
        firstOnly: Boolean = false,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (s in resolved.sources.sortedBy { rank(it) }) {
            if (firstOnly && out.isNotEmpty()) break
            val body = when (s.type) {
                "jkaraoke" -> s.feedUrl?.let { fetch(it) }?.let { page -> s.songId?.let { id -> JkaraokeLrc.fromFeedPage(page, id)?.synced } }
                "jyrics" -> s.url?.let { fetch(it) }?.let { JyricsParser.parse(it).plain.takeIf { p -> p.lines().count { l -> l.isNotBlank() } >= 4 } }
                "shironet" -> s.url?.let { fetch(it) }?.let { ShironetParser.parse(it).plain.takeIf { p -> p.lines().count { l -> l.isNotBlank() } >= 4 } }
                "booklet", "manual", "canonical" -> s.syncedLrc?.takeIf { it.isNotBlank() } ?: s.plain?.takeIf { it.isNotBlank() }
                else -> null
            }
            if (body != null) out += s.type to body
        }
        return out
    }

    private fun rank(s: ZemerLyricsClient.Source) = when (s.type) { "jkaraoke" -> 0; "jyrics" -> 1; "shironet" -> 1; "booklet" -> 2; "manual" -> 2; "canonical" -> 3; else -> 9 }

    /** "Zemer · jkaraoke ✓": the sub-source matters for provenance; ✓ = the server cross-checked two sources. */
    fun label(source: String, verified: Boolean): String = "$name · $source" + (if (verified) " ✓" else "")

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> =
        getLabeledLyrics(id, title, artist, duration, album).map { it.lyrics }

    override suspend fun getLabeledLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<LabeledLyrics> = runCatching {
        val resolved = ZemerLyricsClient.resolve(id) ?: throw LyricsUnavailableException
        val best = bodies(resolved, firstOnly = true).firstOrNull() ?: throw LyricsUnavailableException
        LabeledLyrics(label(best.first, resolved.verified), best.second)
    }

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, album: String?, callback: (String) -> Unit) {
        getAllLabeledLyrics(id, title, artist, duration, album) { callback(it.lyrics) }
    }

    override suspend fun getAllLabeledLyrics(id: String, title: String, artist: String, duration: Int, album: String?, callback: (LabeledLyrics) -> Unit) {
        val resolved = ZemerLyricsClient.resolve(id) ?: return
        bodies(resolved).forEach { callback(LabeledLyrics(label(it.first, resolved.verified), it.second)) }
    }
}
