package com.jtech.zemer.playback.sabr

import android.net.Uri
import android.util.Base64
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import com.zemer.cipher.potoken.PoTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Dual-track (video + audio) SABR resolution — the video counterpart of [SabrPlayerResolver]. Fetches the
 * `/player` for the first ENABLED SABR-usable client that exposes SABR inputs, pins a video-only rung at
 * or below the requested quality (via `preferredVideoFormatId`, proven in `tests/sabr-video.mjs`) plus the
 * best audio, registers a shared [SabrVideoStream], and returns the `sabrvideo://<id>` uri. Playback wraps
 * that (video) with the paired `sabraudio://<id>` in a `MergingMediaSource`. Isolated from DIRECT/RELAY.
 */
object SabrVideoResolver {
    private const val TAG = "SabrVideoResolver"
    private val poTokenGenerator = PoTokenGenerator()

    private class Spec(
        val key: String,
        val client: YouTubeClient,
        val label: String,
        val web: Boolean,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdk: Int? = null,
    )

    // Same roster + priority as SabrPlayerResolver (the video+audio usable set is identical — proven in
    // tests/sabr-video-clients.mjs), keyed off the same SabrPlayerResolver.KEY_* toggles.
    private val ROSTER = listOf(
        Spec(SabrPlayerResolver.KEY_WEB_REMIX, YouTubeClient.WEB_REMIX, "WEB_REMIX (SABR)", web = true, osName = "Windows", osVersion = "10.0"),
        Spec(SabrPlayerResolver.KEY_VISIONOS, YouTubeClient.VISIONOS, "VISIONOS (SABR)", web = false, osName = "visionOS", osVersion = "26.5.23O471", deviceMake = "Apple", deviceModel = "RealityDevice17,1"),
        Spec(SabrPlayerResolver.KEY_TVHTML5_SIMPLY, YouTubeClient.TVHTML5_SIMPLY, "TVHTML5_SIMPLY (SABR)", web = true),
        Spec(SabrPlayerResolver.KEY_MWEB, YouTubeClient.MWEB, "MWEB (SABR)", web = true),
    )

