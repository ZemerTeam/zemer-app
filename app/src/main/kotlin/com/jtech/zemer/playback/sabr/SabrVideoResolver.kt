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
        val visitorData = YouTube.visitorData ?: return@withContext null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return@withContext null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val uri = tryClient(spec, videoId, pot, sts, maxHeightPx)
            if (uri != null) return@withContext uri
        }
        null
    }

    private suspend fun tryClient(spec: Spec, videoId: String, pot: PoTokenResult, sts: Int?, maxHeightPx: Int): Uri? {
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
            SabrVideoRegistry.put(videoId, SabrVideoStream(config, SabrStreamResolver.sharedClient()))
            SabrVideoRegistry.videoUri(videoId)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR video resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
