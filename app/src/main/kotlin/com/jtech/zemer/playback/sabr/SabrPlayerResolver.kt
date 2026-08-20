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

    /**
     * A successful SABR audio resolution: the `sabr://` uri plus everything MusicService mirrors from
     * the DIRECT resolver — the /player response's stats URLs for the watch-time reporter, the resolved
     * itag, the client label for telemetry, and whether the client is web (ran the cipher, so the player
     * hash is attributable). Full DIRECT parity: a SABR listen seeds watch-time and telemetry the same way.
     */
    class SabrAudioResult internal constructor(
        val uri: Uri,
        val playbackTracking: com.metrolist.innertube.models.response.PlayerResponse.PlaybackTracking?,
        val itag: Int,
        val streamClient: String,
        val web: Boolean,
        /** The /player response's audioConfig loudness (DIRECT parity — audio normalization input). */
        val loudnessDb: Double?,
        /** The built session config — the download path drives a session from it, no registry. */
        internal val config: SabrConfig,
    )

    /**
     * Resolve [videoId] over the first [enabled] SABR client that works, or null (caller falls back).
     * [register] true (playback) installs the config in [SabrStreamRegistry] so the returned `sabr://`
     * uri opens; false (downloads) leaves the registry untouched — a download of the currently-playing
     * id must never clobber its live playback entry.
     */
    suspend fun resolve(
        videoId: String,
        enabled: Set<String>,
        audioQuality: com.jtech.zemer.constants.AudioQuality = com.jtech.zemer.constants.AudioQuality.AUTO,
        meteredNetwork: Boolean = false,
        cpn: () -> String? = { null },
        register: Boolean = true,
    ): SabrAudioResult? = withContext(Dispatchers.IO) {
        val visitorData = YouTube.visitorData ?: return@withContext null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return@withContext null
        val sts = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
        for (spec in ROSTER) {
            if (spec.key !in enabled) continue
            val result = tryClient(spec, videoId, pot, sts, audioQuality, meteredNetwork, cpn)
            if (result != null) {
                if (register) SabrStreamRegistry.put(videoId, result.config)
                return@withContext result
            }
        }
        null
    }

    /**
     * The DIRECT resolver's audio pick, mirrored exactly (YTPlayerUtils): bitrate weighted by the quality
     * preference (AUTO follows the metered state), with the same opus/webm streaming bonus.
     */
    internal fun <T> pickAudio(
        formats: List<T>,
        bitrate: (T) -> Int,
        mimeType: (T) -> String,
        audioQuality: com.jtech.zemer.constants.AudioQuality,
        meteredNetwork: Boolean,
    ): T? = formats.maxByOrNull {
        bitrate(it) * when (audioQuality) {
            com.jtech.zemer.constants.AudioQuality.AUTO -> if (meteredNetwork) -1 else 1
            com.jtech.zemer.constants.AudioQuality.HIGH -> 1
            com.jtech.zemer.constants.AudioQuality.LOW -> -1
        } + (if (mimeType(it).startsWith("audio/webm")) 10240 else 0)
    }

    private suspend fun tryClient(
        spec: Spec,
        videoId: String,
        pot: com.zemer.cipher.potoken.PoTokenResult,
        sts: Int?,
        audioQuality: com.jtech.zemer.constants.AudioQuality,
        meteredNetwork: Boolean,
        cpn: () -> String?,
    ): SabrAudioResult? {
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
            val fmt = pickAudio(
                streaming.adaptiveFormats.filter { it.isAudio && it.isOriginal },
                bitrate = { it.bitrate },
                mimeType = { it.mimeType },
                audioQuality = audioQuality,
                meteredNetwork = meteredNetwork,
            ) ?: return null
            val contentLength = fmt.contentLength ?: return null
            // Refuse a contentLength the reassembly buffer cannot hold (SabrBuffer would otherwise
            // fail loudly at open) — try the next roster client instead.
            if (!SabrBuffer.lengthValid(contentLength)) {
                Timber.tag(TAG).w("SABR ${spec.label} contentLength out of range for $videoId: $contentLength")
                return null
            }

            Timber.tag(TAG).d("SABR resolved $videoId via ${spec.label}: itag=${fmt.itag} contentLength=$contentLength")
            val config = SabrStreamResolver.buildConfig(
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
                cpn = cpn,
            )
            SabrAudioResult(
                uri = SabrStreamRegistry.uri(videoId),
                playbackTracking = response.playbackTracking,
                itag = fmt.itag,
                streamClient = spec.label,
                web = spec.web,
                loudnessDb = response.playerConfig?.audioConfig?.loudnessDb,
                config = config,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SABR resolve via ${spec.label} failed for $videoId: ${e.message}")
            null
        }
    }
}
