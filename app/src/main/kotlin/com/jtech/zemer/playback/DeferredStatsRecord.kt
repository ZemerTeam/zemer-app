package com.jtech.zemer.playback

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One offline/cached listen whose live YouTube playback-stats session could NOT fire (no network →
 * no `/player` tracking URLs), captured to push later when the device reconnects. Additive to the
 * live watch-time session ([WatchTimeReporter]): the live path is untouched; this only recovers the
 * plays it deliberately drops.
 *
 * The stored ranges are the SAME real ranges [WatchTimeSegments] computed for the listen — nothing is
 * fabricated. On flush they ride a fresh WEB_REMIX `/player` session verbatim: `st`/`et` the watched
 * ranges, `cmt` the final media position, `rt` the total real watched seconds. All fields are already
 * beacon-shaped (seconds strings) so the flusher does no re-derivation.
 */
@kotlinx.serialization.Serializable
data class DeferredStatsRecord(
    val videoId: String,
    /** Comma-joined range STARTS in media seconds (the watchtime `st` list), verbatim. */
    val st: String,
    /** Comma-joined range ENDS in media seconds (the watchtime `et` list), verbatim. */
    val et: String,
    /** Final media position in seconds (the `cmt` param). */
    val cmt: String,
    /** Total real watched seconds (the `rt` param) — never exceeds the listen's played time. */
    val rt: String,
    /** Wall-clock ms the listen ended — for the staleness cap only (beacons carry no absolute time). */
    val endedAtMs: Long,
) {
    /** Older than [maxAgeMs] relative to [nowMs] — not worth reporting; the flusher drops it. */
    fun isStale(nowMs: Long, maxAgeMs: Long): Boolean = nowMs - endedAtMs > maxAgeMs

    /** The `cmt` for the playback (open) ping: the listen's real START position (first watched range). */
    fun openCmt(): String = st.substringBefore(",").ifBlank { "0.0" }

    fun encode(): String = JSON.encodeToString(this)

    companion object {
        private val JSON = Json { encodeDefaults = true }

        /** Parse one queue line; null on a corrupt/partial line (never crashes the flush). */
        fun decode(line: String): DeferredStatsRecord? =
            try {
                JSON.decodeFromString<DeferredStatsRecord>(line)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
