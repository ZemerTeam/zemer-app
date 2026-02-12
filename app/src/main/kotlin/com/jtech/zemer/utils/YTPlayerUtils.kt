package com.jtech.zemer.utils

import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.AudioQuality

import timber.log.Timber
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.jtech.zemer.utils.cipher.CipherDeobfuscator
import com.jtech.zemer.utils.potoken.PoTokenGenerator
import com.jtech.zemer.utils.potoken.PoTokenResult
import com.jtech.zemer.utils.sabr.EjsNTransformSolver
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
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
        val mainClient = MAIN_CLIENT
        val fallbackClients = STREAM_FALLBACK_CLIENTS

        Timber.tag(TAG).d( "=== Stream resolution START for videoId=$videoId ===")
        Timber.tag(TAG).d( "Main client: ${mainClient.clientName}, audioQuality=$audioQuality, preferVideo=$preferVideo")

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

                if (clientIndex == fallbackClients.size - 1) {
                    Timber.tag(TAG).d( "Last fallback — skipping validation: ${client.clientName}")
                    break
                }

                val validationResult = validateStatus(streamUrl)
                if (validationResult) {
                    Timber.tag(TAG).d( "Stream VALIDATED OK with ${client.clientName}")
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
