package com.jtech.zemer.utils

import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.StreamSourceAndroidVRKey
import com.jtech.zemer.constants.StreamSourceIOSKey
import com.jtech.zemer.constants.StreamSourceIPadOSKey
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import kotlinx.coroutines.flow.first

import timber.log.Timber
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import com.zemer.cipher.potoken.PoTokenResult
import com.jtech.zemer.utils.sabr.EjsNTransformSolver
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    // Track videoIds where WEB_REMIX stream URLs 403 on ExoPlayer GET, so the next
    // resolution falls through to TVHTML5/ANDROID_VR instead of looping.
    private val webRemixFailedIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    fun markWebRemixFailed(videoId: String) {
        webRemixFailedIds.add(videoId)
    }

    /**
     * Cleared when the cipher recovers (player config refreshed after a stream rejection): the
     * prior WEB_REMIX failures were caused by the stale cipher, so let resolution try WEB_REMIX
     * again instead of staying pinned to a lower fallback client for the rest of the process.
     */
    fun clearWebRemixFailures() {
        webRemixFailedIds.clear()
    }

    // Fire-and-forget scope for the cipher config self-heal triggered when a cipher client fails
    // stream validation during resolution. Only WEB_REMIX skips HEAD validation (so its bad URL
    // 403s on ExoPlayer and hits MusicService's handler); WEB_CREATOR / TVHTML5 / WEB are validated
    // here and never reach ExoPlayer, so without this trigger a WEB_REMIX-disabled user would never
    // self-heal a stale/wrong cipher config. Kept off the resolution coroutine so the (network)
    // refresh never blocks falling through to the next client.
    private val cipherRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Client names disabled by the user in Settings → Stream sources. Updated by MusicService. */
    var disabledStreamClients: Set<String> = emptySet()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val ALL_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        // VISIONOS first: its CDN URL has no `spc` gate, so it streams the whole song with no
        // poToken and no cipher (HEAD 200) — the most reliable fallback, ahead of TVHTML5 and the
        // ANDROID_VR variants. IOS/IPADOS below ARE spc-gated and 403 past the 1 MiB free window
        // (the web poToken can't satisfy iOS attestation) — verified via tests/re-apple.mjs — so
        // they stay only as last-ditch attempts.
        VISIONOS,
        WEB_CREATOR,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        TVHTML5,
        IOS,
        IPADOS,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB
    )

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient>
        get() = ALL_FALLBACK_CLIENTS.filter { it.clientName !in disabledStreamClients }.toTypedArray()

    // A stable video id used only to warm the local BotGuard token generator; the token is
    // discarded. PoToken generation is a local WebView computation (no YouTube /player call), so
    // this triggers no network request to YouTube for the video itself.
    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    /**
     * Best-effort warm-up of the cipher WebView so the first real playback doesn't pay its
     * cold-start cost (fetch + load of the ~2.8 MB player JS). Needs no session, so callers can run
     * it immediately at startup. Safe to call any time; failures are swallowed and playback falls
     * back to the existing lazy-init path unchanged.
     */
    suspend fun prewarmCipher() {
        runCatching { CipherDeobfuscator.prewarm() }
            .onFailure { Timber.tag(TAG).w(it, "Cipher prewarm skipped: ${it.message}") }
    }

    /**
     * Best-effort warm-up of the PoToken/BotGuard generator (~2–5s cold) so the first real playback
     * doesn't pay it. Requires a session — callers must ensure [YouTube.visitorData] is populated
     * first; it's a no-op otherwise. Safe to call any time; failures are swallowed and playback
     * falls back to the existing lazy-init path unchanged.
     */
    suspend fun prewarmPoToken() {
        val sessionId = YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, sessionId)
                }
            }.onFailure { Timber.tag(TAG).w(it, "PoToken prewarm skipped: ${it.message}") }
        }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from the selected main client.
     * Format & stream can be from main client or fallback clients.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean = false,
        maxVideoBitrateKbps: Int? = null,
        forDownload: Boolean = false,
    ): Result<PlaybackData> = runCatching {
        val mainClient = if (MAIN_CLIENT.clientName in disabledStreamClients) {
            STREAM_FALLBACK_CLIENTS.firstOrNull()
                ?: throw PlaybackException("All stream sources are disabled", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        } else {
            MAIN_CLIENT
        }
        val fallbackClients = STREAM_FALLBACK_CLIENTS.filter { it.clientName != mainClient.clientName }.toTypedArray()

        Timber.tag(TAG).d( "=== Stream resolution START for videoId=$videoId ===")
        Timber.tag(TAG).d( "Main client: ${mainClient.clientName}, audioQuality=$audioQuality, preferVideo=$preferVideo")

        val defaultStreamTtlSeconds = 6 * 60 * 60 // 6 hours
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(TAG).d( "Signature timestamp: ${signatureTimestamp ?: "FAILED/null"}")

        // Enhanced authentication validation with SAPISID check
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)
        Timber.tag(TAG).d( "Auth: isLoggedIn=$isLoggedIn, dataSyncId=${YouTube.dataSyncId?.take(20)}, visitorData=${YouTube.visitorData?.take(20)}")

        // PoToken session must always be visitorData — dataSyncId is an account identifier
        // and is rejected by YouTube's BotGuard attestation when used as the session context.
        val sessionId = YouTube.visitorData
        Timber.tag(TAG).d( "Using sessionId: ${sessionId?.take(20)}... (visitorData)")

        // Generate PoToken for web clients
        val poTokenResult: PoTokenResult? = try {
            if (sessionId == null) {
                Timber.tag(TAG).d( "PoToken SKIPPED: sessionId is null")
                null
            } else {
                poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e( "PoToken generation EXCEPTION", e)
            null
        }
        Timber.tag(TAG).d( "PoToken: ${if (poTokenResult != null) "generated" else "unavailable"}")

        Timber.tag(TAG).d( "Fetching main player response with client: ${mainClient.clientName}")
        // Resilient: the chosen main client can fail outright (e.g. ANDROID_CREATOR returns
        // HTTP 400 with login). Use getOrNull, not getOrThrow, so one bad client never kills the
        // whole resolution — the stream loop below falls through to the next enabled client, and
        // metadata is captured from the first client that returns OK.
        val mainPlayerResponse =
            YouTube.player(
                videoId, playlistId, mainClient, signatureTimestamp,
                webPlayerPot = if (mainClient.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
            ).getOrNull()
        Timber.tag(TAG).d( "Main response status: ${mainPlayerResponse?.playabilityStatus?.status ?: "request failed"}")
        var audioConfig = mainPlayerResponse?.playerConfig?.audioConfig
        var videoDetails = mainPlayerResponse?.videoDetails
        var playbackTracking = mainPlayerResponse?.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var successClient: String? = null

        for (clientIndex in (-1 until fallbackClients.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first
                client = mainClient
                streamPlayerResponse = mainPlayerResponse
                Timber.tag(TAG).d( "--- Trying streams from main client: ${client.clientName} ---")
            } else {
                // after main client use fallback clients
                client = fallbackClients[clientIndex]
                Timber.tag(TAG).d( "--- Trying fallback client ${clientIndex + 1}/${fallbackClients.size}: ${client.clientName} ---")

                if (client.loginRequired && !isLoggedIn) {
                    Timber.tag(TAG).d( "Skipping ${client.clientName} - requires login but not authenticated")
                    continue
                }

                streamPlayerResponse =
                    YouTube.player(
                        videoId, playlistId, client, signatureTimestamp,
                        webPlayerPot = if (client.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
                    ).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(TAG).d( "Status OK for ${client.clientName}")

                // Capture metadata from the first OK client (main may have failed/returned null above).
                if (audioConfig == null) audioConfig = streamPlayerResponse?.playerConfig?.audioConfig
                if (videoDetails == null) videoDetails = streamPlayerResponse?.videoDetails
                if (playbackTracking == null) playbackTracking = streamPlayerResponse?.playbackTracking

                // Use the player response as-is. The old NewPipe StreamInfo.getInfo
                // pre-processing ran a full second extraction for EVERY song (fetch watch
                // page + decipher all ~18 formats) — slow with the bundled extractor and
                // redundant. Direct-url clients (IOS/ANDROID_VR/IPADOS/VISIONOS) already
                // carry playable URLs; web clients are deciphered per-format by the Zemer
                // cipher in findUrlOrNull (sig) + transformNParamInUrl (n) below.
                val responseToUse = streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        maxVideoBitrateKbps,
                        forDownload,
                    )

                if (format == null) {
                    Timber.tag(TAG).d( "No suitable format found for ${client.clientName}")
                    continue
                }
                Timber.tag(TAG).d( "Format: itag=${format.itag}, mime=${format.mimeType}, bitrate=${format.bitrate}, sampleRate=${format.audioSampleRate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse)
                if (streamUrl == null) {
                    Timber.tag(TAG).d( "No stream URL for format on ${client.clientName}")
                    continue
                }
                Timber.tag(TAG).d( "Stream URL (${client.clientName}): ${streamUrl.take(80)}...")

                // Apply n-transform and PoToken for web clients (n-transform FIRST, then pot=)
                val needsNTransform = client.useWebPoTokens ||
                    client.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")

                if (needsNTransform) {
                    try {
                        Timber.tag(TAG).d("Applying n-transform to stream URL for ${client.clientName}")

                        // Try CipherDeobfuscator first (uses Pattern 5), fallback to EjsNTransformSolver
                        val originalUrl = streamUrl
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl)

                        if (streamUrl == originalUrl) {
                            // CipherDeobfuscator didn't transform, try EjsNTransformSolver as fallback
                            Timber.tag(TAG).d("CipherDeobfuscator returned same URL, trying EjsNTransformSolver...")
                            streamUrl = EjsNTransformSolver.transformNParamInUrl(originalUrl)
                        }

                        // Append pot= parameter AFTER n-transform (URI-encode to handle base64 padding)
                        if (client.useWebPoTokens && poTokenResult?.streamingDataPoToken != null) {
                            Timber.tag(TAG).d("Appending pot= parameter to stream URL")
                            val separator = if ("?" in streamUrl) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${android.net.Uri.encode(poTokenResult.streamingDataPoToken)}"
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "N-transform or pot append failed: ${e.message}")
                        // Continue with original URL
                    }
                }

                streamExpiresInSeconds =
                    streamPlayerResponse.streamingData?.expiresInSeconds
                        ?: streamUrl.let(::deriveExpireSecondsFromUrl)
                        ?: defaultStreamTtlSeconds

                Timber.tag(TAG).d( "Expires in: ${streamExpiresInSeconds}s")

                if (streamExpiresInSeconds <= 0) {
                    Timber.tag(TAG).d( "Stream already expired, skipping")
                    continue
                }

                if (clientIndex == fallbackClients.size - 1) {
                    Timber.tag(TAG).d( "Last fallback — skipping validation: ${client.clientName}")
                    successClient = client.clientName
                    break
                }

                // WEB_REMIX authenticated CDN URLs 403 on HEAD but serve correctly
                // on the actual byte-range GET that ExoPlayer makes. Skip HEAD validation
                // for streaming UNLESS this videoId already failed on GET (tracked in
                // webRemixFailedIds), in which case fall through to TVHTML5/ANDROID_VR.
                // For downloads, always fall through — WEB_REMIX signed URLs don't support
                // the &range= query-param download pattern.
                if (client.clientName == "WEB_REMIX" && clientIndex == -1
                    && !forDownload && !webRemixFailedIds.contains(videoId)) {
                    Timber.tag(TAG).d("WEB_REMIX — skipping HEAD validation, letting ExoPlayer try directly")
                    successClient = client.clientName
                    break
                }

                val validationResult = validateStatus(streamUrl)
                if (validationResult) {
                    Timber.tag(TAG).d( "Stream VALIDATED OK with ${client.clientName}")
                    successClient = client.clientName
                    break
                } else {
                    Timber.tag(TAG).d( "Stream validation FAILED for ${client.clientName}")
                    // A cipher client failing validation can mean a wrong-but-non-throwing signature
                    // from a stale/wrong player config — caught here at resolution, so it never
                    // reaches ExoPlayer and MusicService's 403 handler never fires. Ask the cipher to
                    // re-fetch its config (rate-limited, off this coroutine); if it changes, the
                    // cipher rebuilds its WebView and the next resolution returns to this client — no
                    // app restart. This is what covers WEB_CREATOR/TVHTML5/WEB-only users.
                    if (needsNTransform) {
                        cipherRefreshScope.launch {
                            if (CipherDeobfuscator.onStreamRejected()) clearWebRemixFailures()
                        }
                    }
                }
            } else {
                Timber.tag(TAG).d( "Status NOT OK for ${client.clientName}: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(TAG).e( "All clients failed for $videoId")
            throw PlaybackException(
                "All clients failed for $videoId",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(TAG).e( "Playability not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (format == null) {
            Timber.tag(TAG).e( "No playable format found for $videoId")
            throw PlaybackException(
                "No playable format found",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamUrl == null) {
            Timber.tag(TAG).e( "No stream URL found for $videoId")
            throw PlaybackException(
                "No stream URL found",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(TAG).e( "Stream expired for $videoId")
            throw PlaybackException(
                "Stream expired",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        Timber.tag(TAG).d( "=== Stream resolution SUCCESS: client=${streamPlayerResponse.let { "OK" }}, itag=${format.itag}, expires=${streamExpiresInSeconds}s ===")
        // Log.i survives release builds (Timber is stripped)
        android.util.Log.i(TAG, "Playback: client=${successClient ?: "unknown"}, itag=${format.itag}, videoId=$videoId")

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            streamClient = successClient ?: "unknown",
        )
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean,
        maxVideoBitrateKbps: Int?,
        forDownload: Boolean = false,
    ): PlayerResponse.StreamingData.Format? {
        if (preferVideo) {
            val progressive = playerResponse.streamingData?.formats.orEmpty()
                .filter { it.mimeType.startsWith("video") && (it.audioQuality != null || it.audioChannels != null) }
            val progressiveMp4 = progressive.filter { it.mimeType.contains("mp4") }
            val ordered = (progressiveMp4.ifEmpty { progressive }).sortedBy { it.bitrate }
            val capped = maxVideoBitrateKbps?.let { cap ->
                ordered.filter { (it.bitrate / 1000) <= cap }
            }.orEmpty()
            val chosen = when {
                capped.isNotEmpty() -> capped.maxByOrNull { it.bitrate }
                else -> ordered.maxByOrNull { it.bitrate }
            }
            if (chosen != null) {
                return chosen
            }
            return null
        }

        // For downloads: exclude webm (MediaStore doesn't support it)
        // For streaming: prefer opus (webm) for better quality
        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.let { formats ->
                if (forDownload) {
                    // Exclude webm for downloads - MediaStore only supports mp4/m4a
                    formats.filter { !it.mimeType.startsWith("audio/webm") }.ifEmpty { formats }
                } else {
                    formats
                }
            }

        val audioFormat = audioFormats?.maxByOrNull {
            it.bitrate * when (audioQuality) {
                AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                AudioQuality.HIGH -> 1
                AudioQuality.LOW -> -1
            } + (if (!forDownload && it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus for streaming only
        }

        return audioFormat
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        try {
            val validatedUrl = UrlValidator.validateAndParseUrl(url)
                ?: return false.also {
                    Timber.tag(TAG).e( "Invalid stream URL for validation: $url")
                    reportException(Exception("Invalid stream URL: $url"))
                }

            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(validatedUrl)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            YouTube.cookie?.let { requestBuilder.addHeader("Cookie", it) }
            val request = try {
                requestBuilder.build()
            } catch (e: Exception) {
                Timber.tag(TAG).e( "Failed to build validation request", e)
                reportException(Exception("Failed to build request for URL: $url", e))
                return false
            }
            val response = httpClient.newCall(request).execute()
            val code = response.code
            val isSuccessful = response.isSuccessful
            Timber.tag(TAG).d( "Validation HTTP $code (success=$isSuccessful)")
            return isSuccessful
        } catch (e: Exception) {
            Timber.tag(TAG).e( "Validation exception", e)
            reportException(e)
        }
        return false
    }
    /**
     * STS for the /player request. It must come from the same player generation the Zemer
     * cipher deciphers with: during A/B rollouts NewPipe's independently fetched player can
     * be a different one, and a sig minted for one player deciphered by another 403s on the
     * CDN (observed 2026-06-09: NewPipe sts=20611/69e2a55d vs cipher player ce74690f).
     * NewPipe is only the fallback for when the cipher player fetch fails entirely.
     */
    private suspend fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        val cipherSts = try {
            CipherDeobfuscator.signatureTimestamp()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Cipher player STS fetch FAILED", e)
            null
        }
        if (cipherSts != null) {
            Timber.tag(TAG).d("Signature timestamp from cipher player: $cipherSts")
            return cipherSts
        }
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(TAG).d( "Signature timestamp fetched via NewPipe: $it") }
            .onFailure {
                Timber.tag(TAG).e( "Signature timestamp fetch FAILED", it)
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Resolves a playable stream URL from the format.
     * If the response was pre-processed by NewPipe, uses the direct URL.
     * Otherwise tries custom cipher deobfuscation, then NewPipe extractor as fallback.
     */
    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        response: PlayerResponse
    ): String? {
        Timber.tag(TAG).d( "findUrlOrNull: signatureCipher=${format.signatureCipher != null}, directUrl=${format.url != null}")

        var url: String? = null

        // If format already has a direct URL (from NewPipe pre-processing), use it
        if (format.url != null) {
            Timber.tag(TAG).d( "Using URL from format directly (NewPipe pre-processed)")
            url = format.url
        }

        // Try custom cipher deobfuscation (for signatureCipher URLs without direct URL)
        if (url == null && format.signatureCipher != null) {
            try {
                val deobfuscated = CipherDeobfuscator.deobfuscateStreamUrl(format.signatureCipher!!, videoId)
                if (deobfuscated != null) {
                    Timber.tag(TAG).d( "Custom cipher deobfuscation succeeded for $videoId")
                    url = deobfuscated
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e( "Custom cipher deobfuscation FAILED for $videoId", e)
            }
        }

        // If custom cipher failed, try NewPipe extractor as fallback
        if (url == null) {
            val extractorUrl = NewPipeUtils.getStreamUrl(format, videoId)
                .onSuccess { Timber.tag(TAG).d( "NewPipe extractor succeeded for $videoId") }
                .onFailure {
                    Timber.tag(TAG).e( "NewPipe extractor FAILED for $videoId", it)
                }
                .getOrNull()
            if (extractorUrl != null) {
                url = extractorUrl
            }
        }

        if (url == null) return null

        return if (UrlValidator.isValidUrl(url)) {
            url
        } else {
            Timber.tag(TAG).e( "Stream URL validation failed: $url")
            reportException(Exception("Stream URL validation failed: $url"))
            null
        }
    }
}

private fun deriveExpireSecondsFromUrl(streamUrl: String): Int? {
    val uri = streamUrl.toUri()
    val expireEpoch = uri.getQueryParameter("expire")?.toLongOrNull()
        ?: uri.getQueryParameter("exp")?.toLongOrNull()
    return expireEpoch?.let { epoch ->
        val remainingMillis = epoch * 1000L - System.currentTimeMillis()
        if (remainingMillis > 0) (remainingMillis / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null
    }
}
