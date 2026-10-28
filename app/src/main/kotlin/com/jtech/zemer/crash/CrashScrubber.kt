package com.jtech.zemer.crash

/**
 * Removes what must never leave the device in a crash report: stream URLs carry the user's IP and tokens,
 * and logs carry poTokens, visitorData, search text and account ids. Applied to messages and breadcrumbs
 * before a report is queued (the server scrubs again).
 */
internal object CrashScrubber {
    private val url = Regex("""https?://[^\s"'<>]+""")
    private val param = Regex("""(?i)\b(ip|pot|visitorData|sig|n|key|token)=[^&\s"';,]+""")
    private val jsonSecret = Regex("""(?i)"(ip|pot|poToken|visitorData|sig|key|token)"\s*:\s*"[^"]*"""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    // four or more groups, or a compressed "::" form; a clock time like 10:23:45 has fewer
    private val ipv6 = Regex(
        """(?<![\w:])(?:(?:[0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4}::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4})(?![\w:])""",
    )
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

    /** [secrets]: exact values to remove too (the signed-in account's uid). */
    fun scrub(text: String, secrets: Collection<String> = emptyList()): String {
        var s = url.replace(text) { it.value.substringBefore('?').substringBefore('#') }
        s = param.replace(s) { "${it.groupValues[1]}=<redacted>" }
        s = jsonSecret.replace(s) { "\"${it.groupValues[1]}\":\"<redacted>\"" }
        s = email.replace(s, "<email>")
        s = ipv4.replace(s, "<ip>")
        s = ipv6.replace(s, "<ip>")
        for (secret in secrets) if (secret.length >= 6) s = s.replace(secret, "<uid>")
        return s
    }
}
