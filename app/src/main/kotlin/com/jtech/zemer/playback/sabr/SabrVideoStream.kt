package com.jtech.zemer.playback.sabr

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException

/**
 * A single dual-track SABR session shared by the two ExoPlayer [DataSource]s of a `MergingMediaSource`
 * (video-only + audio). One [SabrVideoSession] fills BOTH disk-backed buffers; the video DataSource
 * reads [videoBuffer], the audio DataSource reads [audioBuffer].
 *
 * LIFECYCLE — explicit, NEVER tied to DataSource open/close cycles. media3's progressive loading opens
 * and closes the two children UN-paired: entering video mode seeks mid-track, and once the period
 * prepares, media3 CANCELS the in-flight loads and re-opens both children at the seek offset — so there
 * is always a close→reopen gap where zero DataSources are open while playback very much continues. A
 * ref-counted "cancel + unregister at zero" (the first design) killed the session and wiped the registry
 * entry inside exactly that gap, and the reopen then failed with "no session" (found on-device). Instead:
 * sessions run until [destroy] — called only by [SabrVideoRegistry] when the entry is removed (video
 * mode exits) or replaced (a committed new resolve).
 *
 * The stream ORCHESTRATES its dual-track session around reader demand like [SabrAudioStream]: covered
 * reads serve from the spool; near-frontier reads wait for the catch-up; a far/backward read RESTARTS
 * the session at the estimated `playerTimeMs` (the dual-track cold seek is proven live —
 * tests/sabr-video.mjs START_S — with both tracks landing at T and NO end_segment_number, so completion
 * is judged by byte coverage). Both track readers share ONE session; a fresh restart gets a landing
 * grace so the sibling reader doesn't immediately re-aim it. Playback sessions are demand-paced.
 */
@UnstableApi
internal class SabrVideoStream(
    private val mediaId: String,
    val config: SabrVideoConfig,
    private val client: OkHttpClient,
) {
    val videoBuffer = SabrBuffer(config.videoFormat.contentLength, SabrSpool.partFile(mediaId, config.videoFormat.itag, suffix = "v"))
    val audioBuffer = SabrBuffer(config.audioFormat.contentLength, SabrSpool.partFile(mediaId, config.audioFormat.itag, suffix = "va"))
    private var thread: Thread? = null
    private var session: SabrVideoSession? = null
    private var destroyed = false
    private var sessionStartedAtMs = 0L
    private var restartAttempts = 0
    private var seekMarginMs = SabrSeekLogic.SEEK_MARGIN_MS

    @Synchronized fun usable(): Boolean = !destroyed && !videoBuffer.failed() && !audioBuffer.failed()

    /** Non-blocking open-time hint: make sure a session is (or soon will be) covering the position. */
    @Synchronized fun prepare(video: Boolean, position: Long) {
        ensureSessionCovering(video, position)
    }

    /** Blocking read on one track — covered serves, catch-ups and seek-restarts (see class doc). */
    fun read(video: Boolean, position: Long, into: ByteArray, offset: Int, length: Int): Int {
        val buffer = if (video) videoBuffer else audioBuffer
        while (true) {
            val n = buffer.readCovered(position, into, offset, length)
            if (n > 0) {
                if (restartAttempts != 0) synchronized(this) { restartAttempts = 0; seekMarginMs = SabrSeekLogic.SEEK_MARGIN_MS }
                return n
            }
            if (position >= buffer.expectedLength) return -1
            synchronized(this) {
                if (destroyed) throw IOException("SABR video stream destroyed")
                ensureSessionCovering(video, position)
            }
            buffer.awaitChange(250)
        }
    }

    private fun ensureSessionCovering(video: Boolean, position: Long) {
        val buffer = if (video) videoBuffer else audioBuffer
        val format = if (video) config.videoFormat else config.audioFormat
        if (destroyed || buffer.failed() || buffer.coveredAt(position)) return
        val s = session
        val alive = s != null && thread?.isAlive == true
        val anchor = if (alive) (if (video) s!!.firstVideoOffset else s!!.firstAudioOffset) else -1L
        when (val action = SabrSeekLogic.decide(
            sessionAlive = alive, anchor = anchor, position = position,
            sinceStartMs = System.currentTimeMillis() - sessionStartedAtMs,
            restartAttempts = restartAttempts, durationMs = config.durationMs,
            contentLength = format.contentLength, marginMs = seekMarginMs,
        )) {
            SabrSeekLogic.Grace, SabrSeekLogic.LetDrain -> return
            SabrSeekLogic.GiveUp -> {
                val msg = "SABR video seek could not be served at $position after $restartAttempts attempts"
                videoBuffer.markError(msg); audioBuffer.markError(msg)
            }
            is SabrSeekLogic.Restart -> {
                seekMarginMs = action.marginMs
                restartAttempts++
                // Pace the seeked track from the reader's real target, not the stale pre-seek watermark.
                buffer.resetDemandFrom(position)
                Timber.tag(TAG).d("SABR video seek-restart %s (%s) pos=%d -> t=%dms (attempt %d)", mediaId, if (video) "v" else "a", position, action.startMs, restartAttempts)
                startSessionAt(action.startMs)
            }
        }
    }

    private fun startSessionAt(startMs: Long) {
        session?.cancel()
        val s = SabrVideoSession(
            config, client, videoBuffer, audioBuffer,
            startTimeMs = startMs,
            paceAheadVideoBytes = AHEAD_VIDEO_BYTES,
            paceAheadAudioBytes = AHEAD_AUDIO_BYTES,
            onIncomplete = { config.clientKey?.let { SabrPlayerResolver.recordStall(mediaId, it) } },
            // Stream-owned lifecycle: a failing session must not poison the shared buffers the next
            // seek-restart reuses — the stream's MAX_SEEK_RESTARTS path owns the terminal error.
            restartable = true,
        )
        session = s
        sessionStartedAtMs = System.currentTimeMillis()
        thread = Thread(s, "sabr-video-$mediaId").apply { isDaemon = true; start() }
    }

    /** Kept for the DataSource open/close pairing (lifecycle is registry-owned — see class doc). */
    @Synchronized fun release() {}

    /** Kill the drain. Called ONLY by the registry on remove/replace — the explicit end of this stream. */
    @Synchronized fun destroy() {
        if (destroyed) return
        destroyed = true
        session?.cancel()
        session = null
        thread = null
        // Wake any reader parked in SabrBuffer.read's wait: a cancelled session deliberately skips
        // marking (its exit branches bare-return), so without this a DataSource still blocked on a
        // destroyed stream would wait forever — an infinite buffering spinner with no player error.
        videoBuffer.markError("SABR stream destroyed")
        audioBuffer.markError("SABR stream destroyed")
        // Video tracks are never retained (no video replay cache — the resolve cache already skips
        // the /player round-trip); drop both spool files.
        videoBuffer.release(deleteFile = true)
        audioBuffer.release(deleteFile = true)
    }

    private companion object {
        const val TAG = "SabrVideoStream"
        // Pacing windows: the drain stays this far ahead of each reader, no further.
        const val AHEAD_VIDEO_BYTES = 32L * 1024 * 1024
        const val AHEAD_AUDIO_BYTES = 8L * 1024 * 1024
        // Forward gaps the sequential drain is allowed to close instead of a seek-restart.
    }
}

