package com.jtech.zemer.playback.sabr

import android.net.Uri
import android.util.Base64
import com.jtech.zemer.playback.VideoDecoderCaps
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
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
 * `/player` for the first ENABLED SABR-usable client that exposes SABR inputs, PINS the exact video itag
 * for the requested quality via `preferredVideoFormatId` (field 17, proven in `tests/sabr-video.mjs`) plus
 * the best audio, registers a shared [SabrVideoStream], and returns the `sabrvideo://<id>` uri + the full
 * quality ladder (so the in-player switcher offers the SAME rungs the DIRECT path does). Playback wraps
 * the video with the paired `sabraudio://<id>` in a `MergingMediaSource`. Isolated from DIRECT/RELAY.
 */
object SabrVideoResolver {
    private const val TAG = "SabrVideoResolver"
    private const val AUTO_HEIGHT = 720 // SABR must pin an itag even for AUTO — cap the automatic pick here
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

    /** A resolved SABR video session: the uri to play, the switcher ladder, and the rung actually pinned. */
    class SabrVideoResult(val uri: Uri, val ladder: List<VideoQualityRung>, val chosen: VideoQualityRung)

    /**
     * Resolve [videoId] for video playback over the first working [enabled] client at [targetLabel]
     * (a quality label like "720p", or [VideoQualityLogic.AUTO]). Registers the shared stream and returns
     * the uri + ladder + chosen rung, or null (caller reverts to audio).
     */
    suspend fun resolve(videoId: String, enabled: Set<String>, targetLabel: String): SabrVideoResult? =
        withContext(Dispatchers.IO) {
            val built = firstWorkingBuilt(videoId, enabled, targetLabel) ?: return@withContext null
            SabrVideoRegistry.put(videoId, SabrVideoStream(built.config, SabrStreamResolver.sharedClient()))
            SabrVideoResult(SabrVideoRegistry.videoUri(videoId), built.ladder, built.chosen)
        }

    /** The mime of the video track SABR downloaded, so the caller picks the muxer container (mp4/webm). */
    class VideoDownloadInfo(val videoMimeType: String, val audioMimeType: String, val webm: Boolean)

    /**
     * Download the WHOLE video + audio for [videoId] (over the first working [enabled] client at
     * [targetLabel]) to [videoFile] and [audioFile], byte-exact. Returns null on an incomplete drain
     * (a capped client) so the caller retries / falls back — a truncated track is never muxed.
     */
    suspend fun download(videoId: String, enabled: Set<String>, targetLabel: String, videoFile: File, audioFile: File): VideoDownloadInfo? =
        withContext(Dispatchers.IO) {
            val config = firstWorkingBuilt(videoId, enabled, targetLabel)?.config ?: return@withContext null
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

    private class Built(val config: SabrVideoConfig, val ladder: List<VideoQualityRung>, val chosen: VideoQualityRung)

    /** Resolve the first ENABLED client that exposes SABR video inputs into a ready [Built]. */
    private suspend fun firstWorkingBuilt(videoId: String, enabled: Set<String>, targetLabel: String): Built? {
        val visitorData = YouTube.visitorData ?: return null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val built = build(spec, videoId, pot, sts, targetLabel)
            if (built != null) return built
        }
        return null
    }

    /** Pick the rung for [targetLabel]; AUTO (selectRung returns null) caps at [AUTO_HEIGHT]. */
    private fun pickRung(ladder: List<VideoQualityRung>, targetLabel: String): VideoQualityRung? =
        VideoQualityLogic.selectRung(ladder, targetLabel)
            ?: ladder.filter { it.height <= AUTO_HEIGHT }.maxByOrNull { it.height }
            ?: ladder.minByOrNull { it.height }

    private suspend fun build(spec: Spec, videoId: String, pot: PoTokenResult, sts: Int?, targetLabel: String): Built? {
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
            val audioLen = audio.contentLength ?: return null
            // The same ladder the DIRECT switcher renders, minus progressive (SABR video is dual-track,
            // video-only + audio) and minus rungs this device can't decode (never pin an undecodable itag).
            val ladder = VideoQualityLogic.rungs(streaming).filter { !it.progressive && VideoDecoderCaps.supports(it) }
            if (ladder.isEmpty()) return null
            val chosen = pickRung(ladder, targetLabel) ?: return null
            val videoFmt = streaming.adaptiveFormats.firstOrNull { it.itag == chosen.itag } ?: return null
            val videoLen = videoFmt.contentLength ?: return null

            Timber.tag(TAG).d("SABR video resolved $videoId via ${spec.label}: video itag=${chosen.itag} ${chosen.label}, audio itag=${audio.itag}")
            val nTransform: (String) -> String =
                if (spec.web) { url -> runBlocking { CipherDeobfuscator.transformNParamInUrl(url) } } else { url -> url }
            val config = SabrVideoConfig(
                sabrUrl = sabrUrl,
                ustreamerConfig = decodeBase64(ustreamer),
                videoFormat = SabrMessages.Format(videoFmt.itag, videoFmt.lastModified ?: 0L, videoLen),
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
            Built(config, ladder, chosen)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR video resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
