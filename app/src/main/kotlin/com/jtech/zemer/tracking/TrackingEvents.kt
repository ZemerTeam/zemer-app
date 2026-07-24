package com.jtech.zemer.tracking

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Builders for the wire events of the tracking spec
 * (`handoff-docs/zemer-tracking-app-integration.md`, mirrored in `docs/tracking/README.md`).
 * Field names and types are the exact server contract — the server drops unknown types and skips
 * malformed rows, so these are kept byte-faithful and unit-tested. `t` = epoch millis at event time.
 */
internal object TrackingEvents {

    fun open(t: Long): JsonObject = buildJsonObject {
        put("type", "open")
        put("t", t)
    }

    /**
     * [provider] is a Zemer extension to the base spec (requested in
     * handoff-docs/zemer-tracking-search-provider-request.md; the live server accepts it — verified):
     * which of the app's two search paths served the query — `"zemer"` (search.zemer.io) or
     * `"youtube"` (InnerTube + local whitelist filter). Omitted when unknown; the server stores
     * absent/unknown as NULL, so pre-field builds stay interpretable.
     */
    fun search(t: Long, q: String, results: Int, provider: String? = null): JsonObject = buildJsonObject {
        put("type", "search")
        put("t", t)
        put("q", q)
        put("results", results)
        if (provider != null) put("provider", provider)
    }

    fun click(t: Long, q: String, id: String, kind: String, rank: Int): JsonObject = buildJsonObject {
        put("type", "click")
        put("t", t)
        put("q", q)
        put("id", id)
        put("kind", kind)
        put("rank", rank)
    }

    /**
     * [client]/[player] are Zemer extensions to the base spec (requested in
     * handoff-docs/zemer-tracking-play-client-fields-request.md; the live server accepts the extra
     * fields — verified): the stream client that served the listen (WEB_REMIX, ANDROID_VR, …) and,
     * for deciphered web clients, the player_ias hash — so streaming health can be correlated with
     * client/config rollouts.
     */
    fun play(
        t: Long,
        videoId: String,
        secs: Int,
        dur: Int?,
        source: String,
        client: String? = null,
        player: String? = null,
    ): JsonObject = buildJsonObject {
        put("type", "play")
        put("t", t)
        put("videoId", videoId)
        put("secs", secs)
        if (dur != null) put("dur", dur)
        put("source", source)
        if (client != null) put("client", client)
        if (player != null) put("player", player)
    }

    fun action(t: Long, kind: String, id: String): JsonObject = buildJsonObject {
        put("type", "action")
        put("t", t)
        put("kind", kind)
        put("id", id)
    }

    /**
     * One-time local listen-history backfill row (contract:
     * handoff-docs/zemer-tracking-history-backfill-request.md — SHIPPED server-side). [t] is the
     * ORIGINAL listen time: the server stores this type unclamped (now−3y..now+5min), segregated
     * from live plays, deduped on (device, videoId, t).
     */
    fun playBackfill(t: Long, videoId: String, secs: Int): JsonObject = buildJsonObject {
        put("type", "play_backfill")
        put("t", t)
        put("videoId", videoId)
        put("secs", secs)
    }

    /**
     * One-time favorite/download snapshot backfill row (contract:
     * handoff-docs/zemer-tracking-action-backfill-request.md — SETTLED, server-side built). [t]
     * is the ORIGINAL action time; the server accepts now−10y..now+5min (wider floor than plays —
     * an old likedDate on a still-liked song is a long-standing favorite, not stale data), stores
     * it segregated from live actions, dedupes on (device, kind, id, t), and skips out-of-window
     * rows PER-ROW (never a batch-level failure). [kind] is restricted server-side to
     * favorite|download — the other action kinds have no durable timestamp to backfill.
     */
    fun actionBackfill(t: Long, kind: String, id: String): JsonObject = buildJsonObject {
        put("type", "action_backfill")
        put("t", t)
        put("kind", kind)
        put("id", id)
    }

    /**
     * The videoIds of one rendered row/screen — what the app SHOWED, the denominator the ranking
     * side's exposure dampener divides plays by (contract:
     * `docs/tracking/README.md` §impressions — SETTLED, server-side shipped). [ids] must already be
     * filtered and chunked by [impressionChunks]; [surface] is omitted when it doesn't match the
     * server's slug alphabet, which stores absent/invalid as NULL while still counting the ids.
     */
    fun impression(t: Long, ids: List<String>, surface: String?): JsonObject = buildJsonObject {
        put("type", "impression")
        put("t", t)
        putJsonArray("ids") { ids.forEach { add(it) } }
        if (surface != null && isTrackingSurface(surface)) put("surface", surface)
    }
}

