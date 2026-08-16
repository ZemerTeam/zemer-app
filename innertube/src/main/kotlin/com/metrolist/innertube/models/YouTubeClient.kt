package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

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
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
) {
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
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260213.00.00",
            clientId = "62",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            // Verified against the live CDN (tests/web-creator-stream.mjs): WEB_CREATOR returns
            // ciphered URLs that 403 past the 1 MiB free window unless a videoId-bound pot is
            // appended (HEAD also 403s without it). Enabling poTokens makes it stream the whole
            // song — without this it is a dead fallback.
            useWebPoTokens = true,
        )

        /**
         * The mobile-web client (yt-dlp-master-exact `mweb`, iPad UA). Requires a signed-in cookie:
         * anonymous requests 403 at the CDN under every pot binding, authenticated ones drain whole
         * songs (validated 2026-08-15 on multiple videos via tests/client-fulldownload.mjs). Streams
         * through the normal web path (cipher sig + n-transform + web poToken).
         */
        val MWEB = YouTubeClient(
            clientName = "MWEB",
            clientVersion = "2.20260708.05.00",
            clientId = "2",
            userAgent = "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
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
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )




        /**
         * The current VR client (v1.65.10, eureka build). Its eureka-style UA clears the bot gate
         * ("confirm you're not a bot") where the older 1.61.48/1.43.32 UAs are rejected. Like every VR
         * client it serves a DIRECT stream URL used AS-IS — no signatureCipher, no n-transform, no
         * poToken (yt-dlp `android_vr`: REQUIRE_JS_PLAYER=false, no GVS poToken policy); on-device
         * validated. Shares clientName "ANDROID_VR" (the "Android VR" stream-source toggle governs it).
         */
        val ANDROID_VR_1_65_10 = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            loginSupported = false,
            useSignatureTimestamp = false
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
            useSignatureTimestamp = false
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
            useSignatureTimestamp = false
        )

    }
}
