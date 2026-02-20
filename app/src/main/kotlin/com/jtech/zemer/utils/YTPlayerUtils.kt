package com.jtech.zemer.utils

import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.constants.PreferredClient

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
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.utils.parseCookieString
import okhttp3.OkHttpClient

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5,
        ANDROID_VR_1_43_32,
        IOS,
        IPADOS,
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB,
        WEB_CREATOR
    )

    // Video-specific fallback (excludes TVHTML5 since it was already tried for adaptive 1080p+)
    private val VIDEO_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_43_32,
        IOS,
        IPADOS,
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB,
        WEB_CREATOR
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Data for adaptive video playback with separate video and audio streams.
     * Used with ExoPlayer's MergingMediaSource for 1080p+ playback.
     */
    data class AdaptiveVideoData(
        val videoDetails: PlayerResponse.VideoDetails?,
        val videoUrl: String,
        val audioUrl: String,
        val videoFormat: PlayerResponse.StreamingData.Format,
        val audioFormat: PlayerResponse.StreamingData.Format,
        val expiresInSeconds: Int,
        val availableQualities: List<VideoQualityInfo>,
    )

    data class VideoQualityInfo(
        val height: Int,
        val width: Int?,
        val fps: Int?,
        val bitrate: Int,
        val label: String,
        val format: PlayerResponse.StreamingData.Format,
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
        targetBitrateKbps: Int = 0, // 0 = use audioQuality, >0 = target specific bitrate
        targetItag: Int = 0, // 0 = auto, >0 = exact itag (overrides all other quality settings)
        preferredClient: PreferredClient = PreferredClient.AUTO,
        isVideoFallback: Boolean = false, // Set true for video 720p fallback (skips TVHTML5)
    ): Result<PlaybackData> = runCatching {
        // Select client based on preference
        // For video fallback, use VIDEO_FALLBACK_CLIENTS which excludes TVHTML5
        val (mainClient, fallbackClients) = when (preferredClient) {
            PreferredClient.AUTO -> MAIN_CLIENT to (if (isVideoFallback) VIDEO_FALLBACK_CLIENTS else STREAM_FALLBACK_CLIENTS)
            PreferredClient.WEB_REMIX -> WEB_REMIX to arrayOf(TVHTML5, ANDROID_VR_1_43_32)
            PreferredClient.TVHTML5 -> TVHTML5 to arrayOf(WEB_REMIX, ANDROID_VR_1_43_32)
            PreferredClient.ANDROID_VR -> ANDROID_VR_1_43_32 to arrayOf(ANDROID_VR_1_61_48, ANDROID_VR_NO_AUTH)
        }

        Timber.tag(TAG).d( "=== Stream resolution START for videoId=$videoId ===")
        Timber.tag(TAG).d( "Main client: ${mainClient.clientName}, preferredClient=$preferredClient, audioQuality=$audioQuality, targetBitrate=${if (targetBitrateKbps > 0) "${targetBitrateKbps}kbps" else "auto"}, preferVideo=$preferVideo")

        val defaultStreamTtlSeconds = 6 * 60 * 60 // 6 hours
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(TAG).d( "Signature timestamp: ${signatureTimestamp ?: "FAILED/null"}")

        // Enhanced authentication validation with SAPISID check
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)
        Timber.tag(TAG).d( "Auth: isLoggedIn=$isLoggedIn, dataSyncId=${YouTube.dataSyncId?.take(20)}, visitorData=${YouTube.visitorData?.take(20)}")

        val sessionId = if (isLoggedIn) {
            YouTube.dataSyncId ?: YouTube.visitorData
        } else {
            YouTube.visitorData
        }
        Timber.tag(TAG).d( "Using sessionId: ${sessionId?.take(20)}... (from ${if (YouTube.dataSyncId != null) "dataSyncId" else "visitorData"})")

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
        val mainPlayerResponse =
            YouTube.player(
                videoId, playlistId, mainClient, signatureTimestamp,
                webPlayerPot = if (mainClient.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
            ).getOrThrow()
        Timber.tag(TAG).d( "Main response status: ${mainPlayerResponse.playabilityStatus.status}")
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
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

                format =
                    findFormat(
                        streamPlayerResponse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        maxVideoBitrateKbps,
                        forDownload,
                        targetBitrateKbps,
                        targetItag,
                    )

                if (format == null) {
                    Timber.tag(TAG).d( "No suitable format found for ${client.clientName}")
                    continue
                }
                Timber.tag(TAG).d( "Format: itag=${format.itag}, mime=${format.mimeType}, bitrate=${format.bitrate}, sampleRate=${format.audioSampleRate}")

                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl == null) {
                    Timber.tag(TAG).d( "No stream URL for format on ${client.clientName}")
                    continue
                }
                Timber.tag(TAG).d( "Stream URL (${client.clientName}): ${streamUrl.take(80)}...")

                // Append streaming PoToken before validation (for web clients)
                if (client.useWebPoTokens && poTokenResult?.streamingDataPoToken != null) {
                    val separator = if ("?" in streamUrl) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poTokenResult.streamingDataPoToken}"
                    Timber.tag(TAG).d( "Appended streaming PoToken to URL")
                }

                // Apply n-transform proactively for web clients (avoids 403 round-trip)
                if (client.useWebPoTokens) {
                    Timber.tag(TAG).d("Attempting proactive n-transform...")
                    try {
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl)
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(TAG).d("Proactive n-transform applied successfully")
                        } else {
                            Timber.tag(TAG).d("Proactive n-transform returned same URL (no change)")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Proactive n-transform failed, will retry if needed: ${e.message}")
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

                // Skip validation for: last fallback client, or WEB_REMIX with anon login
                // (anon login HEAD requests return 403 but actual playback works)
                val skipValidation = when {
                    clientIndex == fallbackClients.size - 1 -> {
                        Timber.tag(TAG).d("Last fallback — skipping validation: ${client.clientName}")
                        true
                    }
                    client.clientName == "WEB_REMIX" && YouTube.isAnonLogin -> {
                        Timber.tag(TAG).d("WEB_REMIX + anon login — skipping validation (HEAD returns 403 but playback works)")
                        true
                    }
                    else -> false
                }

                if (skipValidation) {
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

                    // For web clients: try n-parameter transform and re-validate
                    if (client.useWebPoTokens) {
                        var nTransformWorked = false

                        // Try CipherDeobfuscator n-transform first
                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl)
                            if (nTransformed != streamUrl) {
                                Timber.tag(TAG).d( "CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(nTransformed)) {
                                    Timber.tag(TAG).d( "N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    successClient = client.clientName
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e( "CipherDeobfuscator n-transform error", e)
                        }

                        // If CipherDeobfuscator failed, try EjsNTransformSolver
                        if (!nTransformWorked) {
                            try {
                                val ejsTransformed = EjsNTransformSolver.transformNParamInUrl(streamUrl)
                                if (ejsTransformed != streamUrl) {
                                    Timber.tag(TAG).d( "EJS n-transform applied, re-validating...")
                                    if (validateStatus(ejsTransformed)) {
                                        Timber.tag(TAG).d( "EJS n-transformed URL VALIDATED OK!")
                                        streamUrl = ejsTransformed
                                        nTransformWorked = true
                                        successClient = client.clientName
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.tag(TAG).e( "EJS n-transform error", e)
                            }
                        }

                        if (nTransformWorked) break
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
        )
    }

    /**
     * Get adaptive video playback data with separate video and audio URLs.
     * This enables 1080p+ playback using ExoPlayer's MergingMediaSource.
     * Uses TVHTML5 client only - if it fails, VideoPlayerScreen falls back to progressive (720p).
     *
     * @param videoId The YouTube video ID
     * @param targetHeight Target video height (e.g., 1080, 720). If null, picks highest available.
     * @return AdaptiveVideoData with separate video/audio URLs, or failure if not available
     */
    suspend fun getAdaptiveVideoData(
        videoId: String,
        targetHeight: Int? = null,
        preferredClient: PreferredClient = PreferredClient.AUTO,
        preferMp4: Boolean = false, // Set true for downloads - MediaMuxer only supports H.264/MP4
    ): Result<AdaptiveVideoData> = runCatching {
        // Use TVHTML5 for adaptive video - doesn't require n-transform
        // If this fails, VideoPlayerScreen will fallback to progressive playback (720p max)
        val client = TVHTML5

        Timber.tag(TAG).d("=== Adaptive video START for videoId=$videoId, targetHeight=$targetHeight, preferMp4=$preferMp4 ===")

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        // Auth and PoToken setup
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)
        val sessionId = if (isLoggedIn) YouTube.dataSyncId ?: YouTube.visitorData else YouTube.visitorData

        val poTokenResult: PoTokenResult? = try {
            if (sessionId == null || !client.useWebPoTokens) null
            else poTokenGenerator.getWebClientPoToken(videoId, sessionId)
        } catch (e: Exception) {
            Timber.tag(TAG).e("PoToken generation failed", e)
            null
        }

        // Fetch player response
        val playerResponse = YouTube.player(
            videoId, null, client, signatureTimestamp,
            webPlayerPot = if (client.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
        ).getOrThrow()

        if (playerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(
                playerResponse.playabilityStatus.reason ?: "Playback not available",
                null, PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats
            ?: throw PlaybackException("No adaptive formats", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)

        // Get video formats (video-only, sorted by height descending)
        val videoFormats = adaptiveFormats
            .filter { !it.isAudio && (it.height ?: 0) > 0 }
            .sortedByDescending { it.height ?: 0 }

        if (videoFormats.isEmpty()) {
            throw PlaybackException("No video formats available", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        }

        // Build available qualities list (show all for selection)
        val availableQualities = videoFormats
            .distinctBy { it.height }
            .mapNotNull { format ->
                val height = format.height ?: return@mapNotNull null
                VideoQualityInfo(
                    height = height,
                    width = format.width,
                    fps = format.fps,
                    bitrate = format.bitrate,
                    label = buildString {
                        append("${height}p")
                        format.fps?.takeIf { it > 30 }?.let { append(" ${it}fps") }
                    },
                    format = format
                )
            }

        // When preferMp4 is true (for downloads), only use MP4 formats (MediaMuxer doesn't support WebM/VP9)
        val filteredFormats = if (preferMp4) {
            videoFormats.filter { it.mimeType.contains("mp4") }.ifEmpty { videoFormats }
        } else {
            videoFormats
        }

        val selectedVideoFormat = if (targetHeight != null) {
            // Find exact match or closest lower
            filteredFormats.find { it.height == targetHeight }
                ?: filteredFormats.filter { (it.height ?: 0) <= targetHeight }.maxByOrNull { it.height ?: 0 }
                ?: filteredFormats.first()
        } else {
            // Pick highest quality, prefer MP4 over WebM
            val mp4Formats = filteredFormats.filter { it.mimeType.contains("mp4") }
            (mp4Formats.ifEmpty { filteredFormats }).first()
        }

        Timber.tag(TAG).d("Selected video: ${selectedVideoFormat.height}p, itag=${selectedVideoFormat.itag}, mime=${selectedVideoFormat.mimeType}")

        // Get best audio format (prefer AAC/M4A for compatibility)
        val audioFormat = adaptiveFormats
            .filter { it.isAudio && it.isOriginal }
            .let { audioFormats ->
                // Prefer MP4/AAC for compatibility
                audioFormats.filter { it.mimeType.contains("mp4") }.maxByOrNull { it.bitrate }
                    ?: audioFormats.maxByOrNull { it.bitrate }
            }
            ?: throw PlaybackException("No audio format available", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)

        Timber.tag(TAG).d("Selected audio: itag=${audioFormat.itag}, mime=${audioFormat.mimeType}, bitrate=${audioFormat.bitrate}")

        // Resolve URLs
        var videoUrl = findUrlOrNull(selectedVideoFormat, videoId)
            ?: throw PlaybackException("Cannot resolve video URL", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        var audioUrl = findUrlOrNull(audioFormat, videoId)
            ?: throw PlaybackException("Cannot resolve audio URL", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)

        // Append PoToken if needed
        if (client.useWebPoTokens && poTokenResult?.streamingDataPoToken != null) {
            val pot = poTokenResult.streamingDataPoToken
            videoUrl = appendQueryParam(videoUrl, "pot", pot)
            audioUrl = appendQueryParam(audioUrl, "pot", pot)
            Timber.tag(TAG).d("Appended PoToken to URLs")
        }

        // Apply n-transform proactively
        if (client.useWebPoTokens) {
            try {
                videoUrl = EjsNTransformSolver.transformNParamInUrl(videoUrl)
                audioUrl = EjsNTransformSolver.transformNParamInUrl(audioUrl)
                Timber.tag(TAG).d("N-transform applied to URLs")
            } catch (e: Exception) {
                Timber.tag(TAG).w("N-transform failed: ${e.message}")
            }
        }

        // Only validate for web clients (non-web clients like TVHTML5 don't need n-transform)
        if (client.useWebPoTokens) {
            if (!validateStatus(videoUrl)) {
                Timber.tag(TAG).w("Video URL validation failed, trying CipherDeobfuscator n-transform...")
                try {
                    val transformed = CipherDeobfuscator.transformNParamInUrl(videoUrl)
                    if (transformed != videoUrl && validateStatus(transformed)) {
                        videoUrl = transformed
                        Timber.tag(TAG).d("Video URL fixed with CipherDeobfuscator n-transform")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e("CipherDeobfuscator n-transform failed for video: ${e.message}")
                }
            }

            if (!validateStatus(audioUrl)) {
                Timber.tag(TAG).w("Audio URL validation failed, trying CipherDeobfuscator n-transform...")
                try {
                    val transformed = CipherDeobfuscator.transformNParamInUrl(audioUrl)
                    if (transformed != audioUrl && validateStatus(transformed)) {
                        audioUrl = transformed
                        Timber.tag(TAG).d("Audio URL fixed with CipherDeobfuscator n-transform")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e("CipherDeobfuscator n-transform failed for audio: ${e.message}")
                }
            }

            Timber.tag(TAG).d("Video URL validated: ${validateStatus(videoUrl)}")
            Timber.tag(TAG).d("Audio URL validated: ${validateStatus(audioUrl)}")
        } else {
            Timber.tag(TAG).d("Skipping URL validation for non-web client: ${client.clientName}")
        }

        val expiresInSeconds = playerResponse.streamingData?.expiresInSeconds
            ?: deriveExpireSecondsFromUrl(videoUrl)
            ?: (6 * 60 * 60)

        Timber.tag(TAG).d("=== Adaptive video SUCCESS: ${selectedVideoFormat.height}p, expires=${expiresInSeconds}s ===")

        AdaptiveVideoData(
            videoDetails = playerResponse.videoDetails,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            videoFormat = selectedVideoFormat,
            audioFormat = audioFormat,
            expiresInSeconds = expiresInSeconds,
            availableQualities = availableQualities,
        )
    }

    private fun appendQueryParam(url: String, key: String, value: String): String {
        val separator = if ("?" in url) "&" else "?"
        return "$url$separator$key=$value"
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

    /**
     * Data class representing an available audio format option.
     */
    data class AudioFormatOption(
        val itag: Int,
        val bitrate: Int,
        val bitrateKbps: Int,
        val mimeType: String,
        val codec: String,
    ) {
        val displayName: String
            get() = "${bitrateKbps}kbps ${codec.uppercase()}"
    }

    /**
     * Fetches available audio formats for a video.
     * Returns a list of AudioFormatOption sorted by bitrate (highest first).
     * Uses proper authentication (signatureTimestamp, poToken) to get streaming data.
     */
    suspend fun getAvailableAudioFormats(
        videoId: String,
        preferredClient: PreferredClient = PreferredClient.AUTO
    ): Result<List<AudioFormatOption>> = runCatching {
        // Select client based on preference
        val client = when (preferredClient) {
            PreferredClient.AUTO -> WEB_REMIX
            PreferredClient.WEB_REMIX -> WEB_REMIX
            PreferredClient.TVHTML5 -> TVHTML5
            PreferredClient.ANDROID_VR -> ANDROID_VR_1_43_32
        }

        Timber.tag(TAG).d("Fetching available formats for $videoId using ${client.clientName}")

        // Get signature timestamp (required for streaming data)
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        // Check login status
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)

        // Get session ID for PoToken
        val sessionId = if (isLoggedIn) {
            YouTube.dataSyncId ?: YouTube.visitorData
        } else {
            YouTube.visitorData
        }

        // Generate PoToken for web clients
        val poTokenResult: PoTokenResult? = if (client.useWebPoTokens) {
            try {
                if (sessionId == null) {
                    Timber.tag(TAG).d("PoToken SKIPPED for formats: sessionId is null")
                    null
                } else {
                    poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e("PoToken generation EXCEPTION for formats", e)
                null
            }
        } else null

        // Make authenticated player request
        val response = YouTube.player(
            videoId = videoId,
            client = client,
            signatureTimestamp = signatureTimestamp,
            webPlayerPot = if (client.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
        ).getOrThrow()

        // Only include formats that have a URL (direct or via signatureCipher)
        // This filters out unusable formats without restricting by login type
        val formats = response.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal && (it.url != null || it.signatureCipher != null) }
            ?.map { format ->
                val codec = when {
                    format.mimeType.contains("opus") -> "opus"
                    format.mimeType.contains("mp4a") -> "m4a"
                    else -> format.mimeType.substringAfter("audio/").substringBefore(";")
                }
                AudioFormatOption(
                    itag = format.itag,
                    bitrate = format.bitrate,
                    bitrateKbps = format.bitrate / 1000,
                    mimeType = format.mimeType,
                    codec = codec,
                )
            }
            ?.sortedByDescending { it.bitrate }
            ?: emptyList()

        Timber.tag(TAG).d("Found ${formats.size} audio formats for $videoId")
        formats.forEach { Timber.tag(TAG).d("  Format: ${it.displayName} (itag=${it.itag})") }
        formats
    }

    /**
     * Fetches available audio formats from ALL clients for download selection.
     * Returns all formats (including opus/webm) sorted by bitrate (highest first).
     * Shows all format options so user can choose their preferred codec.
     */
    suspend fun getAllAvailableAudioFormats(
        videoId: String
    ): Result<List<AudioFormatOption>> = runCatching {
        Timber.tag(TAG).d("=== Fetching ALL audio formats for $videoId from multiple clients ===")

        val allClients = listOf(WEB_REMIX, TVHTML5, ANDROID_VR_1_43_32, IOS)
        val allFormats = mutableListOf<AudioFormatOption>()
        val seenItags = mutableSetOf<Int>()

        // Get signature timestamp once
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        // Check login status
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)
        val sessionId = if (isLoggedIn) YouTube.dataSyncId ?: YouTube.visitorData else YouTube.visitorData

        // Generate PoToken once for web clients
        val poTokenResult: PoTokenResult? = try {
            if (sessionId != null) poTokenGenerator.getWebClientPoToken(videoId, sessionId) else null
        } catch (e: Exception) {
            Timber.tag(TAG).e("PoToken generation failed", e)
            null
        }

        for (client in allClients) {
            try {
                if (client.loginRequired && !isLoggedIn) {
                    Timber.tag(TAG).d("Skipping ${client.clientName} - requires login")
                    continue
                }

                val response = YouTube.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    webPlayerPot = if (client.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
                ).getOrNull()

                if (response?.playabilityStatus?.status != "OK") {
                    Timber.tag(TAG).d("${client.clientName}: status=${response?.playabilityStatus?.status}")
                    continue
                }

                val formats = response.streamingData?.adaptiveFormats
                    ?.filter { it.isAudio && it.isOriginal }
                    ?: continue

                for (format in formats) {
                    // Dedupe by itag (same format from different clients)
                    if (seenItags.contains(format.itag)) continue
                    seenItags.add(format.itag)

                    val bitrateKbps = format.bitrate / 1000
                    val codec = when {
                        format.mimeType.contains("opus") -> "OPUS"
                        format.mimeType.contains("mp4a") -> "M4A"
                        else -> format.mimeType.substringAfter("audio/").substringBefore(";").uppercase()
                    }
                    allFormats.add(
                        AudioFormatOption(
                            itag = format.itag,
                            bitrate = format.bitrate,
                            bitrateKbps = bitrateKbps,
                            mimeType = format.mimeType,
                            codec = codec,
                        )
                    )
                    Timber.tag(TAG).d("  ${client.clientName}: ${bitrateKbps}kbps $codec (itag=${format.itag})")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to fetch from ${client.clientName}", e)
            }
        }

        // Sort by bitrate (highest first), then by codec (M4A before OPUS for same bitrate)
        val sorted = allFormats.sortedWith(compareByDescending<AudioFormatOption> { it.bitrate }.thenBy { it.codec })
        Timber.tag(TAG).d("=== Total unique formats: ${sorted.size} ===")
        sorted
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean,
        maxVideoBitrateKbps: Int?,
        forDownload: Boolean = false,
        targetBitrateKbps: Int = 0, // 0 = use audioQuality, >0 = target specific bitrate
        targetItag: Int = 0, // 0 = auto, >0 = exact itag
    ): PlayerResponse.StreamingData.Format? {
        // If exact itag requested, find it directly (for user-selected quality downloads)
        if (targetItag > 0) {
            val exactFormat = playerResponse.streamingData?.adaptiveFormats
                ?.find { it.itag == targetItag }
            if (exactFormat != null) {
                Timber.tag(TAG).i("Found exact itag=$targetItag: mime=${exactFormat.mimeType}, bitrate=${exactFormat.bitrate/1000}kbps")
                return exactFormat
            }
            // Don't fall back to quality selection - return null to try next client
            // This ensures user's exact format choice is respected
            Timber.tag(TAG).d("Exact itag=$targetItag not found in this client, trying next client")
            return null
        }
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

        // For downloads: exclude webm UNLESS user explicitly selected a format (targetItag > 0)
        // For streaming: prefer opus (webm) for better quality
        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.let { formats ->
                if (forDownload && targetItag == 0) {
                    // Exclude webm for auto downloads - MediaStore doesn't categorize it well
                    // But if user explicitly chose a format (targetItag > 0), allow any codec
                    formats.filter { !it.mimeType.startsWith("audio/webm") }.ifEmpty { formats }
                } else {
                    formats
                }
            }

        audioFormats?.forEach { format ->
            Timber.tag(TAG).d("  Available format: itag=${format.itag}, mime=${format.mimeType}, bitrate=${format.bitrate/1000}kbps")
        }

        val audioFormat: PlayerResponse.StreamingData.Format?

        // If target bitrate specified, find closest match
        if (targetBitrateKbps > 0) {
            Timber.tag(TAG).i("Format selection: targetBitrate=${targetBitrateKbps}kbps, availableFormats=${audioFormats?.size ?: 0}")

            // Find format with closest bitrate to target, preferring opus for streaming
            audioFormat = audioFormats?.minByOrNull { format ->
                val bitrateKbps = format.bitrate / 1000
                val distance = kotlin.math.abs(bitrateKbps - targetBitrateKbps)
                // Slight preference for opus (subtract 5 from distance for opus)
                if (!forDownload && format.mimeType.startsWith("audio/webm")) distance - 5 else distance
            }
        } else {
            // Legacy: use audioQuality enum
            val isMetered = connectivityManager.isActiveNetworkMetered
            val effectiveQuality = when (audioQuality) {
                AudioQuality.AUTO -> if (isMetered) "LOW (metered)" else "HIGH (unmetered)"
                AudioQuality.HIGH -> "HIGH"
                AudioQuality.LOW -> "LOW"
            }
            Timber.tag(TAG).i("Format selection: audioQuality=$audioQuality, effectiveQuality=$effectiveQuality, availableFormats=${audioFormats?.size ?: 0}")

            audioFormat = audioFormats?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (isMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (!forDownload && it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus for streaming only
            }
        }

        Timber.tag(TAG).i("Selected format: itag=${audioFormat?.itag}, mime=${audioFormat?.mimeType}, bitrate=${audioFormat?.bitrate?.div(1000)}kbps")

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
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(TAG).d( "Signature timestamp fetched: $it") }
            .onFailure {
                Timber.tag(TAG).e( "Signature timestamp fetch FAILED", it)
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Resolves a playable stream URL from the format.
     * Tries custom cipher deobfuscation first, then NewPipe extractor as fallback,
     * and finally the raw format URL if all else fails.
     */
    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        Timber.tag(TAG).d( "findUrlOrNull: signatureCipher=${format.signatureCipher != null}, directUrl=${format.url != null}")

        var url: String? = null

        // Try custom cipher deobfuscation first (for signatureCipher URLs)
        if (format.signatureCipher != null) {
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

        // If both failed, use direct format URL
        if (url == null) {
            url = format.url?.also {
                Timber.tag(TAG).d( "Using direct format URL for $videoId (all extractors failed)")
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