/**
 * Registry mapping a media id to its live [SabrVideoStream]. The two track DataSources
 * (`sabrvideo://<id>` and `sabraudio://<id>`) resolve the SAME stream here. Isolated from the
 * audio-only [SabrStreamRegistry]. The registry OWNS stream lifetime: [put] destroys a replaced
 * stream, [remove] destroys the removed one — VideoModeController removes on every video-mode exit
 * (its clearState chokepoint), so a stream never outlives the listen it was resolved for.
 */
@UnstableApi
internal object SabrVideoRegistry {
    const val SCHEME_VIDEO = "sabrvideo"
    const val SCHEME_AUDIO = "sabraudio"
    private val streams = java.util.concurrent.ConcurrentHashMap<String, SabrVideoStream>()

    fun put(mediaId: String, stream: SabrVideoStream) { streams.put(mediaId, stream)?.destroy() }
    fun get(mediaId: String): SabrVideoStream? = streams[mediaId]
    fun remove(mediaId: String) { streams.remove(mediaId)?.destroy() }

    /** Destroy every live stream — MusicService.onDestroy teardown (nothing may outlive the service). */
    fun clear() {
        val ids = streams.keys.toList()
        for (id in ids) streams.remove(id)?.destroy()
    }

    fun videoUri(mediaId: String): Uri = Uri.parse("$SCHEME_VIDEO://$mediaId")
    fun audioUri(mediaId: String): Uri = Uri.parse("$SCHEME_AUDIO://$mediaId")
    fun mediaId(uri: Uri): String? =
        if (uri.scheme == SCHEME_VIDEO || uri.scheme == SCHEME_AUDIO) uri.host ?: uri.schemeSpecificPart.trimStart('/') else null
}

/**
 * ExoPlayer [DataSource] for one track ([video]=true reads the video buffer, else the audio buffer) of a
 * shared [SabrVideoStream]. Length is the track format's contentLength; reads go through the stream's
 * orchestration (covered serves / catch-ups / seek-restarts) exactly like [SabrDataSource].
 */
@UnstableApi
internal class SabrVideoDataSource(private val client: OkHttpClient, private val video: Boolean) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var stream: SabrVideoStream? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    // True once transferStarted() ran. media3's DefaultBandwidthMeter NPEs if transferEnded() fires
    // without a matching transferStarted() (its dataSpec is null) — and closeQuietly only swallows
    // IOException, so that NPE would surface as a "Source error". A MergingMediaSource tears down BOTH
    // children when one fails, so a child whose open() never reached transferStarted() gets close()d.
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val id = SabrVideoRegistry.mediaId(dataSpec.uri) ?: throw IOException("SABR video: no media id in ${dataSpec.uri}")
        val s = SabrVideoRegistry.get(id) ?: throw IOException("SABR video: no session for $id")
        stream = s
        s.prepare(video, dataSpec.position)
        val length = (if (video) s.config.videoFormat else s.config.audioFormat).contentLength
        position = dataSpec.position
        bytesRemaining = if (length > 0) length - dataSpec.position else C.LENGTH_UNSET.toLong()
        transferStarted(dataSpec)
        started = true
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) readLength else minOf(readLength.toLong(), bytesRemaining).toInt()
        val n = stream?.read(video, position, target, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (n == -1) return C.RESULT_END_OF_INPUT
        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        stream?.release()
        stream = null
        if (started) { transferEnded(); started = false }
        uri = null
    }
}

/** Factory for one track's [SabrVideoDataSource]. */
@UnstableApi
internal class SabrVideoDataSourceFactory(private val client: OkHttpClient, private val video: Boolean) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrVideoDataSource(client, video)
}
