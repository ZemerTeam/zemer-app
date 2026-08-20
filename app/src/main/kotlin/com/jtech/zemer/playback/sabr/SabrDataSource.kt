package com.jtech.zemer.playback.sabr

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One live audio SABR stream: the reassembly buffer plus the session that fills it. The audio sibling of
 * [SabrVideoStream], with the same explicit lifecycle: the session starts on the first [attach] and runs
 * until [destroy] — NEVER tied to a DataSource close. media3 closes and reopens the DataSource on every
 * seek outside its sample buffer, and the first design (session per open) killed the whole reassembled
 * stream on each of those, re-running a full /player + poToken resolve and re-draining from byte 0 —
 * exactly the close→reopen churn the video path was rebuilt to survive. Reopens re-read the accumulating
 * buffer (backward seeks free; a forward seek blocks until the drain reaches it).
 */
internal class SabrAudioStream(private val mediaId: String, val config: SabrConfig, private val client: OkHttpClient) {
    val buffer = SabrBuffer(config.format.contentLength)
    val createdAtMs: Long = System.currentTimeMillis()
    private var thread: Thread? = null
    private var session: SabrSession? = null
    private var destroyed = false

    /** First attach starts the ONE session; later attaches (seek reopen churn) reuse it. */
    @Synchronized fun attach() {
        if (thread == null && !destroyed) {
            val s = SabrSession(config, client, buffer)
            session = s
            thread = Thread(s, "sabr-$mediaId").apply { isDaemon = true; start() }
        }
    }

    /** Whether this stream can serve another open: not destroyed and its session has not failed. */
    @Synchronized fun usable(): Boolean = !destroyed && !buffer.failed()

    /** Kill the drain. Called ONLY by [SabrStreamRegistry] on replace/remove/clear. */
    @Synchronized fun destroy() {
        destroyed = true
        session?.cancel()
        session = null
        thread = null
        // Wake any reader parked in SabrBuffer.read (a cancelled session skips marking on its own).
        buffer.markError("SABR stream destroyed")
    }
}

/**
 * The scheme SABR DataSpecs use. [SabrPlayerResolver] registers a [SabrConfig] under the media id and
 * hands ExoPlayer `sabr://<mediaId>`; [SabrDataSource] looks the config up here and attaches to (or
 * starts) the id's live [SabrAudioStream]. The registry OWNS stream lifetime ([SabrVideoRegistry]'s
 * pattern): installing a replacement destroys the old stream, [remove]/[clear] destroy outright, and a
 * small cap evicts the oldest streams so whole-track buffers are never retained per unique track for the
 * whole listening session (current + gapless-preloaded next stay live). Isolated from DIRECT / RELAY:
 * nothing outside this package produces this scheme.
 */
internal object SabrStreamRegistry {
    const val SCHEME = "sabr"
    // Keep the current track + the gapless-preloaded next (+1 slack) alive; evict the oldest beyond that.
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
 * reassembled bytes at any position, blocking until they arrive. Length is the format's contentLength
 * (known up front). Backward seeks are free (whole stream buffered); a forward seek blocks until the
 * sequential drain reaches that offset. [close] drops only this source's references — the stream (and
 * its registered config) live in [SabrStreamRegistry] so a seek's close→reopen never re-resolves or
 * re-drains (see [SabrAudioStream]).
 */
@UnstableApi
internal class SabrDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var buffer: SabrBuffer? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    // See SabrVideoDataSource: only fire transferEnded() when transferStarted() ran, or media3's
    // DefaultBandwidthMeter NPEs (null dataSpec) and closeQuietly won't swallow it.
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val mediaId = SabrStreamRegistry.mediaId(dataSpec.uri) ?: throw IOException("SABR: no media id in ${dataSpec.uri}")
        val config = SabrStreamRegistry.get(mediaId) ?: throw IOException("SABR: no session config for $mediaId")

        val length = config.format.contentLength
        if (!SabrBuffer.lengthValid(length)) throw IOException("SABR: contentLength out of range ($length) for $mediaId")
        // Reuse the id's live stream (a seek reopen, a repeat-one replay of the completed buffer);
        // start a fresh one from the registered config when absent or failed.
        val stream = SabrStreamRegistry.stream(mediaId)?.takeIf { it.usable() }
            ?: SabrAudioStream(mediaId, config, client).also { SabrStreamRegistry.installStream(mediaId, it) }
        stream.attach()
        buffer = stream.buffer

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
        val n = buffer?.read(position, target, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (n == -1) return C.RESULT_END_OF_INPUT
        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        // Deliberately does NOT cancel the session or unregister: the stream + config are
        // registry-owned (SabrAudioStream's class doc) so a seek's close→reopen re-reads the same
        // buffer. The registry's replace/evict/clear paths are the explicit end of the stream.
        buffer = null
        if (started) { transferEnded(); started = false }
        uri = null
    }
}

/** Factory for [SabrDataSource]; the SABR OkHttp client carries generous timeouts (slow fresh resolves). */
@UnstableApi
internal class SabrDataSourceFactory(private val client: OkHttpClient) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrDataSource(client)
}
