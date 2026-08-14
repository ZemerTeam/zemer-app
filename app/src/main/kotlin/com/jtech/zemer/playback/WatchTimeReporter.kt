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
 * and podcast episodes alike (handoff: `zemer-app-emulate-youtube-music-stream-request.md`).
 *
 * One session per listen, keyed by one cpn: a playback ping when the listen actually starts
 * (`cmt=<start position>`, `final=0`), watchtime pings every ~[PING_INTERVAL_MS] of playback plus on
 * pause/seek, and a `final=1` watchtime ping when the listen ends. Every reported range comes from
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
) {

    private data class TrackingUrls(
        val playbackUrl: String?,
        val watchtimeUrl: String?,
        /** The streamed itag (base.js `fmt=y.D.itag`); null for cached/local plays — then omitted. */
        val fmt: Int?,
        /** The server-provided watchtime flush cadence (falls back to the base.js default). */
        val schedule: WatchTimeSchedule,
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
        )
        if (urls.playbackUrl == null && urls.watchtimeUrl == null) return
        if (resolvedTracking.size > MAX_CACHED_TRACKING) resolvedTracking.clear()
        resolvedTracking[videoId] = urls
        // If the session for this id is already live, adopt its real flush schedule immediately.
        session?.takeIf { it.videoId == videoId && !it.finished }?.schedule = urls.schedule
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            ensureSession()
            session?.segments?.onPlay(player.currentPosition)
            startTicker()
        } else {
            tickerJob?.cancel()
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

    /** The last item ran out (STATE_ENDED fires no transition). */
    fun onPlaybackEnded() {
        finishSession(endPositionMs = player.currentPosition)
    }

    fun onDestroy() {
        finishSession(endPositionMs = null)
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
        // seeds it via mediaCpnFor before playback starts); getOrCreate returns that same value.
        val newSession = Session(videoId = id, cpn = nonces.getOrCreate(id))
        // Adopt the real flush schedule if tracking already resolved for this id (else the default,
        // which equals the common server value, until onTrackingResolved swaps it in).
        resolvedTracking[id]?.schedule?.let { newSession.schedule = it }
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
        val end = endPositionMs?.takeIf { it >= 0 } ?: player.currentPosition
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
        val urls = resolvedTracking.remove(s.videoId)
            ?: runCatching { fetchTracking(s.videoId) }.getOrNull()?.let {
                // Fallback metadata fetch (cached/local play): no resolved itag, so `fmt` is omitted.
                // The schedule field is unused here (this path only supplies URLs to the consumer; the
                // ticker already holds the session's schedule).
                TrackingUrls(
                    it.videostatsPlaybackUrl?.baseUrl,
                    it.videostatsWatchtimeUrl?.baseUrl,
                    fmt = null,
                    schedule = WatchTimeSchedule(
                        it.videostatsScheduledFlushWalltimeSeconds,
                        it.videostatsDefaultFlushIntervalSeconds,
                    ),
                )
            }
        if (urls?.playbackUrl == null && urls?.watchtimeUrl == null) {
            for (ping in s.pings) { /* no tracking URLs (offline local play): nothing to report */ }
            return
        }
        for (ping in s.pings) {
            // Re-check per ping so enabling "pause listen history" MID-listen silences the rest of the
            // in-flight session (not just sessions started while paused). A cheap off-main DataStore read.
            if (historyPaused()) continue
            when (ping) {
                is Ping.Start -> urls.playbackUrl?.let { url ->
                    YouTube.registerPlayback(
                        playlistId = null,
                        playbackTracking = url,
                        cpn = s.cpn,
                        cmt = WatchTimeSegments.formatSeconds(ping.cmtMs),
                        final = false,
                        fmt = urls.fmt,
                        muted = ping.muted,
                    ).onFailure { Timber.d(it, "WatchTime: playback ping failed") }
                }
                is Ping.Watch -> urls.watchtimeUrl?.let { url ->
                    YouTube.registerWatchtime(
                        watchtimeTracking = url,
                        cpn = s.cpn,
                        st = ping.segments.st,
                        et = ping.segments.et,
                        cmt = WatchTimeSegments.formatSeconds(ping.cmtMs),
                        rt = WatchTimeSegments.formatSeconds(ping.rtMs),
                        final = ping.final,
                        fmt = urls.fmt,
                        muted = ping.muted,
                    ).onFailure { Timber.d(it, "WatchTime: watchtime ping failed") }
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
    }
}