    private fun decodeBase64(s: String): ByteArray {
        val normalized = s.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded, Base64.NO_WRAP)
    }

    /**
     * Resolve [videoId] for video playback over the first working [enabled] client at or below
     * [maxHeightPx]. Registers the shared stream and returns the `sabrvideo://` uri, or null (caller
     * reverts to audio). The paired audio uri is [SabrVideoRegistry.audioUri] of the same id.
     */
    suspend fun resolve(videoId: String, enabled: Set<String>, maxHeightPx: Int): Uri? = withContext(Dispatchers.IO) {
        val config = firstWorkingConfig(videoId, enabled, maxHeightPx) ?: return@withContext null
        SabrVideoRegistry.put(videoId, SabrVideoStream(config, SabrStreamResolver.sharedClient()))
        SabrVideoRegistry.videoUri(videoId)
    }

    /** The mime of the video track SABR downloaded, so the caller picks the muxer container (mp4/webm). */
    class VideoDownloadInfo(val videoMimeType: String, val audioMimeType: String, val webm: Boolean)

    /**
     * Download the WHOLE video + audio for [videoId] (over the first working [enabled] client, pinned at
     * or under [maxHeightPx]) to [videoFile] and [audioFile], byte-exact. Returns null on an incomplete
     * drain (a capped client) so the caller retries / falls back — a truncated track is never muxed.
     */
    suspend fun download(videoId: String, enabled: Set<String>, maxHeightPx: Int, videoFile: File, audioFile: File): VideoDownloadInfo? =
        withContext(Dispatchers.IO) {
            val config = firstWorkingConfig(videoId, enabled, maxHeightPx) ?: return@withContext null
            val videoBuf = SabrBuffer(config.videoFormat.contentLength)
            val audioBuf = SabrBuffer(config.audioFormat.contentLength)
            SabrVideoSession(config, SabrStreamResolver.sharedClient(), videoBuf, audioBuf).run() // blocks until drained
            if (!drainWhole(videoBuf, config.videoFormat.contentLength) || !drainWhole(audioBuf, config.audioFormat.contentLength)) {
                Timber.tag(TAG).w("SABR video download incomplete for $videoId: video ${videoBuf.available()}/${config.videoFormat.contentLength}, audio ${audioBuf.available()}/${config.audioFormat.contentLength}")
                return@withContext null
            }
            writeBuffer(videoBuf, videoFile)
            writeBuffer(audioBuf, audioFile)
            if (videoFile.length() <= 0L || audioFile.length() <= 0L) return@withContext null
            val webm = config.videoMimeType.contains("webm")
            VideoDownloadInfo(config.videoMimeType, if (webm) "audio/webm" else "audio/mp4", webm)
        }

    private fun drainWhole(buf: SabrBuffer, contentLength: Long): Boolean =
        contentLength > 0 && buf.available() >= contentLength

    private fun writeBuffer(buf: SabrBuffer, file: File) {
        val size = buf.available()
        file.outputStream().use { out ->
            val chunk = ByteArray(64 * 1024)
            var pos = 0L
            while (pos < size) {
                val n = buf.read(pos, chunk, 0, chunk.size)
                if (n <= 0) break
                out.write(chunk, 0, n)
                pos += n
            }
        }
    }

    /** Resolve the first ENABLED client that exposes SABR video inputs into a ready [SabrVideoConfig]. */
    private suspend fun firstWorkingConfig(videoId: String, enabled: Set<String>, maxHeightPx: Int): SabrVideoConfig? {
        val visitorData = YouTube.visitorData ?: return null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val config = buildConfig(spec, videoId, pot, sts, maxHeightPx)
            if (config != null) return config
        }
        return null
    }

    private suspend fun buildConfig(spec: Spec, videoId: String, pot: PoTokenResult, sts: Int?, maxHeightPx: Int): SabrVideoConfig? {
        return try {
            val response = YouTube.player(
                videoId,
                client = spec.client,
                signatureTimestamp = if (spec.web) sts else null,
                webPlayerPot = if (spec.web) pot.playerRequestPoToken else null,
            ).getOrNull() ?: return null
            if (response.playabilityStatus.status != "OK") return null
            val streaming = response.streamingData ?: return null
            val sabrUrl = streaming.serverAbrStreamingUrl ?: return null
            val ustreamer = response.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig
                ?: return null

            val audio = streaming.adaptiveFormats.filter { it.isAudio && it.isOriginal }.maxByOrNull { it.bitrate } ?: return null
            val videoRungs = streaming.adaptiveFormats
                .filter { !it.isAudio && it.width != null && it.contentLength != null && it.mimeType.startsWith("video/") }
                .map { SabrVideoQuality.Rung(it.itag, it.height ?: 0, it.mimeType, it.bitrate, it.contentLength!!) }
            val rung = SabrVideoQuality.select(videoRungs, maxHeightPx) ?: return null
            val videoFmt = streaming.adaptiveFormats.first { it.itag == rung.itag }
            val audioLen = audio.contentLength ?: return null

            Timber.tag(TAG).d("SABR video resolved $videoId via ${spec.label}: video itag=${rung.itag} ${rung.height}p, audio itag=${audio.itag}")
            val nTransform: (String) -> String =
                if (spec.web) { url -> runBlocking { CipherDeobfuscator.transformNParamInUrl(url) } } else { url -> url }
            val config = SabrVideoConfig(
                sabrUrl = sabrUrl,
                ustreamerConfig = decodeBase64(ustreamer),
                videoFormat = SabrMessages.Format(videoFmt.itag, videoFmt.lastModified ?: 0L, rung.contentLength),
                audioFormat = SabrMessages.Format(audio.itag, audio.lastModified ?: 0L, audioLen),
                poToken = decodeBase64(pot.playerRequestPoToken),
                clientInfo = SabrMessages.ClientInfo(
                    clientName = spec.client.clientId.toInt(),
                    clientVersion = spec.client.clientVersion,
                    osName = spec.osName,
                    osVersion = spec.osVersion,
                    deviceMake = spec.deviceMake,
                    deviceModel = spec.deviceModel,
                    androidSdkVersion = spec.androidSdk,
                ),
                userAgent = spec.client.userAgent,
                nTransform = nTransform,
                urlPot = if (spec.web) pot.streamingDataPoToken else null,
                streamClientLabel = spec.label,
                videoMimeType = videoFmt.mimeType,
                videoBitrate = videoFmt.bitrate,
            )
            config
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR video resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
