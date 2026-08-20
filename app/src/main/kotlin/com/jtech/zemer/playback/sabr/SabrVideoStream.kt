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
 * [videoBuffer], the audio DataSource reads [audioBuffer]. The session starts lazily on the first
 * DataSource open and is cancelled when the last one closes (ref-counted), so the two children never
 * start two sessions and the thread never outlives playback.
 */
@UnstableApi
internal class SabrVideoStream(val config: SabrVideoConfig, private val client: OkHttpClient) {
    val videoBuffer = SabrBuffer(config.videoFormat.contentLength)
    val audioBuffer = SabrBuffer(config.audioFormat.contentLength)
    private var thread: Thread? = null
    private var session: SabrVideoSession? = null
    private var refs = 0

    @Synchronized fun attach(mediaId: String) {
        refs++
        if (thread == null) {
            val s = SabrVideoSession(config, client, videoBuffer, audioBuffer)
            session = s
            thread = Thread(s, "sabr-video-$mediaId").apply { isDaemon = true; start() }
        }
    }

    @Synchronized fun release() {
        if (refs > 0) refs--
        if (refs == 0) { session?.cancel(); session = null; thread = null }
    }
}

/**
 * Registry mapping a media id to its live [SabrVideoStream]. The two track DataSources
 * (`sabrvideo://<id>` and `sabraudio://<id>`) resolve the SAME stream here. Isolated from the
 * audio-only [SabrStreamRegistry].
 */
@UnstableApi
internal object SabrVideoRegistry {
    const val SCHEME_VIDEO = "sabrvideo"
    const val SCHEME_AUDIO = "sabraudio"
    private val streams = java.util.concurrent.ConcurrentHashMap<String, SabrVideoStream>()

    fun put(mediaId: String, stream: SabrVideoStream) { streams[mediaId] = stream }
    fun get(mediaId: String): SabrVideoStream? = streams[mediaId]
    fun remove(mediaId: String) { streams.remove(mediaId) }
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
        uri?.let { transferEnded() }
        uri = null
    }
}

/** Factory for one track's [SabrVideoDataSource]. */
@UnstableApi
internal class SabrVideoDataSourceFactory(private val client: OkHttpClient, private val video: Boolean) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrVideoDataSource(client, video)
}
