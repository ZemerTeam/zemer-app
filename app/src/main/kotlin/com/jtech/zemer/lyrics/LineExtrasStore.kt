package com.jtech.zemer.lyrics

import android.content.Context
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What [LyricsStore] needs from the extras storage; the file store below is the app binding, tests use a map. */
interface LineExtrasStorage {
    /** The stored record for [videoId], or null when the song was never resolved for extras. */
    fun read(videoId: String): LineExtrasRecord?

    /** Records the resolver's answer for [videoId] ([wire] null = the song has no extras, a negative cache with a date). */
    fun write(videoId: String, wire: ZemerLyricsClient.LineExtras?, now: Long = System.currentTimeMillis())

    /** Records an aligned `/lyrics/extras` reply for [videoId] in [lang], made for the displayed body [linesHash] ([wire] null = 404, a dated negative). */
    fun writeAligned(videoId: String, lang: String, linesHash: String, wire: ZemerLyricsClient.LineExtras?, now: Long = System.currentTimeMillis())

    /** Forgets [videoId] (a refetch: the fresh chain answer re-records or clears it). */
    fun delete(videoId: String)
}

/** One aligned reply: for which displayed body it was made, when, and what came back (null = the server holds nothing). */
@Serializable
data class AlignedExtras(val linesHash: String, val checkedAt: Long, val extras: ZemerLyricsClient.LineExtras? = null)

/**
 * One song's stored extras: the resolve-time [extras] (keyed to the server's verified text, the zero-network
 * fast path) plus the [aligned] replies per wire language (keyed to the lines the app displayed).
 */
@Serializable
data class LineExtrasRecord(val checkedAt: Long, val extras: ZemerLyricsClient.LineExtras? = null, val aligned: Map<String, AlignedExtras> = emptyMap()) {
    /** A song with NO extras is re-asked after [LineExtrasStore.EMPTY_TTL_MS] (the server keeps adding translations); a song WITH extras only on refetch. */
    fun isStale(now: Long): Boolean = extras == null && now - checkedAt > LineExtrasStore.EMPTY_TTL_MS

    /** Whether the aligned reply for [lang] still answers the displayed body [linesHash] (a negative re-asks after the TTL). */
    fun alignedCurrent(lang: String, linesHash: String, now: Long): Boolean {
        val a = aligned[lang] ?: return false
        return a.linesHash == linesHash && (a.extras != null || now - a.checkedAt <= LineExtrasStore.EMPTY_TTL_MS)
    }
}

/**
 * The per-song extras cache: one small JSON file per videoId under `filesDir/lyrics-extras/`. Deliberately not
 * a Room column (the lyrics table takes no migrations) and not one DataStore blob (thousands of songs would
 * re-serialize on every write). A record never outlives its lyrics row: [LyricsStore] overwrites it on every
 * chain answer and drops it on refetch, since a text re-verification changes the server's keys.
 */
@Singleton
class LineExtrasStore(private val dir: File) : LineExtrasStorage {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIR_NAME))

    private val changes = MutableStateFlow(0L)

    override fun read(videoId: String): LineExtrasRecord? {
        val file = fileFor(videoId) ?: return null
        if (!file.isFile) return null
        return runCatching { json.decodeFromString(LineExtrasRecord.serializer(), file.readText()) }
            .onFailure { Timber.w(it, "lyrics extras: unreadable record for %s", videoId); file.delete() }
            .getOrNull()
    }

    override fun write(videoId: String, wire: ZemerLyricsClient.LineExtras?, now: Long) {
        // A chain answer replaces the resolve-time extras but keeps aligned replies made for a body that still matches.
        store(videoId, LineExtrasRecord(now, wire, read(videoId)?.aligned.orEmpty()))
    }

    override fun writeAligned(videoId: String, lang: String, linesHash: String, wire: ZemerLyricsClient.LineExtras?, now: Long) {
        val current = read(videoId) ?: LineExtrasRecord(now)
        store(videoId, current.copy(aligned = current.aligned + (lang to AlignedExtras(linesHash, now, wire))))
    }

    private fun store(videoId: String, record: LineExtrasRecord) {
        val file = fileFor(videoId) ?: return
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LineExtrasRecord.serializer(), record))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }.onFailure { Timber.w(it, "lyrics extras: write failed for %s", videoId) }
        changes.value = changes.value + 1
    }

    override fun delete(videoId: String) {
        fileFor(videoId)?.delete()
        changes.value = changes.value + 1
    }

    /**
     * The extras to render for [videoId] in [language] under the displayed [lines], re-read on every store change:
     * the aligned reply made for exactly this body when there is one, else the resolve-time extras (they pair only
     * where the displayed text is the server's own). Null while nothing is recorded.
     */
    fun flow(videoId: String, language: LineExtrasLanguage, lines: List<String>): Flow<LineExtras?> {
        val lang = language.wireLang
        val hash = LineExtras.linesHash(lines)
        return changes.map {
            val record = read(videoId)
            val aligned = lang?.let { record?.aligned?.get(it) }?.takeIf { it.linesHash == hash }
            if (aligned != null) LineExtras.aligned(lines, aligned.extras) else LineExtras.from(record?.extras)
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    /** A videoId is `[A-Za-z0-9_-]{11}`; anything else never touches the filesystem. */
    private fun fileFor(videoId: String): File? = if (SAFE_ID.matches(videoId)) File(dir, "$videoId.json") else null

    companion object {
        const val DIR_NAME = "lyrics-extras"
        const val EMPTY_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
