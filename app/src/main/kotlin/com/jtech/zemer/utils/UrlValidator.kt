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

            // Basic validation checks before parsing
            if (trimmedUrl.length > 2048) {
                // URLs should not be excessively long
                return null
            }

            // Check for illegal characters
            if (trimmedUrl.contains('\n') || trimmedUrl.contains('\r') || trimmedUrl.contains('\u0000')) {
                return null
            }

            // Ensure URL has a scheme
            val urlWithScheme = if (!trimmedUrl.startsWith("http://") &&
                !trimmedUrl.startsWith("https://")) {
                "https://$trimmedUrl"
            } else {
                trimmedUrl
            }

            // Parse and validate with HttpUrl
            val httpUrl = try {
                urlWithScheme.toHttpUrl()
            } catch (e: Exception) {
                // If OkHttp fails to parse, return null
                reportException(e)
                return null
            }

            // Verify it's a valid HTTPS or HTTP URL with non-empty host
            if ((httpUrl.scheme == "https" || httpUrl.scheme == "http") &&
                httpUrl.host.isNotEmpty() &&
                !httpUrl.host.contains('\u0000')) {
                httpUrl
            } else {
                null
            }
        } catch (e: Exception) {
            reportException(e)
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
     * Safely extracts query parameters from a URL
     * @param urlString The URL string
     * @param paramName The parameter name to extract
     * @return The parameter value or null
     */
    fun getQueryParameter(urlString: String?, paramName: String): String? {
        val httpUrl = validateAndParseUrl(urlString) ?: return null
        return httpUrl.queryParameter(paramName)
    }

}
