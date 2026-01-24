package com.jtech.zemer.utils

import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.AudioQuality
import timber.log.Timber
import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
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

    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
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
    ): Result<PlaybackData> = runCatching {
        val mainClient = MAIN_CLIENT
        val fallbackClients = STREAM_FALLBACK_CLIENTS

        Timber.tag(TAG).d("Fetching player response for videoId: $videoId, client: ${mainClient.clientName}")

        val defaultStreamTtlSeconds = 6 * 60 * 60 // 6 hours
        /**
         * This is required for some clients to get working streams however
         * it should not be forced for the main client because the response of the main client
         * is required even if the streams won't work from this client.
         * This is why it is allowed to be null.
         */
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        // Enhanced authentication validation with SAPISID check
        val currentAuthCookie = YouTube.cookie
        val isLoggedIn = currentAuthCookie != null && "SAPISID" in parseCookieString(currentAuthCookie)

        if (isLoggedIn) {
            // signed in sessions use dataSyncId as identifier
            YouTube.dataSyncId
        } else {
            // signed out sessions use visitorData as identifier
            YouTube.visitorData
        }

        Timber.tag(TAG).d("Attempting to get player response using main client: ${mainClient.clientName}")
        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, mainClient, signatureTimestamp).getOrThrow()
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
                Timber.tag(TAG).d("Trying stream from main client: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = fallbackClients[clientIndex]
                Timber.tag(TAG).d("Trying fallback client ${clientIndex + 1}/${fallbackClients.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn) {
                    // skip client if it requires login but user is not properly authenticated
                    Timber.tag(TAG).d("Skipping client ${client.clientName} - requires login but user is not authenticated")
                    continue
                }

                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(TAG).d("Player response status OK for client: ${client.clientName}")

                format =
                    findFormat(
                        streamPlayerResponse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        maxVideoBitrateKbps,
                    )

                if (format == null) {
                    Timber.tag(TAG).d("No suitable format found for client: ${client.clientName}")
                    continue
                }

                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl == null) {
                    continue
                }

                streamExpiresInSeconds =
                    streamPlayerResponse.streamingData?.expiresInSeconds
                        ?: streamUrl.let(::deriveExpireSecondsFromUrl)
                        ?: defaultStreamTtlSeconds

                Timber.tag(TAG).d("Stream expires in: $streamExpiresInSeconds seconds")

                if (streamExpiresInSeconds <= 0) {
                    continue
                }

                // Skip validation for main client - it almost always works and
                // skipping the HEAD request saves ~300-500ms on initial playback
                if (clientIndex == -1) {
                    Timber.tag(TAG).d("Using main client directly without validation for faster playback")
                    break
                }

                if (clientIndex == fallbackClients.size - 1) {
                    /** skip [validateStatus] for last client */
                    Timber.tag(TAG).d("Using last fallback client without validation: ${client.clientName}")
                    break
                }

                if (validateStatus(streamUrl)) {
                    // working stream found
                    Timber.tag(TAG).d("Stream validated successfully with client: ${client.clientName}")
                    break
                } else {
                    Timber.tag(TAG).d("Stream validation failed for client: ${client.clientName}")
                }
            } else {
                Timber.tag(TAG).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            throw Exception("Could not find stream url")
        }

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

        // Filter for MediaStore-compatible formats (exclude webm/opus which MediaStore doesn't support)
        val compatibleFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal && !it.mimeType.startsWith("audio/webm") }

        // If no compatible formats, fall back to all audio formats
        val audioFormats = if (compatibleFormats.isNullOrEmpty()) {
            playerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio && it.isOriginal }
        } else {
            compatibleFormats
        }

        val audioFormat = audioFormats?.maxByOrNull {
            it.bitrate * when (audioQuality) {
                AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                AudioQuality.HIGH -> 1
                AudioQuality.LOW -> -1
            }
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
            // Validate URL before attempting to build request
            val validatedUrl = UrlValidator.validateAndParseUrl(url)
                ?: return false.also { reportException(Exception("Invalid stream URL: $url")) }

            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(validatedUrl)
            val request = try {
                requestBuilder.build()
            } catch (e: Exception) {
                reportException(Exception("Failed to build request for URL: $url", e))
                return false
            }
            val response = httpClient.newCall(request).execute()
            val isSuccessful = response.isSuccessful
            return isSuccessful
        } catch (e: Exception) {
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
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        val url = NewPipeUtils.getStreamUrl(format, videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull() ?: return null

        // Validate the URL before returning it to prevent OkHttp crashes
        return if (UrlValidator.isValidUrl(url)) {
            url
        } else {
            reportException(Exception("Stream URL from NewPipe validation failed: $url"))
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
