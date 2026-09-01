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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One live audio SABR stream: the disk-backed reassembly buffer plus the session(s) that fill it. The
 * audio sibling of [SabrVideoStream], with the same explicit lifecycle: sessions run until [destroy] —
 * NEVER tied to a DataSource close (media3 closes/reopens on every seek outside its sample buffer; the
 * first design re-resolved + re-drained from byte 0 on each of those).
 *
 * The stream ORCHESTRATES its sessions around reader demand:
 *  - a read inside covered bytes is served from the spool;
 *  - a read just past the drain frontier waits for the catch-up;
 *  - a read far ahead / behind coverage RESTARTS the session at the estimated `playerTimeMs` for that
 *    byte (cold-start seek, proven live in tests/sabr-seek.mjs: the server serves the segment
 *    containing T with absolute offsets), converging with a growing back-margin when an estimate lands
 *    past the target;
 *  - playback sessions are DEMAND-PACED (the drain follows consumption; a skip stops the spend).
 *
 * A stream whose drain COMPLETED from byte 0 is promoted to the persistent [SabrSpool] replay cache on
 * destroy; a stream built FROM the cache ([fromSpool]) is fully covered and never opens a session.
 */
internal class SabrAudioStream private constructor(
    private val mediaId: String,
    private val config: SabrConfig?, // null = replay-cache stream (fully covered, no sessions)
    private val client: OkHttpClient?,
    val buffer: SabrBuffer,
) {
    constructor(mediaId: String, config: SabrConfig, client: OkHttpClient) : this(
        mediaId, config, client,
        SabrBuffer(config.format.contentLength, SabrSpool.partFile(mediaId, config.format.itag)),
    )

    val length: Long get() = buffer.expectedLength
    /** Whether this stream serves a persistent spool replay (its failure means a bad cache file). */
    val fromSpool: Boolean get() = config == null
    private var thread: Thread? = null
    private var session: SabrSession? = null
    private var destroyed = false
    private var sessionStartedAtMs = 0L
    private var restartAttempts = 0
    private var seekMarginMs = SEEK_MARGIN_MS

    @Synchronized fun usable(): Boolean = !destroyed && !buffer.failed()

    /** Non-blocking: make sure a session is (or soon will be) covering [position] (open-time hint). */
    @Synchronized fun prepare(position: Long) {
        ensureSessionCovering(position)
    }

    /**
     * Blocking read at [position]: serves covered bytes, orchestrates sessions for uncovered ones.
     * Returns the count, -1 at end of stream; throws when the stream failed at a gap or was destroyed.
     */
    fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val n = buffer.readCovered(position, into, offset, length)
            if (n > 0) {
                if (restartAttempts != 0) synchronized(this) { restartAttempts = 0; seekMarginMs = SEEK_MARGIN_MS }
                return n
            }
            if (position >= buffer.expectedLength) return -1
            synchronized(this) {
                if (destroyed) throw IOException("SABR stream destroyed")
                ensureSessionCovering(position)
            }
            buffer.awaitChange(250)
        }
    }

    private fun ensureSessionCovering(position: Long) {
        if (destroyed || buffer.failed() || buffer.coveredAt(position)) return
        val cfg = config ?: return // a replay stream is fully covered; an uncovered read here is EOF math
        val s = session
        if (s != null && thread?.isAlive == true) {
            val anchor = s.firstWrittenOffset
            when {
                // Landing: the session hasn't received its first segment yet — give it grace.
                anchor < 0 -> if (System.currentTimeMillis() - sessionStartedAtMs < LAND_GRACE_MS) return
                // Anchored at/below the target: let the sequential drain catch up when it's close.
                anchor <= position -> {
                    val frontier = buffer.coverageEndFrom(anchor)
                    if (position - frontier <= CATCHUP_BYTES) return
                }
                // Landed PAST the target (the estimate was late): widen the back-margin and re-aim.
                else -> seekMarginMs = (seekMarginMs * 2).coerceAtMost(60_000L)
            }
        }
        if (restartAttempts >= MAX_SEEK_RESTARTS) {
            buffer.markError("SABR seek could not be served at $position after $restartAttempts attempts")
            return
        }
        restartAttempts++
        val startMs = estimateTimeMs(cfg, position)
        Timber.tag(TAG).d("SABR seek-restart %s at pos=%d -> t=%dms (attempt %d)", mediaId, position, startMs, restartAttempts)
        startSessionAt(cfg, startMs)
    }

    /** Byte -> time estimate (linear over the format), pulled back by the convergence margin. */
    private fun estimateTimeMs(cfg: SabrConfig, position: Long): Long {
        if (cfg.durationMs <= 0 || cfg.format.contentLength <= 0 || position <= 0) return 0
        return (position * cfg.durationMs / cfg.format.contentLength - seekMarginMs).coerceAtLeast(0)
    }

    private fun startSessionAt(cfg: SabrConfig, startMs: Long) {
        session?.cancel()
        val s = SabrSession(
            cfg, client!!, buffer,
            startTimeMs = startMs,
            paceAheadBytes = AHEAD_AUDIO_BYTES,
            onIncomplete = { cfg.clientKey?.let { SabrPlayerResolver.recordStall(mediaId, it) } },
            // Stream-owned lifecycle: a failing session must not poison the shared buffer the next
            // seek-restart reuses — the stream's MAX_SEEK_RESTARTS path owns the terminal error.
            restartable = true,
        )
        session = s
        sessionStartedAtMs = System.currentTimeMillis()
        thread = Thread(s, "sabr-$mediaId").apply { isDaemon = true; start() }
    }

    /** Kill the drain. Called ONLY by [SabrStreamRegistry] on replace/remove/clear. */
    @Synchronized fun destroy() {
        if (destroyed) return
        destroyed = true
        session?.cancel()
        session = null
        thread = null
        val fromCache = config == null
        val complete = buffer.completeFromZero()
        // Wake any reader parked in the buffer (a cancelled session skips marking on its own). A
        // COMPLETE buffer is left unmarked — its bytes stay valid through the spool promotion.
        if (!complete) buffer.markError("SABR stream destroyed")
        val keepFile = fromCache || complete
        buffer.release(deleteFile = !keepFile)
        // A whole from-0 drain becomes a persistent replay: the next play of this id needs NO network.
        if (!fromCache && complete && config != null) SabrSpool.promote(mediaId, config, buffer.file)
    }

    companion object {
        private const val TAG = "SabrAudioStream"
        // Pacing window (~7 min of opus): the drain stays this far ahead of the reader, no further.
        private const val AHEAD_AUDIO_BYTES = 8L * 1024 * 1024
        // A forward gap the sequential drain is allowed to close instead of a seek-restart.
        private const val CATCHUP_BYTES = 3L * 1024 * 1024
        private const val LAND_GRACE_MS = 4_000L
        private const val SEEK_MARGIN_MS = 5_000L
        private const val MAX_SEEK_RESTARTS = 6

        /** A stream over a COMPLETE persistent spool entry — serves reads with zero network. */
        fun fromSpool(mediaId: String, entry: SabrSpool.Entry): SabrAudioStream =
            SabrAudioStream(mediaId, null, null, SabrBuffer.completeFrom(entry.file, entry.contentLength))
    }
}

