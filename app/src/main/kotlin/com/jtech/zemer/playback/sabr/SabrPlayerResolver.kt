package com.jtech.zemer.playback.sabr

import android.net.Uri
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Self-contained SABR resolution over a roster of SABR-USABLE clients (only those validated to deliver a
 * whole song over SABR with the app's pot in `tests/sabr-clients.mjs`). Fetches the `/player` response for
 * each ENABLED client in priority order, and the FIRST that exposes SABR inputs (serverAbrStreamingUrl +
 * ustreamer config) wins: registers a session and returns a `sabr://<videoId>` uri for ExoPlayer.
 *
 * Isolated from the DIRECT path. Reuses the app's [YouTube.player], the [PoTokenGenerator] WebView pot,
 * and the [CipherDeobfuscator] n-transform. Web clients (WEB_REMIX / TVHTML5_SIMPLY / MWEB) have a
 * CIPHERED serverAbrStreamingUrl - their `n` is n-transformed and the videoId pot is appended; direct
 * clients (VISIONOS) do neither.
 */
object SabrPlayerResolver {
    private const val TAG = "SabrPlayerResolver"
    private val poTokenGenerator = PoTokenGenerator()

    // Preference-key ids the settings toggles + MusicService use to enable/disable each client.
    const val KEY_WEB_REMIX = "WEB_REMIX"
    const val KEY_VISIONOS = "VISIONOS"
    const val KEY_TVHTML5_SIMPLY = "TVHTML5_SIMPLY"
    const val KEY_MWEB = "MWEB"

    private class Spec(
        val key: String,
        val client: YouTubeClient,
        val label: String,
        /** web = ciphered SABR url (n-transform + videoId url-pot + web pot/sts in /player). */
        val web: Boolean,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdk: Int? = null,
    )

    // Priority order. WEB_REMIX first (the app's main client); VISIONOS is the reliable pot-less direct one.
    private val ROSTER = listOf(
        Spec(KEY_WEB_REMIX, YouTubeClient.WEB_REMIX, "WEB_REMIX (SABR)", web = true, osName = "Windows", osVersion = "10.0"),
        Spec(KEY_VISIONOS, YouTubeClient.VISIONOS, "VISIONOS (SABR)", web = false, osName = "visionOS", osVersion = "26.5.23O471", deviceMake = "Apple", deviceModel = "RealityDevice17,1"),
        Spec(KEY_TVHTML5_SIMPLY, YouTubeClient.TVHTML5_SIMPLY, "TVHTML5_SIMPLY (SABR)", web = true),
        Spec(KEY_MWEB, YouTubeClient.MWEB, "MWEB (SABR)", web = true),
    )

    /** Resolve [videoId] over the first [enabled] SABR client that works, or null (caller falls back). */
    suspend fun resolve(videoId: String, enabled: Set<String>): Uri? = withContext(Dispatchers.IO) {
        val visitorData = YouTube.visitorData ?: return@withContext null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return@withContext null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val uri = tryClient(spec, videoId, pot, sts)
            if (uri != null) return@withContext uri
        }
        null
    }

    private suspend fun tryClient(
        spec: Spec,
        videoId: String,
        pot: com.zemer.cipher.potoken.PoTokenResult,
        sts: Int?,
    ): Uri? {
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
            val fmt = streaming.adaptiveFormats.filter { it.isAudio && it.isOriginal }.maxByOrNull { it.bitrate } ?: return null
            val contentLength = fmt.contentLength ?: return null

            Timber.tag(TAG).d("SABR resolved $videoId via ${spec.label}: itag=${fmt.itag} contentLength=$contentLength")
            SabrStreamResolver.register(
                mediaId = videoId,
                serverAbrStreamingUrl = sabrUrl,
                ustreamerConfigBase64 = ustreamer,
                itag = fmt.itag,
                lastModified = fmt.lastModified ?: 0L,
                contentLength = contentLength,
                poTokenBase64Url = pot.playerRequestPoToken, // session/visitorData-bound = streamerContext pot
                client = SabrStreamResolver.Client(
                    clientName = spec.client.clientId.toInt(),
                    clientVersion = spec.client.clientVersion,
                    osName = spec.osName,
                    osVersion = spec.osVersion,
                    deviceMake = spec.deviceMake,
                    deviceModel = spec.deviceModel,
                    androidSdkVersion = spec.androidSdk,
                ),
                userAgent = spec.client.userAgent,
                // Web clients cipher the SABR url; direct clients use it as-is.
                nTransform = if (spec.web) { url -> kotlinx.coroutines.runBlocking { CipherDeobfuscator.transformNParamInUrl(url) } } else { url -> url },
                urlPotBase64Url = if (spec.web) pot.streamingDataPoToken else null,
                streamClientLabel = spec.label,
                mimeType = fmt.mimeType,
                bitrate = fmt.bitrate,
                audioSampleRate = fmt.audioSampleRate,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
