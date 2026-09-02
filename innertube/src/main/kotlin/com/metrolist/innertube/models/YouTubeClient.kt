package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

/**
 * How a client's stream URLs become playable. This is the ONE selector for the per-client
 * stream-handling behavior bundle — all logic stays compiled; the enum only picks which path runs.
 */
@Serializable
enum class StreamProtocol {
    /**
     * The web path: sig deciphered by the cipher submodule, n-transform applied, `pot=` appended,
     * poToken sent in the /player body, signatureTimestamp sent in playbackContext. Eligible to
     * seed the video quality ladder / rung URLs / merge audio.
     */
    WEB_CIPHER_POT,

    /**
     * Direct-URL clients (VISIONOS/ANDROID_VR): the CDN URL is used AS-IS — no sig, no
     * n-transform, no pot (applying the web transforms would CORRUPT it), no STS in the body.
     * Never seeds the quality ladder.
     */
    DIRECT,
}

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val isEmbedded: Boolean = false,
    val protocol: StreamProtocol = StreamProtocol.DIRECT,
    /**
     * Skip the HEAD pre-validation when this client serves the MAIN stream slot: its
     * authenticated CDN URLs 403 on HEAD but serve correctly on ExoPlayer's byte-range GET
     * (a WEB_REMIX CDN quirk, kept as a flag so the main slot is not name-keyed).
     */
    val skipHeadValidation: Boolean = false,
) {
    /**
     * Derived from [protocol] — kept as properties because the request builder (InnerTube.ytClient/
     * player) reads them; the protocol is the single source of truth so the two can never drift.
     */
    val useSignatureTimestamp: Boolean get() = protocol == StreamProtocol.WEB_CIPHER_POT
    val useWebPoTokens: Boolean get() = protocol == StreamProtocol.WEB_CIPHER_POT

    fun toContext(locale: YouTubeLocale, visitorData: String?, dataSyncId: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData
        ),
        user = Context.User(
            onBehalfOfUser = if (loginSupported) dataSyncId else null
        ),
    )

    companion object {
        /**
         * Should be the latest Firefox ESR version.
         */
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

        // NOT a stream client (InnerTube next/transcript only). DIRECT here just keeps its request
        // shape unchanged (no STS, no poToken) — the protocol is never consulted for non-stream use.
        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260213.00.00",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            protocol = StreamProtocol.WEB_CIPHER_POT,
            // Authenticated WEB_REMIX CDN URLs 403 on HEAD but serve fine on ExoPlayer's
            // byte-range GET — as the main client it skips the HEAD pre-validation.
            skipHeadValidation = true,
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260213.00.00",
            clientId = "62",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            // Verified against the live CDN (tests/web-creator-stream.mjs): WEB_CREATOR returns
            // ciphered URLs that 403 past the 1 MiB free window unless a videoId-bound pot is
            // appended (HEAD also 403s without it). The web pot path makes it stream the whole
            // song — without it it is a dead fallback.
            protocol = StreamProtocol.WEB_CIPHER_POT,
        )

        /**
         * A minimal TV client (clientId 75) that serves ordinary ciphered adaptive URLs (not SABR),
         * so it streams through the existing web (cipher + web-poToken) path — unlike the current 7.x
         * TVHTML5, which returns SABR-only audio the app can't consume. Validated full-drain on-device;
         * governed by the "TVHTML5" stream-source toggle.
         */
        val TVHTML5_SIMPLY = YouTubeClient(
            clientName = "TVHTML5_SIMPLY",
            clientVersion = "1.0",
            clientId = "75",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
            protocol = StreamProtocol.WEB_CIPHER_POT,
        )




        /**
         * Cannot play livestreams and lacks HDR, but can play videos with music and labeled "for children".
         * <a href=\"https://dumps.tadiphone.dev/dumps/google/barbet\">Google Pixel 9 Pro Fold</a>
         */

        /**
         * Internal YT client for an unreleased YT client. May stop working at any time.
         */
        // yt-dlp-master-exact `visionos` (1.02): the 0.1 build was internal/unreleased and could be
        // retired any time; 1.02 validated whole-song drain against the live CDN (client-fulldownload)
        // and on-device. The previous 0.1 config stays below as [VISIONOS_0_1], the second-chance
        // fallback (same clientName, so the one "VisionOS" stream-source toggle governs both).
        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "1.02",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "26.5.23O471",
            deviceMake = "Apple",
            deviceModel = "RealityDevice17,1",
            loginSupported = false,
        )

        // The pre-1.02 visionOS config (still streaming whole songs as of 2026-08-15); kept as the
        // second-chance fallback behind [VISIONOS].
        val VISIONOS_0_1 = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            loginSupported = false,
        )

    }
}
