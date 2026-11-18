package com.jtech.zemer.search

import kotlinx.serialization.Serializable

/**
 * Wire models for **Zemer Stations** — synchronized broadcast radio (`GET /stations`,
 * `GET /station?id=`), per the handoff doc `handoff-docs/zemer-app-stations.md`. One
 * shared, server-programmed wall-clock schedule per station: every listener hears the SAME track at
 * the SAME moment; the client's only job is to join at the live offset and keep step (see
 * [stationJoinPositionMs]). Field names match the JSON exactly; everything the server may omit is
 * nullable/defaulted so a sparse row never fails deserialization (the shared lenient reader).
 *
 * Content policy (handoff §6): the pools are pre-filtered server-side to the strictest common
 * denominator, so the content flags are NOT sent to these endpoints, and the row is hidden in
 * kidZone mode. Responses are clock-dependent — never cache them. Stations are LIVE-ONLY: never
 * served from the offline snapshot (like `/playlist`).
 */
@Serializable
data class ZemerStationsResponse(
    val count: Int = 0,
    val stations: List<ZemerStation> = emptyList(),
    /** Server clock at response time — measure local skew against it (see [stationSkewMs]). */
    val serverTimeMs: Long = 0,
)

@Serializable
data class ZemerStation(
    val id: String = "",
    val title: String = "",
    /** Branded broadcast SVG cover; RELATIVE — resolve via `resolveZemerUrl` before rendering. */
    val thumbnail: String? = null,
    /** Only `live == true` stations render; a not-live station is hidden, not greyed. */
    val live: Boolean = false,
    val nowPlaying: ZemerStationNowPlaying? = null,
)

/** The row's "Now: ‹title› — ‹artist›" line; refreshed on load and every 60s while on screen. */
@Serializable
data class ZemerStationNowPlaying(
    val title: String = "",
    val artist: String = "",
    val thumbnail: String? = null,
)

/** `GET /station?id=&next=` — the tune-in payload: the on-air track + the upcoming schedule runway. */
@Serializable
data class ZemerStationTuneInResponse(
    val station: ZemerStation = ZemerStation(),
    /** Server clock at response time — the skew reference for the §4 sync math. */
    val serverTimeMs: Long = 0,
    /** Schedule runway remaining; an ops signal, not needed for playback. */
    val horizonMs: Long = 0,
    val now: ZemerStationEntry? = null,
    val next: List<ZemerStationEntry> = emptyList(),
)

/** One scheduled slot. [offsetMs] is present on `now` only (where the broadcast is right now). */
@Serializable
data class ZemerStationEntry(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "",
    val artistId: String? = null,
    /** Square album art (absolute URL); null for coverless standalones. */
    val thumbnail: String? = null,
    val durationSec: Int? = null,
    /** Wall-clock broadcast window (server clock). */
    val startMs: Long = 0,
    val endMs: Long = 0,
    /**
     * Present on `now` only. MAY BE NEGATIVE (contract addendum): when the on-air entry was just
     * taken down, the next servable entry is served as `now` and a negative offset means "this
     * track starts in |offset| ms" — see [stationStartPositionMs].
     */
    val offsetMs: Long? = null,
)

/**
 * The renderable station cards: only `live == true` stations with a real id (a not-live or sparse
 * row is hidden, never greyed), de-duped for the id-keyed lazy row, thumbnails resolved absolute via
 * [resolveUrl] (the covers arrive host-relative). An empty result hides the whole row — the same
 * fail-soft convention as `/home-rows`.
 */
fun ZemerStationsResponse.liveStations(resolveUrl: (String?) -> String?): List<ZemerStation> =
    stations
        .filter { it.live && it.id.isNotBlank() }
        .distinctBy { it.id }
        .map { it.copy(thumbnail = resolveUrl(it.thumbnail)) }

// ── The §4 tune-in clock math (pure; unit-tested) ────────────────────────────────────────────────

/** Local-to-server clock skew: add this to a local `now` to get server wall-clock time. */
fun stationSkewMs(serverTimeMs: Long, localTimeMs: Long): Long = serverTimeMs - localTimeMs

/**
 * Where the broadcast is RIGHT NOW inside [entry], computed at [localNowMs] with the measured
 * [skewMs]: `(local now + skew) − startMs`. Compute it at the moment playback actually starts, so
 * fetch/prepare latency is absorbed by the seek instead of accumulating as drift. NEGATIVE when the
 * entry has not started yet (the addendum's just-taken-down case) — feed it through
 * [stationStartPositionMs] before seeking.
 */
fun stationJoinPositionMs(entry: ZemerStationEntry, skewMs: Long, localNowMs: Long): Long =
    (localNowMs + skewMs) - entry.startMs

/**
 * The position to actually seek to: a negative join position means the entry starts in |value| ms
 * (contract addendum) — of the two sanctioned handlings (wait it out vs start at 0 immediately) we
 * take START AT 0: it is simpler, the gap is a rare sub-track-length sliver, and silence on tune-in
 * reads as broken. The resulting few seconds of head-start decay at the next boundary correction.
 */
