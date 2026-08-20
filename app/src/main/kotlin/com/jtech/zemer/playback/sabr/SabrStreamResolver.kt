package com.jtech.zemer.playback.sabr

import android.util.Base64
import com.metrolist.innertube.utils.ResilientDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Turns a resolved `/player` response into a playable SABR DataSpec. The streaming pipeline (when SABR
 * mode is on for a SABR-capable client — WEB_REMIX / TVHTML5_SIMPLY / VISIONOS, validated whole-song in
 * `tests/sabr-clients.mjs`) calls [register] with the pieces it already has, then hands ExoPlayer the
 * returned `sabr://<mediaId>` uri; [SabrDataSource] does the rest.
 *
 * Inputs are primitives so this stays decoupled from the innertube models and unit-testable. The caller
 * supplies [nTransform] (the cipher n-parameter transform) for web clients whose serverAbrStreamingUrl is
 * ciphered; direct clients pass the identity function.
 */
object SabrStreamResolver {

    /** One shared OkHttp client for all SABR sessions — generous timeouts for slow fresh resolves. */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ResilientDns())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    internal fun dataSourceFactory(): SabrDataSourceFactory = SabrDataSourceFactory(client)

    /** The shared SABR OkHttp client, reused by the dual-track video path ([SabrVideoResolver]). */
    internal fun sharedClient(): OkHttpClient = client

    /** The mime type of the audio SABR downloaded, for the caller to pick a file extension. */
    class DownloadInfo(val mimeType: String, val contentLength: Long)

    /**
     * Download the WHOLE SABR audio for [videoId] (over the first working [enabled] client) to [file].
     * Resolves like playback, runs the session to completion, and writes the byte-exact reassembled stream.
     * Returns the [DownloadInfo] on a COMPLETE download, or null (caller falls back / fails) - a truncated
     * stream (e.g. a capped client) is never saved. The DIRECT/RELAY download paths are untouched.
     */
    suspend fun download(videoId: String, enabled: Set<String>, file: File): DownloadInfo? =
        withContext(Dispatchers.IO) {
            SabrPlayerResolver.resolve(videoId, enabled) ?: return@withContext null
            val cfg = SabrStreamRegistry.get(videoId) ?: return@withContext null
            try {
                val buffer = SabrBuffer(cfg.format.contentLength)
                SabrSession(cfg, client, buffer).run() // blocks on this thread until the stream is drained
                val size = buffer.available()
                if (size <= 0L || (cfg.format.contentLength > 0 && size < cfg.format.contentLength)) {
                    Timber.tag("SabrDownload").w("SABR download incomplete for $videoId: $size/${cfg.format.contentLength}")
                    return@withContext null
                }
                file.outputStream().use { out ->
                    val chunk = ByteArray(64 * 1024)
                    var pos = 0L
                    while (pos < size) {
                        val n = buffer.read(pos, chunk, 0, chunk.size)
                        if (n <= 0) break
                        out.write(chunk, 0, n)
                        pos += n
                    }
                }
                if (file.length() <= 0L) null else DownloadInfo(cfg.mimeType, size)
            } finally {
                SabrStreamRegistry.remove(videoId)
            }
        }

    /** Decode base64 that may be standard (+/) or url-safe (-_), padded or not - never throws on either. */
    private fun decodeBase64(s: String): ByteArray {
        val normalized = s.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded, Base64.NO_WRAP)
    }

    /** Client identity for the SABR streamerContext.clientInfo — must match the `/player` request. */
    class Client(
        val clientName: Int,
        val clientVersion: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
    )

    /**
     * Register a SABR session for [mediaId] and return the `sabr://` uri ExoPlayer should open.
     *
     * @param serverAbrStreamingUrl streamingData.serverAbrStreamingUrl from the /player response
     * @param ustreamerConfigBase64 playerConfig.mediaCommonConfig.mediaUstreamerRequestConfig.videoPlaybackUstreamerConfig
     * @param poTokenBase64Url the app's minted GVS poToken (bgutils, visitorData-bound), base64url
     * @param nTransform cipher n-transform for web clients; identity for direct clients
     */
    fun register(
        mediaId: String,
        serverAbrStreamingUrl: String,
        ustreamerConfigBase64: String,
        itag: Int,
        lastModified: Long,
        contentLength: Long,
        poTokenBase64Url: String,
        client: Client,
        userAgent: String,
        nTransform: (String) -> String,
        urlPotBase64Url: String? = null,
        streamClientLabel: String = "SABR",
        mimeType: String = "",
        bitrate: Int = 0,
        audioSampleRate: Int? = null,
    ): android.net.Uri {
        val config = SabrConfig(
            sabrUrl = serverAbrStreamingUrl,
            ustreamerConfig = decodeBase64(ustreamerConfigBase64),
            format = SabrMessages.Format(itag, lastModified, contentLength),
            // The streamerContext poToken as raw bytes. Decode tolerantly: the app's PoTokenGenerator
            // emits STANDARD base64 (+/), bgutils emits url-safe (-_); normalize either to standard,
            // pad, and decode - a strict URL_SAFE decode throws "bad base-64" on a standard token.
            poToken = decodeBase64(poTokenBase64Url),
            clientInfo = SabrMessages.ClientInfo(
                clientName = client.clientName,
                clientVersion = client.clientVersion,
                osName = client.osName,
                osVersion = client.osVersion,
                deviceMake = client.deviceMake,
                deviceModel = client.deviceModel,
                androidSdkVersion = client.androidSdkVersion,
            ),
            userAgent = userAgent,
            nTransform = nTransform,
            urlPot = urlPotBase64Url,
            streamClientLabel = streamClientLabel,
            mimeType = mimeType,
            bitrate = bitrate,
            audioSampleRate = audioSampleRate,
        )
        SabrStreamRegistry.put(mediaId, config)
        return SabrStreamRegistry.uri(mediaId)
    }
}
