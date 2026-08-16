package com.jtech.zemer.playback

import androidx.media3.common.Player
import com.jtech.zemer.extensions.metadata
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Emulates a genuine YouTube Music playback-stats session for every DIRECT play — music, video-songs
 * and podcast episodes alike.
 *
 * One session per listen, keyed by one cpn: a playback ping when the listen actually starts
 * (`cmt=<start position>`, `final=0`), watchtime pings at the server's scheduled cadence (see
 * [WatchTimeSchedule]) plus on pause/seek, and a `final=1` watchtime ping when the listen ends. Every
 * reported range comes from
 * [WatchTimeSegments] fed with real player positions — nothing is ever fabricated (the spec's hard
 * rule: fabricated watch time is invalid traffic).
 *
 * Deliberately NOT running: RELAY mode (the spec's other hard rule — beacons must never ride the
 * relay egress) and cast sessions (the receiver plays, not this device). A video-mode rendition swap
 * and its repeat-one loop never reach [onTransition] (MusicService's own-swap early return), so the
 * session correctly spans the whole listen across audio/video swaps.
 *
 * Extracted from [MusicService] (the giant-shrinking rule), mirroring [EpisodePositionTracker]:
 * session state is confined to [scope] (the service main scope); only the tracking-URL cache is
 * concurrent (seeded from the data-source resolver thread). Beacons are fire-and-forget: a network
 * failure logs and moves on — stats must never affect playback.
 */
class WatchTimeReporter(
    private val player: Player,
    private val scope: CoroutineScope,
    private val isCasting: () -> Boolean,
    private val isRelay: () -> Boolean,
    /** Mirrors the listen-history pause switch: when the user paused history, no beacons either. */
    private val historyPaused: suspend () -> Boolean,
    /**
     * Fallback tracking-URL resolution for plays that skipped the stream resolver (cached spans,
     * downloaded files) — a light metadata `/player` fetch, exactly what the legacy end-of-listen
     * ping did. Null when unavailable (offline local play): the session then reports nothing.
     */
    private val fetchTracking: suspend (videoId: String) -> PlayerResponse.PlaybackTracking?,
    /**
     * Sink for a genuine OFFLINE listen the live session could not report (no tracking URLs) — the
     * additive deferred-stats path re-pushes it on reconnect. Default no-op keeps the reporter
     * isolated + unit-testable; [MusicService] wires the real [DeferredStatsQueue]. Only fires from the
     * offline branch, so relay/cast (never a session) and online plays (report live) never reach it.
     */
    private val onOfflineListen: (DeferredStatsRecord) -> Unit = {},
) {

    private data class TrackingUrls(
        val playbackUrl: String?,
        val watchtimeUrl: String?,
        /** The streamed itag (base.js `fmt=y.D.itag`); null for cached/local plays — then omitted. */
        val fmt: Int?,
        /** The server-provided watchtime flush cadence (falls back to the base.js default). */
        val schedule: WatchTimeSchedule,
        /** When this resolution was captured — a preloaded entry played much later is stale (see [TRACKING_MAX_AGE_MS]). */
        val resolvedAtMs: Long,
    )

    private sealed interface Ping {
        /** [muted] is the player's real mute state, captured on the main thread at enqueue time. */
        data class Start(val cmtMs: Long, val muted: Boolean) : Ping
        data class Watch(
            val segments: WatchTimeSegments.Drained,
            val cmtMs: Long,
            val rtMs: Long,
            val final: Boolean,
            val muted: Boolean,
        ) : Ping
    }

    private inner class Session(val videoId: String, val cpn: String) {
        val startedWallMs = System.currentTimeMillis()
        val segments = WatchTimeSegments()
        val pings = Channel<Ping>(Channel.UNLIMITED)
        var consumer: Job? = null
        var finished = false
        // How many SCHEDULED flushes have fired — advances the wall-clock flush cadence. Pause/seek
        // pings are extra state-change flushes and never touch this (matches the web client).
        var scheduledFlushCount = 0
        // Seeded to the base.js default; replaced with the server schedule the moment tracking resolves
        // (usually before the first flush is due at 10s).
        @Volatile var schedule = WatchTimeSchedule(null, null)
        // The streamed itag reported as `fmt`. Lives on the session (not the captured URLs) so a
        // rendition change can update it: a video-mode swap makes the single-itag `fmt` no longer
        // truthful, so [onOwnSwapTransition] nulls it — omitting is honest, a stale wrong itag is not.
        @Volatile var fmt: Int? = null

        fun rtMs() = System.currentTimeMillis() - startedWallMs
    }

    /**
     * playbackTracking captured by the stream resolver, keyed by clean videoId — written on the
     * data-source thread, read at session start on the service scope. Bounded: stats URLs are tiny
     * and a session consumes its entry's value immediately, but never let it grow unbounded.
     */
    private val resolvedTracking = ConcurrentHashMap<String, TrackingUrls>()

    /**
     * The listen's cpn, shared with the DIRECT media request so the beacon session correlates with
     * real byte delivery (the official client stamps the same cpn on both — base.js).
     */
    private val nonces = PlaybackNonceRegistry()

    /**
     * The cpn to stamp on this id's DIRECT media request — called from the stream resolver (a
     * background thread). Returns the SAME cpn the beacon session for this listen uses; keyed by BASE
     * videoId so audio/video/merge-audio renditions of one listen share it. Never mints for relay
     * (relay uses its own factory and never calls this) or cast (the resolver is not its byte path).
     */
    fun mediaCpnFor(videoId: String): String = nonces.getOrCreate(videoId)

    // Written on the service main scope, but READ from the data-source background thread in
    // onTrackingResolved (schedule adoption) — @Volatile publishes those writes safely.
    @Volatile
    private var session: Session? = null
    private var tickerJob: Job? = null

    /** The departed item's final position, captured from the AUTO_TRANSITION discontinuity. */
    private var pendingEndPositionMs = -1L

    /**
     * Called from the stream resolver with the playback response's tracking block and the itag it
     * actually resolved to stream — so `fmt` carries the real streamed format, never a guess.
     */
    fun onTrackingResolved(videoId: String, tracking: PlayerResponse.PlaybackTracking?, itag: Int?) {
        val urls = TrackingUrls(
            playbackUrl = tracking?.videostatsPlaybackUrl?.baseUrl,
            watchtimeUrl = tracking?.videostatsWatchtimeUrl?.baseUrl,
            fmt = itag,
            schedule = WatchTimeSchedule(
                tracking?.videostatsScheduledFlushWalltimeSeconds,
                tracking?.videostatsDefaultFlushIntervalSeconds,
            ),
            resolvedAtMs = System.currentTimeMillis(),
        )
        if (urls.playbackUrl == null && urls.watchtimeUrl == null) return
        if (resolvedTracking.size > MAX_CACHED_TRACKING) resolvedTracking.clear()
        resolvedTracking[videoId] = urls
        // If the session for this id is already live, adopt its real flush schedule AND itag immediately.
        session?.takeIf { it.videoId == videoId && !it.finished }?.let {
            it.schedule = urls.schedule
            it.fmt = urls.fmt
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            ensureSession()
            session?.segments?.onPlay(player.currentPosition)
            startTicker()
        } else {
            tickerJob?.cancel()
            // A mid-track rebuffer/stall (still wants to play, just STATE_BUFFERING) is NOT a pause:
            // firing a state-change ping here produces a timing pattern the official client never
            // emits, and a stall landing < MIN_SEGMENT_MS after the last flush would drop a genuinely
            // contiguous segment. Position does not advance while buffering, so the open segment stays
            // honest and resumes on the next play event — leave it untouched.
            if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) return
            val s = session ?: return
            s.segments.onPause(player.currentPosition)
            enqueueWatch(s, cmtMs = player.currentPosition, final = false)
        }
    }

    fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val s = session ?: return
        // A track boundary (auto-advance) AND a repeat-one loop back to the SAME item both arrive as
        // AUTO_TRANSITION — the repeat wraps position to ~0, so capture the REAL end (oldPosition,
        // before the wrap) for onTransition's final ping instead of the post-wrap ~0. Also covers a
        // genuine item change (index/id differ, e.g. seekToNext).
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
            oldPosition.mediaItemIndex != newPosition.mediaItemIndex ||
            oldPosition.mediaItemId()?.let { it != s.videoId } == true
        ) {
            pendingEndPositionMs = oldPosition.positionMs
            return
        }
        if (reason == Player.DISCONTINUITY_REASON_SEEK ||
            reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        ) {
            // Keep segment accounting correct on any seek, but a video-mode rendition swap seeks to the
            // SAME position (position-continuous) — that is not a user seek and must stay transparent
            // to the session, so fire no spurious zero-progress ping for a sub-second delta.
            s.segments.onSeek(oldPosition.positionMs, newPosition.positionMs, player.isPlaying)
            if (kotlin.math.abs(newPosition.positionMs - oldPosition.positionMs) >= REAL_SEEK_MIN_MS) {
                enqueueWatch(s, cmtMs = newPosition.positionMs, final = false)
            }
        }
    }

    /** A REAL item transition (never the video-mode own-swap): finish the old listen, arm the new. */
    fun onTransition() {
        val endMs = pendingEndPositionMs
        pendingEndPositionMs = -1
        finishSession(endPositionMs = endMs)
        if (player.isPlaying) {
            ensureSession()
            session?.segments?.onPlay(player.currentPosition)
            startTicker()
        }
    }

    /**
     * The video-mode OWN swap (audio↔video toggle, or a repeat-one loop of the same video item) —
     * classified by MusicService BEFORE [onTransition], and it deliberately does NOT finish the
     * session (same listen, one cpn). Two pieces of session state that a real transition would consume
     * must be neutralised here so a LATER real transition can't inherit them:
     *  - the departed-position capture from the loop's AUTO_TRANSITION discontinuity ([onPositionDiscontinuity]),
     *    which would otherwise be consumed by the next real transition and fabricate an unplayed range;
     *  - the single-itag `fmt`, no longer truthful once the rendition changed — nulled so it is omitted
     *    (an honest unknown) rather than reporting the wrong itag against the delivered bytes.
     */
    fun onOwnSwapTransition() {
        pendingEndPositionMs = -1
        session?.fmt = null
    }

    /** The last item ran out (STATE_ENDED fires no transition). */
    fun onPlaybackEnded() {
        finishSession(endPositionMs = player.currentPosition)
    }

    fun onDestroy() {
        // Teardown is NOT a track change — the player still sits on the current item, so its position
        // is the real end (like onPlaybackEnded). Passing null would fall back to the segment's
        // last-known position, which lags the ticker cadence and drops the tail on a swipe-kill.
        finishSession(endPositionMs = player.currentPosition)
    }

    private fun ensureSession() {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId
        if (session?.videoId == id && session?.finished == false) return
        finishSession(endPositionMs = null)
        // The two hard exclusions: relay egress and cast (the receiver plays, not us).
        if (isRelay() || isCasting()) return
        if (item.metadata == null) return
        // The session cpn IS the one the media request was stamped with for this listen (the resolver
        // seeds it via mediaCpnFor before playback starts); getOrCreate returns that same value. Pin it
        // so the registry's LRU can never evict the live listen's cpn mid-stream.
        val newSession = Session(videoId = id, cpn = nonces.getOrCreate(id))
        nonces.pin(id)
        // Adopt the real flush schedule + itag if tracking already resolved for this id (else the
        // defaults, until onTrackingResolved swaps them in).
        resolvedTracking[id]?.let {
            newSession.schedule = it.schedule
            newSession.fmt = it.fmt
        }
        session = newSession
        newSession.pings.trySend(Ping.Start(cmtMs = player.currentPosition, muted = playerMuted()))
        newSession.consumer = scope.launch { consumePings(newSession) }
    }

    private fun finishSession(endPositionMs: Long?) {
        tickerJob?.cancel()
        val s = session ?: return
        session = null
        if (s.finished) return
        s.finished = true
        // Free the listen's cpn so the next play of this song mints a fresh one (fresh-cpn-per-play,
        // matching the client — this keeps view counts incrementing on repeat).
        nonces.release(s.videoId)
        // The end position: the caller's captured value if valid, else this item's OWN last-known
        // position — NEVER player.currentPosition. After a track/queue change the player's position
        // already belongs to the NEW item, so using it would close the departed listen with a
        // fabricated range spanning into the next track (a station join mid-listen was the worst case:
        // a `final=1` ping claiming the live station offset as watched — invalid traffic, the hard rule).
        val end = endPositionMs?.takeIf { it >= 0 } ?: s.segments.lastKnownPositionMs()
        val muted = playerMuted()
        s.segments.onPause(end)
        val drained = s.segments.drain(end, stillPlaying = false)
        if (drained != null) {
            s.pings.trySend(Ping.Watch(drained, cmtMs = end, rtMs = s.rtMs(), final = true, muted = muted))
        } else {
            // Nothing watched since the last ping — the final flag still closes the session honestly
            // with a zero-length range at the end position.
            val cmt = WatchTimeSegments.formatSeconds(end)
            s.pings.trySend(
                Ping.Watch(
                    WatchTimeSegments.Drained(st = cmt, et = cmt),
                    cmtMs = end,
                    rtMs = s.rtMs(),
                    final = true,
                    muted = muted,
                ),
            )
        }
        s.pings.close()
    }

    private fun enqueueWatch(s: Session, cmtMs: Long, final: Boolean) {
        val drained = s.segments.drain(cmtMs, stillPlaying = player.isPlaying && !final) ?: return
        s.pings.trySend(Ping.Watch(drained, cmtMs = cmtMs, rtMs = s.rtMs(), final = final, muted = playerMuted()))
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            // Fire watchtime pings at the server's scheduled wall-clock offsets (10s, 20s, 30s, then
            // every ~40s — matching the official client instead of a fixed interval). A pause/seek
            // ping is separate and never advances the scheduled count.
            while (isActive && player.isPlaying) {
                val s = session ?: break
                val dueAt = s.schedule.flushOffsetMs(s.scheduledFlushCount)
                val wait = dueAt - s.rtMs()
                if (wait > 0) delay(wait)
                if (!isActive || !player.isPlaying || session !== s) break
                s.segments.onProgress(player.currentPosition)
                s.scheduledFlushCount++
                enqueueWatch(s, cmtMs = player.currentPosition, final = false)
            }
        }
    }

    /** One consumer per session: resolves the URLs once, then sends pings strictly in order. */
    private suspend fun consumePings(s: Session) {
        // A preloaded resolution that is played much later (queue swap, then a cache-served replay
        // hours on) carries an EXPIRED tracking baseUrl the server would drop — so a stale entry is
        // discarded here and re-fetched fresh, exactly the case `fetchTracking` exists to cover.
        val fresh = resolvedTracking.remove(s.videoId)
            ?.takeIf { System.currentTimeMillis() - it.resolvedAtMs <= TRACKING_MAX_AGE_MS }
        val urls = fresh
            ?: runCatching { fetchTracking(s.videoId) }.getOrNull()?.let {
                // Fallback metadata fetch (cached/local/stale play): no resolved itag, so `fmt` is
                // omitted. The schedule field is unused here (the ticker already holds the session's).
                TrackingUrls(
                    it.videostatsPlaybackUrl?.baseUrl,
                    it.videostatsWatchtimeUrl?.baseUrl,
                    fmt = null,
                    schedule = WatchTimeSchedule(
                        it.videostatsScheduledFlushWalltimeSeconds,
                        it.videostatsDefaultFlushIntervalSeconds,
                    ),
                    resolvedAtMs = System.currentTimeMillis(),
                )
            }
        if (urls?.playbackUrl == null && urls?.watchtimeUrl == null) {
            // OFFLINE listen: no live URLs to beacon now. Accumulate the SAME real ranges the reporter
            // computed (the pings' st/et are honest deltas) and hand ONE record to the deferred sink to
            // re-push on reconnect. Nothing is fabricated; a listen below the genuine-play gate is dropped.
            // Privacy MUST match the live path exactly (a deferred beacon is still a beacon): the live
            // consumer opens nothing if paused at the Start ping, and silences forward pings once paused
            // mid-listen. Mirror both here — capture nothing if paused at start, and stop accumulating at
            // the first paused ping — so a private listen is never queued even if unpaused before it ends.
            val st = StringBuilder()
            val et = StringBuilder()
            var watchedMs = 0L
            var lastCmtMs = 0L
            var startedPaused = false
            var stoppedForPause = false
            for (ping in s.pings) {
                when (ping) {
                    is Ping.Start -> startedPaused = historyPaused()
                    is Ping.Watch -> {
                        if (startedPaused || stoppedForPause) continue
                        if (historyPaused()) { stoppedForPause = true; continue }
                        if (ping.segments.watchedMs > 0) {
                            if (st.isNotEmpty()) { st.append(','); et.append(',') }
                            st.append(ping.segments.st)
                            et.append(ping.segments.et)
                            watchedMs += ping.segments.watchedMs
                        }
                        lastCmtMs = ping.cmtMs
                    }
                }
            }
            if (!startedPaused && watchedMs >= MIN_DEFERRED_MS && st.isNotEmpty()) {
                runCatching {
                    onOfflineListen(
                        DeferredStatsRecord(
                            videoId = s.videoId,
                            st = st.toString(),
                            et = et.toString(),
                            cmt = WatchTimeSegments.formatSeconds(lastCmtMs),
                            rt = WatchTimeSegments.formatSeconds(watchedMs),
                            endedAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            return
        }
        // The fmt reported per ping is the LIVE session value (updated by onTrackingResolved, nulled by
        // a video-mode swap) — read fresh each ping, never latched.
        urls.fmt?.let { s.fmt = it }
        // Whether the playback (session-open) ping was actually sent. A watchtime/final ping must NEVER
        // ride a cpn whose open ping was suppressed — that half-session shape (watchtime with no
        // preceding playback ping) is one no real client produces, and it would beacon a listen the
        // user had marked private at its start. Keyed off the Start ping, which is always first.
        var opened = false
        for (ping in s.pings) {
            // Re-check per ping so enabling "pause listen history" MID-listen silences the rest of the
            // in-flight session (not just sessions started while paused). A cheap off-main DataStore read.
            val paused = historyPaused()
            when (ping) {
                is Ping.Start -> {
                    if (paused) continue
                    urls.playbackUrl?.let { url ->
                        // opened is set INSIDE the send: a tracking block with only a watchtime URL
                        // (null playbackUrl) must NOT open the session, or the watchtime pings below
                        // would be the orphan shape this gate forbids.
                        opened = true
                        YouTube.registerPlayback(
                            playlistId = null,
                            playbackTracking = url,
                            cpn = s.cpn,
                            cmt = WatchTimeSegments.formatSeconds(ping.cmtMs),
                            final = false,
                            fmt = s.fmt,
                            muted = ping.muted,
                        ).onFailure { Timber.d(it, "WatchTime: playback ping failed") }
                    }
                }
                is Ping.Watch -> {
                    if (!opened || paused) continue
                    urls.watchtimeUrl?.let { url ->
                        YouTube.registerWatchtime(
                            watchtimeTracking = url,
                            cpn = s.cpn,
                            st = ping.segments.st,
                            et = ping.segments.et,
                            cmt = WatchTimeSegments.formatSeconds(ping.cmtMs),
                            rt = WatchTimeSegments.formatSeconds(ping.rtMs),
                            final = ping.final,
                            fmt = s.fmt,
                            muted = ping.muted,
                        ).onFailure { Timber.d(it, "WatchTime: watchtime ping failed") }
                    }
                }
            }
        }
    }

    private fun Player.PositionInfo.mediaItemId(): String? = mediaItem?.mediaId

    /**
     * Our player's real mute state (base.js encodes `muted`/`mos` as `isMuted()?1:0`). ExoPlayer has
     * no mute separate from volume, so zero output IS muted — a truthful read, not a fabricated flag.
     * Read on the main thread at enqueue time.
     */
    private fun playerMuted(): Boolean = player.volume <= 0f

    companion object {
        private const val MAX_CACHED_TRACKING = 64

        // A position jump smaller than this is not a user seek (a rendition swap is position-continuous,
        // ~0) — it fires no watchtime ping. Sub-second seeks are immaterial to watch time.
        private const val REAL_SEEK_MIN_MS = 1_000L

        // A preloaded tracking resolution older than this is treated as stale and re-fetched. Generous
        // enough that a normal next-track preload (played within minutes) is never rejected, tight
        // enough that an hours-later cache-served replay resolves fresh instead of beaconing a dead URL.
        private const val TRACKING_MAX_AGE_MS = 60 * 60 * 1000L

        // The genuine-play gate for a DEFERRED offline capture — a listen shorter than this is not
        // worth queuing (matches the ≥10s history threshold; a YouTube view qualifies around ~30s).
        private const val MIN_DEFERRED_MS = 10_000L
    }
}
