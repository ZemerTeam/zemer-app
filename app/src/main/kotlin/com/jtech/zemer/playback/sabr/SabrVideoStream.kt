package com.jtech.zemer.playback.sabr

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * A single dual-track SABR session shared by the two ExoPlayer [DataSource]s of a `MergingMediaSource`
 * (video-only + audio). One [SabrVideoSession] fills BOTH buffers; the video DataSource reads
 * [videoBuffer], the audio DataSource reads [audioBuffer].
 *
 * LIFECYCLE — explicit, NEVER tied to DataSource open/close cycles. media3's progressive loading opens
 * and closes the two children UN-paired: entering video mode seeks mid-track, and once the period
 * prepares, media3 CANCELS the in-flight loads and re-opens both children at the seek offset — so there
 * is always a close→reopen gap where zero DataSources are open while playback very much continues. A
 * ref-counted "cancel + unregister at zero" (the first design) killed the session and wiped the registry
 * entry inside exactly that gap, and the reopen then failed with "no session" (found on-device). Instead:
 * the ONE session starts on the first [attach] and runs until [destroy] — called only by
 * [SabrVideoRegistry] when the entry is removed (video mode exits) or replaced (a new resolve). Reopens
 * just re-read the accumulating buffers (backward seeks free; a forward seek blocks until the drain
 * reaches it).
 */
@UnstableApi
internal class SabrVideoStream(val config: SabrVideoConfig, private val client: OkHttpClient) {
    val videoBuffer = SabrBuffer(config.videoFormat.contentLength)
    val audioBuffer = SabrBuffer(config.audioFormat.contentLength)
    private var thread: Thread? = null
    private var session: SabrVideoSession? = null
    private var destroyed = false
    private var refs = 0

    /** First attach starts the ONE session; later attaches (seek/merge reopen churn) reuse it. */
    @Synchronized fun attach(mediaId: String) {
        refs++
        if (thread == null && !destroyed) {
            val s = SabrVideoSession(config, client, videoBuffer, audioBuffer)
            session = s
            thread = Thread(s, "sabr-video-$mediaId").apply { isDaemon = true; start() }
        }
    }

    /** A DataSource closed. Deliberately does NOT cancel the session or unregister — see class doc. */
    @Synchronized fun release() {
        if (refs > 0) refs--
    }

    /** Kill the drain. Called ONLY by the registry on remove/replace — the explicit end of this stream. */
    @Synchronized fun destroy() {
        destroyed = true
        session?.cancel()
        session = null
        thread = null
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
    fun videoUri(mediaId: String): Uri = Uri.parse("$SCHEME_VIDEO://$mediaId")
    fun audioUri(mediaId: String): Uri = Uri.parse("$SCHEME_AUDIO://$mediaId")
    fun mediaId(uri: Uri): String? =
        if (uri.scheme == SCHEME_VIDEO || uri.scheme == SCHEME_AUDIO) uri.host ?: uri.schemeSpecificPart.trimStart('/') else null
}

/**
 * ExoPlayer [DataSource] for one track ([video]=true reads the video buffer, else the audio buffer) of a
 * shared [SabrVideoStream]. Length is the track format's contentLength; reads block on the reassembled
 * bytes exactly like [SabrDataSource]. Backward seeks are free (whole track buffered in memory).
 */
@UnstableApi
internal class SabrVideoDataSource(private val client: OkHttpClient, private val video: Boolean) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var mediaId: String? = null
    private var stream: SabrVideoStream? = null
    private var buffer: SabrBuffer? = null
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
        mediaId = id
        val s = SabrVideoRegistry.get(id) ?: throw IOException("SABR video: no session for $id")
        stream = s
        s.attach(id)
        val buf = if (video) s.videoBuffer else s.audioBuffer
        buffer = buf
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
        val n = buffer?.read(position, target, offset, toRead) ?: return C.RESULT_END_OF_INPUT
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
        buffer = null
        mediaId = null
        if (started) { transferEnded(); started = false }
        uri = null
    }
}

/** Factory for one track's [SabrVideoDataSource]. */
@UnstableApi
internal class SabrVideoDataSourceFactory(private val client: OkHttpClient, private val video: Boolean) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrVideoDataSource(client, video)
}
