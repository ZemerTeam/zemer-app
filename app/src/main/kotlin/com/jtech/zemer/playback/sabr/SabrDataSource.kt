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

/**
 * The scheme SABR DataSpecs use. [SabrStreamResolver] registers a [SabrConfig] under the media id and
 * hands ExoPlayer `sabr://<mediaId>`; [SabrDataSource] looks the config up here. Isolated from DIRECT /
 * RELAY: nothing outside this package produces this scheme.
 */
internal object SabrStreamRegistry {
    const val SCHEME = "sabr"
    private val configs = ConcurrentHashMap<String, SabrConfig>()

    fun put(mediaId: String, config: SabrConfig) { configs[mediaId] = config }
    fun get(mediaId: String): SabrConfig? = configs[mediaId]
    fun remove(mediaId: String) { configs.remove(mediaId) }
    fun uri(mediaId: String): Uri = Uri.parse("$SCHEME://$mediaId")
    fun mediaId(uri: Uri): String? = if (uri.scheme == SCHEME) uri.host ?: uri.schemeSpecificPart.trimStart('/') else null
}

/**
 * An ExoPlayer [DataSource] that speaks SABR: on [open] it starts a [SabrSession] streaming the whole
 * audio format into an in-memory [SabrBuffer]; [read] serves the reassembled bytes at any position,
 * blocking until they arrive. Length is the format's contentLength (known up front). Backward seeks are
 * free (whole stream buffered); a forward seek blocks until the sequential drain reaches that offset.
 */
@UnstableApi
internal class SabrDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var buffer: SabrBuffer? = null
    private var session: SabrSession? = null
    private var thread: Thread? = null
    private var position: Long = 0
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val mediaId = SabrStreamRegistry.mediaId(dataSpec.uri) ?: throw IOException("SABR: no media id in ${dataSpec.uri}")
        val config = SabrStreamRegistry.get(mediaId) ?: throw IOException("SABR: no session config for $mediaId")

        val length = config.format.contentLength
        val buf = SabrBuffer(length)
        val ses = SabrSession(config, client, buf)
        buffer = buf
        session = ses
        thread = Thread(ses, "sabr-$mediaId").apply { isDaemon = true; start() }

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
        session?.cancel()
        session = null
        buffer = null
        thread = null
        uri?.let { transferEnded() }
        uri = null
    }
}

/** Factory for [SabrDataSource]; the SABR OkHttp client carries generous timeouts (slow fresh resolves). */
@UnstableApi
internal class SabrDataSourceFactory(private val client: OkHttpClient) : DataSource.Factory {
    override fun createDataSource(): DataSource = SabrDataSource(client)
}
