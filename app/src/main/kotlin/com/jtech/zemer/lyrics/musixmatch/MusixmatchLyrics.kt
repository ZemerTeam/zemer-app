package com.jtech.zemer.lyrics.musixmatch

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.constants.MusixmatchCooldownUntilKey
import com.jtech.zemer.constants.MusixmatchLastStatusKey
import com.jtech.zemer.constants.MusixmatchTokenKey
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.Normalizer
import kotlin.math.abs

/**
 * Musixmatch (the catalog behind Spotify's lyrics) — queried ON-DEVICE, last in the provider chain, with the
 * gates of zemer-search `harvester/lyrics-musixmatch.mjs` mirrored here (the server stores no Musixmatch text
 * by policy; every phone uses its own token for its own few songs, which never trips the captcha gate).
 *
 * Gates (accuracy over coverage):
 *  - artist: Musixmatch artist name == ours by consonant key (Chaim/Haim fold)
 *  - title: a normalised form identical, or consonant keys equal with >= 5 chars (the server's 1 / 0.9 rules)
 *  - length: |track_length − duration| <= [DUR_TOL] when Musixmatch reports one (text is per song); the LRC
 *    only within [SYNC_TOL] — a drifting sync is worse than none
 *  - text: >= 4 lines, not instrumental/restricted, the licence footer stripped; LRC only when monotonic
 */
object MusixmatchLyrics {
    const val DUR_TOL = 2
    const val SYNC_TOL = 1
    private const val BASE = "https://apic-desktop.musixmatch.com/ws/1.1/"
    private const val APP = "web-desktop-app-v1.0"
    private const val COOLDOWN_MS = 30 * 60_000L   // token issuance refused: try again in half an hour (issuance is per-IP rate-limited)

    data class Track(val trackName: String, val artistName: String, val trackLength: Int, val commontrackId: Long, val trackId: Long)
    data class Judged(val plain: String, val synced: String?, val ref: String)

    // ---- pure helpers (mirror corpus/lyrics.mjs normTitle/consonantKey and the harvester's cleaners) ----
    private val NIQQUD = Regex("""[֑-ׇ]""")
    private val QUOTES = Regex("""[’'`"״׳]""")
    private val BRACKETS = Regex("""\(.*?\)|\[.*?]""")
    private val NON_WORD = Regex("""[^a-z0-9א-ת ]+""")
    private val SPACES = Regex("""\s+""")

    fun normTitle(s: String?): String = Normalizer.normalize(s ?: "", Normalizer.Form.NFKD).replace(NIQQUD, "").lowercase()
        .replace(QUOTES, "").replace(BRACKETS, " ").replace(NON_WORD, " ").replace(SPACES, " ").trim()

    fun consonantKey(s: String?): String = normTitle(s)
        .replace(Regex("""\bh\b"""), "").replace(Regex("""h\b"""), "").replace(Regex("ch|kh"), "k").replace("th", "t")
        .replace(Regex("ei|ai|ey|ay"), "e").replace(Regex("oi|oy"), "o").replace(Regex("ou|oo"), "u").replace(Regex("tz|ts|z"), "s")
        .replace(Regex("""s\b"""), "t").replace("w", "v").replace(Regex("""([a-z])\1"""), "$1").replace(Regex("[aeiou]"), "")
        .replace(SPACES, " ").trim()

    fun cleanTitle(t: String?): String = (t ?: "").replace(BRACKETS, "")
        .replace(Regex("""\s+-\s+(live|לייב|acoustic|remix|official.*|cover.*|feat\..*|\d{4})$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^([^-]+?)\s+-\s+[א-ת][^-]*$"""), "$1").replace(Regex("""^([א-ת][^-]*?)\s+-\s+[A-Za-z][^-]*$"""), "$1")
        .replace(SPACES, " ").trim()

    fun cleanArtist(a: String?): String = (a ?: "").replace(Regex("""\s+(&|feat\.?|ft\.?|x|and|,)\s+.*$""", RegexOption.IGNORE_CASE), "").trim()

    private fun artistKey(s: String?): String = consonantKey(normTitle(s).replace(Regex("^h(?=[aeiou])"), "ch"))

    fun artistMatches(mxmArtist: String, names: List<String?>): Boolean {
        val k = artistKey(mxmArtist)
        return k.length >= 3 && names.filterNotNull().any { artistKey(it) == k || artistKey(cleanArtist(it)) == k }
    }

    /** Server's sameSongScore >= 0.9: identical normalised form, or equal consonant keys of >= 5 chars. */
    fun titleMatches(mxmTitle: String, titles: List<String?>): Boolean {
        val n = normTitle(mxmTitle); val k = consonantKey(mxmTitle)
        return titles.filterNotNull().any { t -> (n.isNotEmpty() && normTitle(t) == n) || (k.length >= 5 && consonantKey(t) == k) }
    }