fun stationStartPositionMs(joinPositionMs: Long): Long = maxOf(0L, joinPositionMs)

/**
 * §4 join rule: joining within the last [STATION_DYING_TRACK_MS] of a track is not worth it — skip
 * straight to the first `next` entry instead (which by then is at/near its own start).
 */
fun stationShouldSkipDyingTrack(entry: ZemerStationEntry, skewMs: Long, localNowMs: Long): Boolean =
    entry.endMs - (localNowMs + skewMs) <= STATION_DYING_TRACK_MS

/**
 * The live offset inside [entry] IF it is on-air right now, else null: negative join = the slot
 * has not started (we are ahead of the broadcast — wait, don't play), beyond the slot length = it
 * already ended (we are behind — move on). THE primitive the bidirectional resync is built on: the
 * caller seeks forward when behind, waits when ahead, and re-tunes when nothing queued is on-air.
 */
fun stationOnAirOffsetMs(entry: ZemerStationEntry, skewMs: Long, localNowMs: Long): Long? {
    val join = stationJoinPositionMs(entry, skewMs, localNowMs)
    return join.takeIf { it >= 0 && it < entry.endMs - entry.startMs }
}

/** Don't join a track with less than this left (handoff §4: "the last ~5s"). */
const val STATION_DYING_TRACK_MS = 5_000L

/** Tolerated broadcast drift before a boundary correction (handoff §4: "~3s"). */
const val STATION_MAX_DRIFT_MS = 3_000L

/** The shortest gap between two re-tunes of a station: a re-tune that cannot help must never spin. */
const val STATION_RETUNE_MIN_INTERVAL_MS = 5_000L

/**
 * §4 tune-in: the `now` entry to join at its live offset (null when it is dying or excluded) and the
 * entries to queue. [excluded] covers the blocked-ids table and slots that already failed to stream on
 * this device, so a re-tune never rejoins a slot it just gave up on.
 */
fun stationTuneInPlan(
    now: ZemerStationEntry?,
    next: List<ZemerStationEntry>,
    skewMs: Long,
    localNowMs: Long,
    excluded: (ZemerStationEntry) -> Boolean,
): StationTuneInPlan {
    val joinNow = now
        ?.takeUnless { stationShouldSkipDyingTrack(it, skewMs, localNowMs) }
        ?.takeUnless(excluded)
    return StationTuneInPlan(joinNow, listOfNotNull(joinNow) + next.filterNot(excluded))
}

data class StationTuneInPlan(val joinNow: ZemerStationEntry?, val entries: List<ZemerStationEntry>)

/** One queued slot as the resync sees it: its live offset when on-air and playable, and how long until it starts. */
data class StationSlotState(val onAirOffsetMs: Long?, val msUntilStart: Long?)

/** What the boundary resync does. */
sealed interface StationResync {
    /** Already on the on-air slot, within the tolerated drift (or about to start): leave it. */
    data object Stay : StationResync

    /** Queued slot [index] is on-air: seek to [positionMs] in it. */
    data class Seek(val index: Int, val positionMs: Long) : StationResync

    /** Nothing playable is on-air; queued slot [index] starts in [untilStartMs]: hold there until then. */
    data class WaitFor(val index: Int, val untilStartMs: Long) : StationResync

    /** Nothing queued is on-air or still to come (paused past the runway, stale schedule): re-tune. */
    data object Retune : StationResync
}

/**
 * The bidirectional resync decision over the queued [slots] from [currentIndex] on: seek to the first
 * on-air slot when behind or drifted; otherwise hold for the first queued slot that has not started,
 * which is a later slot when the on-air one could not be played here (its offset is null); re-tune
 * only when nothing queued is on-air or upcoming.
 */
fun stationResync(
    slots: List<StationSlotState>,
    currentIndex: Int,
    currentPositionMs: Long,
    maxDriftMs: Long = STATION_MAX_DRIFT_MS,
): StationResync {
    val from = currentIndex.coerceAtLeast(0)
    for (index in from until slots.size) {
        val live = slots[index].onAirOffsetMs ?: continue
        val inPlace = index == currentIndex && kotlin.math.abs(currentPositionMs - live) <= maxDriftMs
        return if (inPlace) StationResync.Stay else StationResync.Seek(index, live)
    }
    for (index in from until slots.size) {
        val untilStart = slots[index].msUntilStart?.takeIf { it > 0 } ?: continue
        val startsSoon = index == currentIndex && untilStart <= maxDriftMs
        return if (startsSoon) StationResync.Stay else StationResync.WaitFor(index, untilStart)
    }
    return StationResync.Retune
}

/** How long to hold a re-tune so two re-tunes are at least [minIntervalMs] apart (0 = re-tune now). */
fun stationRetuneDelayMs(
    lastRetuneAtMs: Long?,
    nowMs: Long,
    minIntervalMs: Long = STATION_RETUNE_MIN_INTERVAL_MS,
): Long = lastRetuneAtMs?.let { (minIntervalMs - (nowMs - it)).coerceAtLeast(0L) } ?: 0L