/**
 * The scheme SABR DataSpecs use. [SabrPlayerResolver] registers a [SabrConfig] under the media id and
 * hands ExoPlayer `sabr://<mediaId>`; [SabrDataSource] looks the config up here and attaches to (or
 * starts) the id's live [SabrAudioStream]. The registry OWNS stream lifetime ([SabrVideoRegistry]'s
 * pattern): installing a replacement destroys the old stream, [remove]/[clear] destroy outright, and a
 * small cap evicts the oldest streams (current + gapless-preloaded next stay live; spool files are on
 * disk, so eviction is about handles, not memory). Isolated from DIRECT / RELAY: nothing outside this
 * package produces this scheme.
 */
internal object SabrStreamRegistry {
    const val SCHEME = "sabr"
    private const val MAX_STREAMS = 3
    private val configs = ConcurrentHashMap<String, SabrConfig>()
    private val streams = ConcurrentHashMap<String, SabrAudioStream>()
    private val installSeq = AtomicLong(0)
    private val installOrder = ConcurrentHashMap<String, Long>()

    fun put(mediaId: String, config: SabrConfig) { configs[mediaId] = config }
    fun get(mediaId: String): SabrConfig? = configs[mediaId]

    /** The id's live stream, or null. Callers must check [SabrAudioStream.usable] before reusing. */
    fun stream(mediaId: String): SabrAudioStream? = streams[mediaId]

