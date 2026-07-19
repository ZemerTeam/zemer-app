package com.jtech.zemer.tracking

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builders for the five wire events of the tracking spec
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
