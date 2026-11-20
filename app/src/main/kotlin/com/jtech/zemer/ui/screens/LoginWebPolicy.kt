package com.jtech.zemer.ui.screens

/**
 * What the sign-in WebView in [LoginScreen] may load: only what a Google sign-in needs (password, SMS code,
 * tap-Yes prompts, captcha, the consent page and the music.youtube.com landing the login is captured from).
 * Every other request, embedded frames included, is refused, so the page's Help, Guest mode, Privacy and
 * store links lead nowhere: an allowlist, never a list of hosts to block.
 */
object LoginWebPolicy {
    private val hosts = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "music.youtube.com",
        "consent.youtube.com",
        // The push channel a tap-Yes prompt's approval arrives on (an API, no pages).
        "signaler-pa.googleapis.com",
        "www.gstatic.com",
        "ssl.gstatic.com",
        "fonts.gstatic.com",
        "fonts.googleapis.com",
    )

    // www.google.com is a search engine: only its captcha is needed.
    private val pathPrefixes = mapOf("www.google.com" to "/recaptcha/")

    fun allows(scheme: String?, host: String?, path: String?): Boolean {
        if (!scheme.equals("https", ignoreCase = true) || host == null) return false
        val h = host.lowercase()
        if (h in hosts) return true
        val prefix = pathPrefixes[h] ?: return false
        return path.orEmpty().startsWith(prefix)
    }

    /** The landing page: the WebView is hidden there, since the login is only captured from it. */
    fun isLanding(host: String?): Boolean = host.equals("music.youtube.com", ignoreCase = true)
}
