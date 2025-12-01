package com.jtech.zemer.utils

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object UrlValidator {
    /**
     * Validates and safely parses a URL string for use with OkHttp
     * @param urlString The URL string to validate
     * @return HttpUrl if valid, null otherwise
     */
    fun validateAndParseUrl(urlString: String?): HttpUrl? {
        if (urlString.isNullOrBlank()) {
            return null
        }

        return try {
            val trimmedUrl = urlString.trim()

            // Ensure URL has a scheme
            val urlWithScheme = if (!trimmedUrl.startsWith("http://") &&
                !trimmedUrl.startsWith("https://")) {
                "https://$trimmedUrl"
            } else {
                trimmedUrl
            }

            // Parse and validate with HttpUrl
            val httpUrl = urlWithScheme.toHttpUrl()

            // Verify it's a valid HTTPS or HTTP URL with non-empty host
            if ((httpUrl.scheme == "https" || httpUrl.scheme == "http") && httpUrl.host.isNotEmpty()) {
                httpUrl
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Validates a URL string without parsing
     * @param urlString The URL string to validate
     * @return true if valid, false otherwise
     */
    fun isValidUrl(urlString: String?): Boolean {
        return validateAndParseUrl(urlString) != null
    }

    /**
     * Validates that a URL belongs to a trusted domain
     * @param urlString The URL string to validate
     * @param trustedHosts Whitelist of allowed hosts (e.g., "music.youtube.com")
     * @return true if URL is from a trusted host, false otherwise
     */
    fun isUrlFromTrustedHost(urlString: String?, trustedHosts: Set<String>): Boolean {
        val httpUrl = validateAndParseUrl(urlString) ?: return false
        return trustedHosts.contains(httpUrl.host)
    }

    /**
     * Safely extracts query parameters from a URL
     * @param urlString The URL string
     * @param paramName The parameter name to extract
     * @return The parameter value or null
     */
    fun getQueryParameter(urlString: String?, paramName: String): String? {
        val httpUrl = validateAndParseUrl(urlString) ?: return null
        return httpUrl.queryParameter(paramName)
    }

    /**
     * Validates stream URLs commonly used in the app
     * @param streamUrl The stream URL to validate
     * @return true if valid stream URL, false otherwise
     */
    fun isValidStreamUrl(streamUrl: String?): Boolean {
        if (streamUrl.isNullOrBlank()) return false

        val httpUrl = validateAndParseUrl(streamUrl) ?: return false

        // Stream URLs should have expected query parameters
        return (httpUrl.queryParameter("expire") != null ||
                httpUrl.queryParameter("exp") != null ||
                httpUrl.host.contains("googlevideo.com") ||
                httpUrl.host.contains("r")) // YouTube redirect domains
    }
}
