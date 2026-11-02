package com.jtech.zemer.crash

/**
 * Removes credentials from crash report text before a report is queued: a stored live token is an attack surface.
 * Stream URL query strings (signatures, poTokens), poToken / visitorData / OAuth token values, the request
 * signature header and bearer tokens. Addresses, emails and account ids are kept (owner rule: maximum collection).
 */
internal object CrashScrubber {
    private val url = Regex("""https?://[^\s"'<>]+""")
    private val param = Regex("""(?i)\b(pot|visitorData|sig|n|key|token|access_token|id_token|refresh_token)=[^&\s"';,]+""")
    private val jsonSecret = Regex(
        """(?i)"(pot|poToken|visitorData|sig|key|token|access_token|id_token|refresh_token|idToken|accessToken|refreshToken)"\s*:\s*"[^"]*"""",
    )
    private val header = Regex("""(?i)\b(X-Zemer-Sig|Authorization|Cookie)\s*[:=]\s*[^\r\n]+""")

    fun scrub(text: String): String {
        var s = url.replace(text) { it.value.substringBefore('?').substringBefore('#') }
        s = header.replace(s) { "${it.groupValues[1]}: <redacted>" }
        s = param.replace(s) { "${it.groupValues[1]}=<redacted>" }
        s = jsonSecret.replace(s) { "\"${it.groupValues[1]}\":\"<redacted>\"" }
        return s
    }
}
