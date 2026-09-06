package com.jtech.zemer.lyrics.zemer

import com.jtech.zemer.constants.EnableZemerLyricsKey
import com.jtech.zemer.lyrics.LabeledLyrics
import com.jtech.zemer.lyrics.LyricsProvider
import com.jtech.zemer.lyrics.LyricsUtils
import com.jtech.zemer.lyrics.model.LyricsUnavailableException

/**
 * First provider in the chain. Resolves the videoId through the Zemer server, then fetches the text from
 * the sources the server vouches for — in this preference order ([rank]):
 *   zemer (Zemer's own certified text + word timings) > jkaraoke (line-synced LRC) > lrclib / kugou /
 *   musixmatch (synced from the source) > the text pointers jyrics / shironet / zingmusic / youtube / tab4u /
 *   zemirotdb / lyricstranslate (plain, line-synced when the resolver's measured `lineTimes` cover them) >
 *   operator-hosted booklet / manual > canonical / community.
 * Every body goes through the same parser the server used to verify it (golden-pinned ports), so what the
 * user sees is exactly what was cross-checked. A type this build does not know yields nothing and is skipped.
 */
object ZemerLyricsProvider : LyricsProvider {
    override val name = "Zemer"

    override val enabledKey = EnableZemerLyricsKey

    /**
     * Bodies in preference order for a resolved entry (fetchers injected for tests), each paired with its
     * source label suffix. With [firstOnly] the walk stops at the first source that yields a body, so the
     * auto-fetch path does not download and parse every source when only the best one is shown.
     */
    suspend fun bodies(
        resolved: ZemerLyricsClient.Resolved,
        fetch: suspend (String) -> String? = ZemerLyricsClient::fetchText,
        firstOnly: Boolean = false,
        zing: suspend (Long) -> String? = ZemerLyricsClient::zingLyricsHtml,
        youtube: suspend (String) -> String? = ZemerLyricsClient::youtubeLyricsTab,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (s in resolved.sources.sortedBy { rank(it) }) {
            if (firstOnly && out.isNotEmpty()) break
            val body = when (s.type) {
                "zemer" -> s.richSync?.takeIf { it.isNotBlank() } ?: inline(s)
                "jkaraoke" -> s.feedUrl?.let { fetch(it) }?.let { page -> s.songId?.let { id -> JkaraokeLrc.fromFeedPage(page, id, jkaraokeOffset(s))?.synced } }
                "jyrics" -> s.url?.let { fetch(it) }?.let { JyricsParser.parse(it).plain.takeIf(LyricsUtils::hasLyricBody) }
                "shironet" -> s.url?.let { fetch(it) }?.let { ShironetParser.parse(it).plain.takeIf(LyricsUtils::hasLyricBody) }
                // Server-inlined (the site's Cloudflare challenge blocks on-device fetches); the page fetch is
                // a fallback in case an older server serves the pointer form without text.
                "lyricstranslate" -> s.plain?.takeIf(LyricsUtils::hasLyricBody)
                    ?: s.url?.let { fetch(it) }?.let { LyricsTranslateParser.parse(it)?.takeIf(LyricsUtils::hasLyricBody) }
                "zingmusic" -> s.trackId?.let { zing(it) }?.let { ZingParser.toPlain(it).takeIf(LyricsUtils::hasLyricBody) }
                "youtube" -> s.browseId?.let { youtube(it) }?.takeIf(LyricsUtils::hasLyricBody)
                "tab4u" -> s.url?.let { fetch(it) }?.let(Tab4uParser::parse)
                "zemirotdb" -> s.url?.let { fetch(it) }?.let(ZemirotDbParser::parse)
                "lrclib" -> s.trackId?.let { fetch("https://lrclib.net/api/get/$it") }?.let { ZemerLyricsClient.lrclibBody(it) }
                "kugou" -> s.hash?.let { h -> s.krcId?.let { id -> fetch(KugouLrc.searchUrl(h))?.let { KugouLrc.accessKey(it, id) }?.let { key -> fetch(KugouLrc.downloadUrl(id, key))?.let { KugouLrc.lrc(it) } } } }
                "booklet", "manual", "canonical", "community" -> inline(s)
                // Parsed for forward compatibility; the on-device Musixmatch client has no by-id fetch yet and no rows exist.
                else -> null
            }?.let { withLineTimes(s, it, resolved.lineTimes) }
            if (body != null) out += sourceLabel(s) to body
        }
        return out
    }

    /**
     * Karaoke cues lead the voice on most songs but trail it on ~15 %, so only a per-song MEASURED offset is applied;
     * the fleet default would fix most unmeasured songs and worsen the rest, and is treated as zero.
     */
    fun jkaraokeOffset(s: ZemerLyricsClient.Source): Double = if (s.offsetFrom == "measured") s.offsetSec ?: 0.0 else 0.0

    private fun inline(s: ZemerLyricsClient.Source): String? = s.syncedLrc?.takeIf { it.isNotBlank() } ?: s.plain?.takeIf { it.isNotBlank() }

    /** The resolver's measured line times apply to the ONE source they were measured against, and only when they cover its body. */
    private fun withLineTimes(s: ZemerLyricsClient.Source, body: String, lineTimes: ZemerLyricsClient.LineTimes?): String =
        if (lineTimes != null && lineTimes.type == s.type && !LyricsUtils.isSynced(body)) LineTimesLrc.apply(body, lineTimes) ?: body else body

    private fun rank(s: ZemerLyricsClient.Source) = when (s.type) {
        "zemer" -> 0
        "jkaraoke" -> 1
        "lrclib", "kugou", "musixmatch" -> 2
        "jyrics", "shironet", "zingmusic", "youtube", "tab4u", "zemirotdb", "lyricstranslate" -> 3
        "booklet", "manual" -> 4
        "canonical", "community" -> 5
        else -> 9
    }

    /** The label suffix for a source: its type, except a `manual` row names where the text came from. */
    fun sourceLabel(s: ZemerLyricsClient.Source): String =
        if (s.type == "manual" && !s.origin.isNullOrBlank()) originName(s.origin) else s.type

    /** Display names agreed with the server for `manual.origin`; an unknown slug is shown as-is. */
    fun originName(origin: String): String = when (origin) {
        "telegram" -> "Telegram"
        "asrverified" -> "verified"
        else -> origin
    }

    /**
     * "Zemer · jkaraoke": the sub-source matters for provenance; Zemer's own certified text is just "Zemer".
     * Verification is a server fact, not shown in the label.
     */
    fun label(source: String, @Suppress("UNUSED_PARAMETER") verified: Boolean): String = if (source == "zemer") name else "$name · $source"

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