    fun cleanLyrics(body: String?): String {
        val out = ArrayList<String>()
        for (raw in (body ?: "").replace("\r", "").split("\n")) {
            val l = raw.replace(Regex("""[ \t ]+"""), " ").trim()
            if (Regex("""^\*+.*(not for commercial|lyrics powered by|musixmatch)""", RegexOption.IGNORE_CASE).containsMatchIn(l) || Regex("""^\(\d+\)$""").matches(l)) continue
            if (l.isNotEmpty() || (out.isNotEmpty() && out.last().isNotEmpty())) out.add(l)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return out.joinToString("\n")
    }

    /** Musixmatch LRC subtitles: keep only well-formed, monotonic lines; null unless >= 4 sung lines. */
    fun cleanLrc(sub: String?): String? {
        val rows = ArrayList<String>(); var last = -1.0
        for (l in (sub ?: "").split("\n")) {
            val m = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s?(.*)$""").find(l) ?: continue
            val t = m.groupValues[1].toInt() * 60 + m.groupValues[2].toDouble()
            if (t < last) return null
            last = t
            rows.add("[%s:%05.2f] %s".format(m.groupValues[1].padStart(2, '0'), m.groupValues[2].toDouble(), m.groupValues[3].trim()))
        }
        return if (rows.count { !it.endsWith("] ") && !it.endsWith("]") } >= 4) rows.joinToString("\n") else null
    }

    fun judge(track: Track?, lyricsBody: String?, instrumental: Boolean, restricted: Boolean, subtitle: String?, title: String, altTitle: String?, artist: String, altArtist: String?, duration: Int): Judged? {
        if (track == null || lyricsBody.isNullOrBlank() || instrumental || restricted) return null
        if (!artistMatches(track.artistName, listOf(artist, altArtist))) return null
        if (!titleMatches(track.trackName, listOf(title, altTitle))) return null
        val d = if (track.trackLength > 0 && duration > 0) abs(track.trackLength - duration) else -1
        if (d > DUR_TOL) return null
        val plain = cleanLyrics(lyricsBody)
        if (plain.lines().count { it.isNotBlank() } < 4) return null
        val synced = if (d in 0..SYNC_TOL) cleanLrc(subtitle) else null
        return Judged(plain, synced, "mxm:${track.commontrackId}@${track.trackId}")
    }

    // ---- network ----
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 15000; connectTimeoutMillis = 10000; socketTimeoutMillis = 15000 }
            expectSuccess = false
        }
    }
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private suspend fun getJson(url: String): JsonObject? = runCatching {
        json.parseToJsonElement(client.get(url) { header(HttpHeaders.UserAgent, UA); header(HttpHeaders.Cookie, "x-mxm-token-guid=") }.bodyAsText()).jsonObject
    }.getOrNull()

    private fun JsonObject?.header(): JsonObject? = this?.get("message")?.jsonObject?.get("header")?.jsonObject
    private fun JsonObject?.body(): JsonObject? = this?.get("message")?.jsonObject?.get("body")?.let { runCatching { it.jsonObject }.getOrNull() }

    private suspend fun token(context: Context, forceNew: Boolean = false): String? {
        val stored = context.dataStore[MusixmatchTokenKey]?.takeIf { it.isNotBlank() }
        if (!forceNew && stored != null) return stored
        // Prefer the token the Zemer server brokers: it issues from one clean IP, so a phone (whose IP Musixmatch
        // often refuses) never touches issuance. forceNew means our stored token went stale → ask for a renew.
        runCatching { ZemerLyricsClient.musixmatchToken(renew = if (forceNew) stored else null) }.getOrNull()?.let {
            context.dataStore.edit { p -> p[MusixmatchTokenKey] = it; p.remove(MusixmatchCooldownUntilKey) }
            return it
        }
        // Fallback: issue directly (works off the home network). Guarded by a short cooldown when refused.
        val cooldown = context.dataStore[MusixmatchCooldownUntilKey] ?: 0L
        if (cooldown > System.currentTimeMillis() + COOLDOWN_MS) context.dataStore.edit { it.remove(MusixmatchCooldownUntilKey) }
        else if (cooldown > System.currentTimeMillis()) return stored
        val j = getJson("${BASE}token.get?app_id=$APP")
        val tok = j.body()?.get("user_token")?.jsonPrimitive?.contentOrNull
        context.dataStore.edit { if (tok != null) it[MusixmatchTokenKey] = tok else { it.remove(MusixmatchTokenKey); it[MusixmatchCooldownUntilKey] = System.currentTimeMillis() + COOLDOWN_MS } }
        return tok
    }

    /** Outcome of one lookup, also recorded in `MusixmatchLastStatusKey` so Content settings can show why a song had nothing. */
    sealed class Outcome(val status: String) {
        class Hit(val judged: Judged) : Outcome("ok" + if (judged.synced != null) " synced" else "")
        object Unauthorized : Outcome("token quota reached (captcha)")
        object Network : Outcome("network error")
        object NoMatch : Outcome("no matching track")
        object NoLyrics : Outcome("track known, no lyrics on file")
        class Rejected(why: String) : Outcome("rejected: $why")
    }

    /** One gated lookup with an explicit token (pure network + gates; live-testable off-device). */
    suspend fun fetch(token: String, title: String, artist: String, duration: Int): Outcome {
        val q = listOf(
            "app_id" to APP, "usertoken" to token, "q_track" to cleanTitle(title), "q_artist" to cleanArtist(artist),
            "q_duration" to (duration.takeIf { it > 0 }?.toString() ?: ""), "f_subtitle_length" to (duration.takeIf { it > 0 }?.toString() ?: ""),
            "f_subtitle_length_max_deviation" to "1", "subtitle_format" to "lrc", "format" to "json",
        ).joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        val j = getJson("${BASE}macro.subtitles.get?$q") ?: return Outcome.Network
        if (j.header()?.get("status_code")?.jsonPrimitive?.intOrNull == 401) return Outcome.Unauthorized
        val (track, ly, st) = parseMacro(j) ?: return Outcome.Network
        if (track == null) return Outcome.NoMatch
        if (ly.body.isNullOrBlank()) return Outcome.NoLyrics
        if (ly.instrumental || ly.restricted) return Outcome.Rejected(if (ly.instrumental) "instrumental" else "restricted")
        if (!artistMatches(track.artistName, listOf(artist))) return Outcome.Rejected("artist ${track.artistName}")
        if (!titleMatches(track.trackName, listOf(title))) return Outcome.Rejected("title ${track.trackName}")
        val judged = judge(track, ly.body, ly.instrumental, ly.restricted, st, title, null, artist, null, duration) ?: return Outcome.Rejected("length ${track.trackLength}s vs ${duration}s")
        return Outcome.Hit(judged)
    }

    /** The gated body for this song: LRC when the recording length matched within 1 s, else plain text; null = not found. */
    suspend fun getLyrics(context: Context, title: String, artist: String, duration: Int): Judged? {
        val tok = token(context) ?: run { record(context, "no token (issuance refused, retry in 30 min)"); return null }
        var out = fetch(tok, title, artist, duration)
        // A token is good for roughly a dozen lookups (measured 2026-09-02), then every reply is a 401 "captcha":
        // fetch a fresh token and retry once; when issuance is refused too, back off (never a 6 h blackout).
        if (out is Outcome.Unauthorized) {
            val fresh = token(context, forceNew = true)
            out = if (fresh != null) fetch(fresh, title, artist, duration) else out
        }
        record(context, out.status)
        return (out as? Outcome.Hit)?.judged
    }

    private suspend fun record(context: Context, status: String) { runCatching { context.dataStore.edit { it[MusixmatchLastStatusKey] = status } } }

    class LyricsPayload(val body: String?, val instrumental: Boolean, val restricted: Boolean)

    /** The three macro calls of a `macro.subtitles.get` reply (fixture-tested): matched track, lyrics payload, first LRC subtitle body. */
    fun parseMacro(j: JsonObject): Triple<Track?, LyricsPayload, String?>? {
        val calls = j.body()?.get("macro_calls")?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        val tr = calls["matcher.track.get"]?.let { runCatching { it.jsonObject }.getOrNull() }.body()?.get("track")?.let { runCatching { it.jsonObject }.getOrNull() }
        val ly = calls["track.lyrics.get"]?.let { runCatching { it.jsonObject }.getOrNull() }.body()?.get("lyrics")?.let { runCatching { it.jsonObject }.getOrNull() }
        val st = calls["track.subtitles.get"]?.let { runCatching { it.jsonObject }.getOrNull() }.body()?.get("subtitle_list")
            ?.let { runCatching { it.jsonArray.firstOrNull()?.jsonObject?.get("subtitle")?.jsonObject?.get("subtitle_body")?.jsonPrimitive?.contentOrNull }.getOrNull() }
        val track = tr?.let {
            Track(
                it["track_name"]?.jsonPrimitive?.contentOrNull ?: "", it["artist_name"]?.jsonPrimitive?.contentOrNull ?: "",
                it["track_length"]?.jsonPrimitive?.intOrNull ?: 0, it["commontrack_id"]?.jsonPrimitive?.longOrNull ?: 0L, it["track_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
        return Triple(track, LyricsPayload(ly?.get("lyrics_body")?.jsonPrimitive?.contentOrNull, (ly?.get("instrumental")?.jsonPrimitive?.intOrNull ?: 0) == 1, (ly?.get("restricted")?.jsonPrimitive?.intOrNull ?: 0) == 1), st)
    }

    fun parseMacro(raw: String): Triple<Track?, LyricsPayload, String?>? = runCatching { parseMacro(json.parseToJsonElement(raw).jsonObject) }.getOrNull()
}