/**
 * Where an impression was rendered. Same alphabet as [PlaySource] but NOT the same taxonomy —
 * `play.source` is the queue context that got played, this is the row the user looked at, and the
 * two are deliberately not joinable (surface-level CTR was dropped from scope for exactly that
 * reason). These slugs are the server's declared-surface gate vocabulary: **renaming one reads as a
 * surface disappearing** and re-closes the gate, so they are append-only in practice.
 */
object TrackingSurface {
    const val SEARCH = "search"
    const val NEW = "new"

    /** One slug per home row, not a flat `home:top` — per-placement exposure costs us nothing. */
    fun home(section: String) = "home:$section"

    fun artist(id: String) = "artist:$id"
    fun album(id: String) = "album:$id"
    fun playlist(id: String) = "playlist:$id"
    fun zemer(id: String) = "zemer:$id"
}

/** At most this many ids per impression event; the server truncates an event beyond it. */
internal const val MAX_IMPRESSION_IDS = 50

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val SURFACE_SLUG = Regex("^[\\w:.\\-]{1,80}$")

/** The server drops anything that isn't an 11-char videoId, so never spend a row on one. */
internal fun isVideoId(value: String): Boolean = VIDEO_ID.matches(value)

/** The `surface` slug alphabet the server enforces; anything else is stored as NULL. */
internal fun isTrackingSurface(value: String): Boolean = SURFACE_SLUG.matches(value)

/**
 * Filters [ids] to real videoIds, drops repeats within the row, and splits into events of at most
 * [max] ids. The chunking is deliberately OURS: the server truncates an over-long event by keeping
 * its HEAD, which would quietly over-count the start of every long row and under-count its tail.
 */
internal fun impressionChunks(ids: Iterable<String>, max: Int = MAX_IMPRESSION_IDS): List<List<String>> =
    ids.filter(::isVideoId).distinct().chunked(max)

/** The server stores at most this many impression rows per POST, dropping whole events past it. */
internal const val MAX_IMPRESSION_ROWS_PER_POST = 500

private val IMPRESSION_PREFIX = "{\"type\":\"impression\""

/** How many impression rows one queued event line would store; 0 for every other event type. */
internal fun impressionRowCount(eventLine: String): Int {
    if (!eventLine.startsWith(IMPRESSION_PREFIX)) return 0
    return runCatching {
        Json.parseToJsonElement(eventLine).jsonObject["ids"]?.jsonArray?.size ?: 0
    }.getOrDefault(0)
}

/**
 * Truncates a queued [batch] so one POST never carries more than [max] impression rows.
 *
 * Without this the drain is event-counted (up to 100) while the server's limit is row-counted, so a
 * scroll-heavy queue can POST thousands of rows and the server silently drops everything past 500.
 * That loss is NOT song-independent — it always falls on the events queued LAST, i.e. whatever the
 * user scrolled to most recently — so it would bias exposure rather than merely thinning it.
 *
 * The result is always a PREFIX of [batch], which [TrackingQueue.removeBatch] relies on to align an
 * uploaded batch against the queue head. Non-impression events before the cut still travel; the
 * remainder simply goes out on the next flush, which costs nothing at 30 batches/minute.
 */
internal fun capImpressionRows(batch: List<String>, max: Int = MAX_IMPRESSION_ROWS_PER_POST): List<String> {
    var rows = 0
    val kept = batch.takeWhile { line ->
        rows += impressionRowCount(line)
        rows <= max
    }
    // takeWhile drops the event that crossed the limit; keep at least one so a single oversized
    // event (impossible at 50 ids/event today) can never stall the queue forever.
    return kept.ifEmpty { batch.take(1) }
}

/** The `action` kinds the server accepts. */
internal object TrackingActionKind {
    const val FAVORITE = "favorite"
    const val UNFAVORITE = "unfavorite"
    const val DOWNLOAD = "download"
    const val ADD_PLAYLIST = "add_playlist"
    const val SHARE = "share"
}

/**
 * Batch body: `{"device":…,"app_ver":…,"debug":…,"events":[…]}` from already-encoded event lines.
 * [debug] = `BuildConfig.DEBUG`: debug builds run the identical client path, but the server ACKs
 * and DISCARDS their batches (responding `debug:true`) so test devices never pollute the stats.
 */
internal fun trackingBatchBody(device: String, appVer: String, debug: Boolean, eventLines: List<String>): String =
    buildString {
        append("{\"device\":")
        append(JsonPrimitive(device))
        append(",\"app_ver\":")
        append(JsonPrimitive(appVer))
        append(",\"debug\":")
        append(debug)
        append(",\"events\":[")
        eventLines.joinTo(this, ",")
        append("]}")
    }