    /** Install [stream] as the id's live stream, destroying any replaced one and evicting the oldest. */
    fun installStream(mediaId: String, stream: SabrAudioStream) {
        streams.put(mediaId, stream)?.destroy()
        installOrder[mediaId] = installSeq.incrementAndGet()
        while (streams.size > MAX_STREAMS) {
            val eldest = installOrder.entries.filter { it.key != mediaId }.minByOrNull { it.value } ?: break
            remove(eldest.key)
        }
    }

    fun remove(mediaId: String) {
        configs.remove(mediaId)
        installOrder.remove(mediaId)
        streams.remove(mediaId)?.destroy()
    }

    /** Destroy every live stream + config — MusicService.onDestroy teardown. */
    fun clear() {
        for (id in streams.keys.toList()) remove(id)
        configs.clear()
        installOrder.clear()
    }

    fun uri(mediaId: String): Uri = Uri.parse("$SCHEME://$mediaId")
    fun mediaId(uri: Uri): String? = if (uri.scheme == SCHEME) uri.host ?: uri.schemeSpecificPart.trimStart('/') else null
}

/**
 * An ExoPlayer [DataSource] that speaks SABR: on [open] it attaches to the media id's live
 * [SabrAudioStream] (starting one from the registered config when absent), and [read] serves the
 * reassembled bytes at any position — the stream orchestrates covered serves, drain catch-ups and
 * seek-restarts (see [SabrAudioStream]). Length is the format's contentLength (known up front).
 * [close] drops only this source's references — the stream (and its registered config) live in
 * [SabrStreamRegistry] so a seek's close→reopen never re-resolves or re-drains.
 */
@UnstableApi
internal class SabrDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var stream: SabrAudioStream? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    // See SabrVideoDataSource: only fire transferEnded() when transferStarted() ran, or media3's
    // DefaultBandwidthMeter NPEs (null dataSpec) and closeQuietly won't swallow it.
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val mediaId = SabrStreamRegistry.mediaId(dataSpec.uri) ?: throw IOException("SABR: no media id in ${dataSpec.uri}")
        // Reuse the id's live stream (a seek reopen, a repeat-one replay of the completed buffer);
        // start a fresh one from the registered config when absent or failed.
        val s = SabrStreamRegistry.stream(mediaId)?.takeIf { it.usable() }
            ?: run {
                val config = SabrStreamRegistry.get(mediaId) ?: throw IOException("SABR: no session config for $mediaId")
                if (!SabrBuffer.lengthValid(config.format.contentLength)) {
                    throw IOException("SABR: contentLength out of range (${config.format.contentLength}) for $mediaId")
                }
                SabrAudioStream(mediaId, config, client).also { SabrStreamRegistry.installStream(mediaId, it) }
            }
        stream = s
        s.prepare(dataSpec.position)

        position = dataSpec.position
        bytesRemaining = if (s.length > 0) s.length - dataSpec.position else C.LENGTH_UNSET.toLong()
        transferStarted(dataSpec)
        started = true
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) readLength else minOf(readLength.toLong(), bytesRemaining).toInt()
        val n = stream?.read(position, target, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (n == -1) return C.RESULT_END_OF_INPUT
        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        // Deliberately does NOT cancel sessions or unregister: the stream + config are registry-owned
        // (SabrAudioStream's class doc) so a seek's close→reopen re-reads the same spool. The
        // registry's replace/evict/clear paths are the explicit end of the stream.
        stream = null
        if (started) { transferEnded(); started = false }
        uri = null
    }
}

/** Factory for [SabrDataSource]; the SABR OkHttp client carries generous timeouts (slow fresh resolves). */
@UnstableApi
internal class SabrDataSourceFactory(private val client: OkHttpClient) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrDataSource(client)
}
