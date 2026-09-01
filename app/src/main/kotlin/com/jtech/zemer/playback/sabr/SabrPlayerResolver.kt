package com.jtech.zemer.playback.sabr

import android.net.Uri
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

    // ---- resolve cache (DIRECT's songUrlCache parity): one /player + poToken serves replays of the
    // same id within the TTL (well under the ~6h URL expiry). Playback-only — the download path passes
    // register=false AND must not inherit a playback config (its cpn stamps the listen's nonce).
    private class Cached(val result: SabrAudioResult, val atMs: Long)
    private val resolveCache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private const val CACHE_TTL_MS = 45L * 60 * 1000
    private const val CACHE_MAX = 8

    /** Drop [videoId]'s cached resolution (a playback error invalidates — DIRECT's cache discipline). */
    fun invalidate(videoId: String) {
        resolveCache.remove(videoId)
    }

    // ---- stall fallback: a client whose session drained INCOMPLETE for a video is skipped on the next
    // resolve of that video, so a replay advances the roster instead of truncating identically forever.
    // (A /player that succeeds gives the roster loop no failure signal — this is that signal.)
    private val stalledClients = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

    /** Record an incomplete drain of [videoId] over [clientKey] (fed by the sessions' onIncomplete). */
    fun recordStall(videoId: String, clientKey: String) {
        Timber.tag(TAG).w("SABR client $clientKey stalled on $videoId — deprioritized for this id")
        stalledClients.getOrPut(videoId) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }.add(clientKey)
        invalidate(videoId)
    }

    /** The clients recorded as stalled for [videoId] (consulted by both resolvers' roster ordering). */
    fun stalledFor(videoId: String): Set<String> = stalledClients[videoId].orEmpty()

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
        if (register) {
            resolveCache[videoId]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.atMs < CACHE_TTL_MS }?.let {
                SabrStreamRegistry.put(videoId, it.result.config)
                Timber.tag(TAG).d("SABR resolve cache hit for $videoId")
                return@withContext it.result
            }
        }
        val visitorData = YouTube.visitorData ?: return@withContext null
        val pot = poTokenGenerator.getWebClientPoToken(videoId, visitorData) ?: return@withContext null
        // STS must come from the cipher player - the single sts/decipher source (a sig minted for one
        // player generation but deciphered by another 403s on the CDN). Null sts degrades the /player
        // response, never playback of clients that need no signature.
        val sts = try {
            CipherDeobfuscator.signatureTimestamp()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher player STS fetch FAILED")
            null
        }
        val stalled = stalledClients[videoId].orEmpty()
        // Two passes: prefer non-stalled clients; a fully-stalled roster still retries them (last hope).
        val order = ROSTER.filter { it.key in enabled && it.key !in stalled } +
            ROSTER.filter { it.key in enabled && it.key in stalled }
        for (spec in order) {
            val result = tryClient(spec, videoId, pot, sts, audioQuality, meteredNetwork, cpn)
            if (result != null) {
                if (register) {
                    SabrStreamRegistry.put(videoId, result.config)
                    if (resolveCache.size >= CACHE_MAX) resolveCache.entries.minByOrNull { it.value.atMs }?.let { resolveCache.remove(it.key) }
                    resolveCache[videoId] = Cached(result, android.os.SystemClock.elapsedRealtime())
                }
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
                clientKey = spec.key,
                // approxDurationMs: the byte<->time estimate the stream's seek-restart needs.
                durationMs = fmt.approxDurationMs?.toLongOrNull() ?: 0L,
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
