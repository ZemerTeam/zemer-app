package com.jtech.zemer.ui.screens

/**
 * Pure helpers for the WebView login capture in [LoginScreen], extracted so the capture contract is
 * JVM-testable (the WebView flow itself needs instrumentation the project doesn't have).
 *
 * Background (issue #140): the login page's `yt.config_` object is not guaranteed to exist yet when
 * `onPageFinished` fires. The old injection was a raw property chain
 * (`Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)`), so on a slow page it THREW, the
 * bridge callback never ran, and the login completed with a cookie but **no dataSyncId** — which is
 * exactly the pooled/anonymous account state, so playlist sync showed "Not logged in to YouTube" for a
 * genuinely personal login. The guarded scripts below always call back (null when unavailable), and
 * [isCaptureComplete] gates the login-complete restart on the dataSyncId actually arriving.
 */
internal object LoginCapture {
    /** Always calls back — null when `yt.config_` isn't ready — so the retry loop can observe progress. */
    const val EXTRACT_VISITOR_DATA_JS =
        "javascript:(function(){try{Android.onRetrieveVisitorData((window.yt&&yt.config_&&yt.config_.VISITOR_DATA)||null)}catch(e){Android.onRetrieveVisitorData(null)}})()"

    const val EXTRACT_DATA_SYNC_ID_JS =
        "javascript:(function(){try{Android.onRetrieveDataSyncId((window.yt&&yt.config_&&yt.config_.DATASYNC_ID)||null)}catch(e){Android.onRetrieveDataSyncId(null)}})()"

    /** Poll cadence for re-injecting the extractors after the login landing page finishes. */
    const val CAPTURE_RETRY_DELAY_MS = 500L

    /** ~10 s total — the page's `yt.config_` appears within a few seconds even on slow devices. */
    const val MAX_CAPTURE_ATTEMPTS = 20

    /** DATASYNC_ID arrives as `id||suffix`; the id part is what the app stores. Null/blank → null. */
    fun cleanDataSyncId(raw: String?): String? =
        raw?.substringBefore("||")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The login is only complete when the cookie is a signed-in one (carries SAPISID) AND the personal
     * dataSyncId arrived. Restarting without the dataSyncId persists the pooled/anonymous state.
     */
    fun isCaptureComplete(cookie: String?, dataSyncId: String?): Boolean =
        cookie?.contains("SAPISID") == true && !dataSyncId.isNullOrBlank()

    /**
     * An incomplete capture is a bug to report only when the user DID sign in (a SAPISID cookie) and the
     * dataSyncId still never arrived. Without that cookie the page was simply reached without signing in
     * (a filtered network, the user leaving the form), which is not an error.
     */
    fun isCaptureFailure(cookie: String?, dataSyncId: String?): Boolean =
        cookie?.contains("SAPISID") == true && dataSyncId.isNullOrBlank()
}
