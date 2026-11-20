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
        // Google's own after-sign-in prompts (confirm recovery info and the like).
        "gds.google.com",
        // The push channel a tap-Yes prompt's approval arrives on (an API, no pages).
        "signaler-pa.googleapis.com",
        "www.gstatic.com",
        "ssl.gstatic.com",
        "fonts.gstatic.com",
        "fonts.googleapis.com",
    )

    fun allows(scheme: String?, host: String?, path: String?): Boolean {
        if (!scheme.equals("https", ignoreCase = true) || host == null) return false
        val h = host.lowercase()
        // www.google.com is a search engine: only its captcha is needed.
        return h in hosts || (h == "www.google.com" && path.orEmpty().startsWith("/recaptcha/"))
    }

    /** The landing page: the WebView is hidden there, since the login is only captured from it. */
    fun isLanding(host: String?): Boolean = host.equals("music.youtube.com", ignoreCase = true)
}
