package com.jtech.zemer.utils

import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.jtech.zemer.constants.AudioQuality
import com.jtech.zemer.playback.VideoDecoderCaps
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.jtech.zemer.constants.StreamSourceTVHTML5Key
import com.jtech.zemer.constants.StreamSourceWebRemixKey
import kotlinx.coroutines.flow.first

import timber.log.Timber
import com.metrolist.innertube.YouTube
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import com.zemer.cipher.potoken.PoTokenResult
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS_0_1
import com.metrolist.innertube.models.YouTubeClient.Companion.MWEB
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.utils.ResilientDns
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.CancellationException
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
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    // Track videoIds where WEB_REMIX stream URLs 403 on ExoPlayer GET, so the next
    // resolution falls through to TVHTML5/VISIONOS instead of looping.
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
    // 403s on ExoPlayer and hits MusicService's handler); WEB_CREATOR / TVHTML5_SIMPLY / MWEB are validated
    // here and never reach ExoPlayer, so without this trigger a WEB_REMIX-disabled user would never
    // self-heal a stale/wrong cipher config. Kept off the resolution coroutine so the (network)
    // refresh never blocks falling through to the next client.
    private val cipherRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Client names disabled by the user in Settings → Stream sources. Updated by MusicService. */
    var disabledStreamClients: Set<String> = emptySet()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val ALL_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        // VISIONOS first: its CDN URL has no `spc` gate, so it streams the whole song with no
        // poToken and no cipher (HEAD 200) — the most reliable fallback, ahead of the
        // TVHTML5 client. (The proven-dead clients were removed: the ANDROID_VR family (incl. the
        // last-living 1.65.10) 403s after 0 bytes on a whole-song drain; the pre-1.65 VR
        // variants were also version-bot-gated; MOBILE 400s authenticated / SABR-only anonymous; WEB,
        // IOS and IPADOS are SABR-only or 403-wall past the 1 MiB free window.)
        VISIONOS,
        // The previous visionOS config as its second chance behind the current 1.02.
        VISIONOS_0_1,
        WEB_CREATOR,
        // The one TV cipher client, governed by the "TVHTML5" stream-source toggle (7.x TVHTML5 and
        // tv_downgraded were both removed as proven dead: 7.x is SABR-only, tv_downgraded 403-walls
        // even yt-dlp-master-exact — re-add from clients-retired.mjs only if YouTube reverts them).
        TVHTML5_SIMPLY,
        // MWEB (yt-dlp-master iPad UA) last: a login-required cipher fallback that drains whole
        // songs when authenticated (the loginRequired gate skips it for login-less sessions). Last
        // because it has the largest dependency surface (auth + cipher + pot) and the shortest
        // track record; promote only after it has proven itself over time.
        MWEB
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
        /**
         * The video quality ladder of the response that served [format] (preferVideo only, else
         * empty) — feeds the in-player quality switcher via VideoModeController.
         */
        val videoQualities: List<VideoQualityRung> = emptyList(),
        /**
         * Ready-to-play stream URLs for EVERY ladder rung (itag → URL), resolved from the same
         * response/client that served [format] (preferVideo only). One resolution seeds them all
         * (MusicService.seedVideoUrlCaches) so a quality switch never needs another network
         * round-trip — the switch is a local replaceMediaItem + one CDN range request.
         */
        val videoRungUrls: Map<Int, String> = emptyMap(),
        /**
         * The merge-audio partner's ready-to-play URL + itag (preferVideo only): the audio track an
         * adaptive rung plays alongside. Seeding it avoids the second full player resolution the
         * `videoaudio:` branch would otherwise run.
         */
        val mergeAudioUrl: String? = null,
        val mergeAudioItag: Int? = null,
        /**
         * For a DOWNLOAD of an adaptive (video-only) rung: the CONTAINER-MATCHED audio partner
         * resolved from THIS SAME response/client (mp4/avc video → AAC, webm/vp9 video → Opus), so the
         * two-stream mux needs no second `/player` resolution — which would double the round-trip AND
         * could pick a different fallback client whose audio container disagrees, failing the mux.
         * Null for a progressive download (no mux) or when no compatible audio was found.
         */
        val downloadAudioUrl: String? = null,
        val downloadAudioMimeType: String? = null,
        val downloadAudioContentLength: Long? = null,
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
        // Explicit video-quality selection (preferVideo only; both null = the automatic progressive
        // pick, the pre-switcher behavior). videoItag = the EXACT rung a streaming quality swap
        // encoded in its rendition key; videoQualityTarget = a target LABEL ("1080p") resolved to the
        // best remux-capable rung at or below it (the download path, which has no ladder up front).
        videoItag: Int? = null,
        videoQualityTarget: String? = null,
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
            ).onFailure {
                // Distinguish thrown request/parse failures from genuine playability rejections
                // (both otherwise surface as a null response downstream).
                Timber.tag(TAG).e(it, "player() request FAILED for main client %s", mainClient.clientName)
            }.getOrNull()
        Timber.tag(TAG).d( "Main response status: ${mainPlayerResponse?.playabilityStatus?.status ?: "request failed"}")
        var audioConfig = mainPlayerResponse?.playerConfig?.audioConfig
        var videoDetails = mainPlayerResponse?.videoDetails
        var playbackTracking = mainPlayerResponse?.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var successClient: String? = null
        var successClientObj: YouTubeClient? = null

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
                    ).onFailure {
                        // A thrown network/HTTP/deserialization failure is not the same signal as
                        // a "Status NOT OK" playability rejection — log which one it was.
                        Timber.tag(TAG).e(it, "player() request FAILED for %s", client.clientName)
                    }.getOrNull()
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
                // redundant. Direct-url clients (VISIONOS) already
                // carry playable URLs; web clients are deciphered per-format by the Zemer
                // cipher in findUrlOrNull (sig) + transformNParamInUrl (n) below.
                val responseToUse = streamPlayerResponse

                // An EXPLICIT quality-rung streaming resolution (videoItag) must come from a WEB client
                // only: a non-web fallback (VISIONOS) can carry the itag, but its pot-bound URL
                // 403s past the 1 MiB wall — so the switched-to quality would play ~1 MiB then revert.
                // Skip non-web clients here so the loop finds a web client or fails safe (revert to
                // audio, re-resolve fresh on re-entry). Mirrors the ladder-seed's web-only rule.
                if (videoItag != null && !clientNeedsNTransform(client)) {
                    Timber.tag(TAG).d("Skipping non-web ${client.clientName} for explicit videoItag=$videoItag")
                    continue
                }

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        maxVideoBitrateKbps,
                        forDownload,
                        videoItag,
                        videoQualityTarget,
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
                val needsNTransform = clientNeedsNTransform(client)

                // VISIONOS (and other direct-URL clients) use the URL AS-IS: per yt-dlp
                // (REQUIRE_JS_PLAYER=False, no GVS poToken policy) their URLs are already ready — no
                // sig, no n-transform, no pot. Applying the web transforms would CORRUPT them.
                if (needsNTransform) {
                    try {
                        Timber.tag(TAG).d("Applying n-transform to stream URL for ${client.clientName}")
                        streamUrl = applyWebUrlTransforms(streamUrl, client, poTokenResult)
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
                    successClientObj = client
                    break
                }

                // WEB_REMIX authenticated CDN URLs 403 on HEAD but serve correctly
                // on the actual byte-range GET that ExoPlayer makes. Skip HEAD validation
                // for streaming UNLESS this videoId already failed on GET (tracked in
                // webRemixFailedIds), in which case fall through to TVHTML5/VISIONOS.
                // For downloads, always fall through — WEB_REMIX signed URLs don't support
                // the &range= query-param download pattern.
                if (client.clientName == "WEB_REMIX" && clientIndex == -1
                    && !forDownload && !webRemixFailedIds.contains(videoId)) {
                    Timber.tag(TAG).d("WEB_REMIX — skipping HEAD validation, letting ExoPlayer try directly")
                    successClient = client.clientName
                    successClientObj = client
                    break
                }

                val validationResult = validateStatus(streamUrl)
                if (validationResult) {
                    Timber.tag(TAG).d( "Stream VALIDATED OK with ${client.clientName}")
                    successClient = client.clientName
                    successClientObj = client
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

        // For a STREAMING video resolution (never forDownload — downloads read only streamUrl and the
        // ladder would be pure wasted cipher work), resolve EVERY ladder rung's URL plus the
        // merge-audio partner from this same response: pure local computation (sig decipher +
        // n-transform + pot append, no extra network), exactly what tests/video-qualities.mjs proves
        // works per rung, seeded into the URL cache so a quality switch never pays a second
        // round-trip. ONLY when the success client is a real web client (WEB_REMIX/WEB_CREATOR/
        // TVHTML5/WEB): a non-web fallback's URLs (IOS/IPADOS) 403 past the 1 MiB wall and are never
        // validated here, so seeding a whole ladder of them would make every quality switch fail a
        // minute in. A non-web success leaves the table empty — the switch does a fresh resolution.
        var videoRungUrls: Map<Int, String> = emptyMap()
        var mergeAudioUrl: String? = null
        var mergeAudioItag: Int? = null
        if (preferVideo && !forDownload && successClientObj != null && clientNeedsNTransform(successClientObj!!)) {
            val response = streamPlayerResponse
            val rungClient = successClientObj!!
            videoRungUrls = VideoQualityLogic.ladderFormats(response.streamingData)
                .mapNotNull { rungFormat ->
                    resolveFinalStreamUrl(rungFormat, videoId, response, rungClient, poTokenResult)
                        ?.let { rungFormat.itag to it }
                }.toMap()
            // Merge audio is ALWAYS resolved at HIGH so the seeded itag matches every other
            // preferVideo resolution (the merge-audio drift purge depends on that agreement).
            val mergeAudioFormat = findFormat(
                response, AudioQuality.HIGH, connectivityManager, preferVideo = false,
                maxVideoBitrateKbps = null, forDownload = false,
            )
            if (mergeAudioFormat != null) {
                mergeAudioUrl =
                    resolveFinalStreamUrl(mergeAudioFormat, videoId, response, rungClient, poTokenResult)
                mergeAudioItag = mergeAudioFormat.itag.takeIf { mergeAudioUrl != null }
            }
        }

        // For a DOWNLOAD of an adaptive (video-only) rung, resolve the CONTAINER-MATCHED audio partner
        // from THIS response/client so the mux needs no second resolution (which could disagree on
        // client → wrong container → mux failure). mp4/avc video → AAC (forDownload excludes webm);
        // webm/vp9 video → Opus (forDownload=false keeps opus). HIGH for a deterministic itag.
        var downloadAudioUrl: String? = null
        var downloadAudioMimeType: String? = null
        var downloadAudioContentLength: Long? = null
        if (forDownload && preferVideo && successClientObj != null && VideoQualityLogic.isVideoOnly(format)) {
            val response = streamPlayerResponse
            val audioClient = successClientObj!!
            val webmVideo = format.mimeType.startsWith("video/webm")
            val audioFormat = findFormat(
                response, AudioQuality.HIGH, connectivityManager, preferVideo = false,
                maxVideoBitrateKbps = null, forDownload = !webmVideo,
            )
            if (audioFormat != null) {
                downloadAudioUrl =
                    resolveFinalStreamUrl(audioFormat, videoId, response, audioClient, poTokenResult)
                if (downloadAudioUrl != null) {
                    downloadAudioMimeType = audioFormat.mimeType
                    downloadAudioContentLength = audioFormat.contentLength
                }
            }
        }

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            streamClient = successClient ?: "unknown",
            videoQualities = if (preferVideo) {
                VideoQualityLogic.rungs(streamPlayerResponse.streamingData)
            } else {
                emptyList()
            },
            videoRungUrls = videoRungUrls,
            mergeAudioUrl = mergeAudioUrl,
            mergeAudioItag = mergeAudioItag,
            downloadAudioUrl = downloadAudioUrl,
            downloadAudioMimeType = downloadAudioMimeType,
            downloadAudioContentLength = downloadAudioContentLength,
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
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // non-web fallbacks do not work with history
    }

    private fun clientNeedsNTransform(client: YouTubeClient): Boolean =
        client.useWebPoTokens || client.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")

    /**
     * The shared web-client URL finalization (n-transform first, then `pot=`) — one implementation
     * for the main resolution path and the ladder-wide rung-URL table, so the two can never drift.
     */
    private suspend fun applyWebUrlTransforms(
        url: String,
        client: YouTubeClient,
        poTokenResult: PoTokenResult?,
    ): String {
        // The cipher is the single n-transform source (it self-heals on rotation).
        var result = CipherDeobfuscator.transformNParamInUrl(url)
        if (client.useWebPoTokens && poTokenResult?.streamingDataPoToken != null) {
            val separator = if ("?" in result) "&" else "?"
            result = "$result${separator}pot=${android.net.Uri.encode(poTokenResult.streamingDataPoToken)}"
        }
        return result
    }

    /** Resolve one format's playable URL (decipher + web transforms); null on any failure. */
    private suspend fun resolveFinalStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        response: PlayerResponse,
        client: YouTubeClient,
        poTokenResult: PoTokenResult?,
    ): String? = runCatching {
        val url = findUrlOrNull(format, videoId, response) ?: return null
        if (clientNeedsNTransform(client)) applyWebUrlTransforms(url, client, poTokenResult) else url
    }.getOrNull()

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean,
        maxVideoBitrateKbps: Int?,
        forDownload: Boolean = false,
        videoItag: Int? = null,
        videoQualityTarget: String? = null,
    ): PlayerResponse.StreamingData.Format? {
        if (preferVideo) {
            // Explicit quality selections (the beyond-720p switcher / quality-aware downloads) pick
            // from the FULL ladder (progressive + adaptive video-only). The user's explicit choice is
            // not bitrate-capped — the metered cap governs only the automatic pick below, and the
            // metered gate on the PERSISTED default lives in VideoModeController.effectiveQualityTarget.
            if (videoItag != null) {
                // Deliberately NO fallback to the automatic pick: the itag came from a rendition key
                // (`video:<id>:q<itag>`) whose cache spans must only ever hold THAT itag's bytes —
                // resolving a different format under it would reintroduce the container-mixing
                // corruption class. A client without the itag is skipped; total failure surfaces
                // through the video error path (revert to audio), which is the safe outcome.
                return VideoQualityLogic.formatForItag(playerResponse.streamingData, videoItag)
            }
            if (videoQualityTarget != null && videoQualityTarget != VideoQualityLogic.AUTO) {
                // An EXPLICIT quality target (the user's Settings default or in-player pick) is
                // honored as chosen — NOT metered-capped (that would silently downgrade what the user
                // asked to save/watch). The metered bitrate cap governs only the AUTOMATIC pick below.
                // Downloads still gate on decoder capability (the streaming ladder is filtered at
                // publish, but a download target arrives label-only): a rung the device cannot decode
                // must never become a committed LOCAL file that errors on every play.
                val rungs = VideoQualityLogic.rungs(playerResponse.streamingData)
                    .filter { it.progressive || VideoDecoderCaps.supports(it) }
                val rung = VideoQualityLogic.selectRung(
                    rungs,
                    videoQualityTarget,
                    downloadable = forDownload,
                    opusWebmMuxSupported = android.os.Build.VERSION.SDK_INT >= 29,
                )
                if (rung != null) {
                    return VideoQualityLogic.formatForItag(playerResponse.streamingData, rung.itag)
                }
                // Fail-soft: an unresolvable target falls through to the automatic progressive pick.
            }
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
     * STS for the /player request, from the cipher player - the SINGLE source. It must come
     * from the same player generation the cipher deciphers with: a sig minted for one player
     * deciphered by another 403s on the CDN (observed 2026-06-09 when an independently
     * fetched player supplied sts=20611/69e2a55d while the cipher held ce74690f). A second,
     * independently fetched sts source is therefore a hazard, not a fallback - and the cipher
     * fetches the player over the same iframe_api route any fallback would use, so there is
     * no failure the fallback could survive that the cipher cannot. Null sts degrades the
     * /player response, never playback of clients that need no sig.
     */
    private suspend fun getSignatureTimestampOrNull(
        @Suppress("UNUSED_PARAMETER") videoId: String
    ): Int? {
        val cipherSts = try {
            CipherDeobfuscator.signatureTimestamp()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Cipher player STS fetch FAILED", e)
            reportException(e)
            null
        }
        if (cipherSts != null) {
            Timber.tag(TAG).d("Signature timestamp from cipher player: $cipherSts")
        }
        return cipherSts
    }
    /**
     * Resolves a playable stream URL from the format: a direct URL when the client serves
     * one, else the cipher deobfuscates the signatureCipher. The cipher is the SINGLE
     * decipher pipeline - it self-heals on rotation (forced config refresh + retry, remote
     * configs land within minutes), so there is deliberately no second extractor behind it
     * (the removed one was live-probed 2026-08-27: its sig parse fails on the current
     * player, so it could no longer produce a playable URL anyway).
     */
    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        response: PlayerResponse
    ): String? {
        Timber.tag(TAG).d( "findUrlOrNull: signatureCipher=${format.signatureCipher != null}, directUrl=${format.url != null}")

        var url: String? = null

        // Some clients serve a plain URL with no cipher - use it as-is.
        if (format.url != null) {
            Timber.tag(TAG).d( "Using direct URL from format")
            url = format.url
        }

        // Cipher deobfuscation for signatureCipher URLs.
        if (url == null && format.signatureCipher != null) {
            try {
                val deobfuscated = CipherDeobfuscator.deobfuscateStreamUrl(format.signatureCipher!!, videoId)
                if (deobfuscated != null) {
                    Timber.tag(TAG).d( "Custom cipher deobfuscation succeeded for $videoId")
                    url = deobfuscated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Throwable-first overload: passing e as a message vararg (no %s) silently
                // dropped the exception class/message/stack from logcat and Crashlytics.
                Timber.tag(TAG).e(e, "Custom cipher deobfuscation FAILED for %s", videoId)
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
