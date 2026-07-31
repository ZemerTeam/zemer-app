package com.jtech.zemer.extensions

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.utils.dataStore
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Share a plain-text payload (a deep link / share URL) through the system chooser. This is the one
 * place the app builds an `ACTION_SEND` `text/plain` intent — call sites pass only the text, and any
 * `Tracker.action(SHARE, …)` / `onDismiss()` stays at the call site. File/stream shares (log export,
 * lyric image) are a different intent shape and deliberately keep their own builder.
 */
fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, null))
}

/**
 * Flow-based alternative for UI code.
 * Emit true when sync is enabled and user is logged in.
 * Safe to use in Composables and Flows.
 */
@Suppress("unused")
fun Context.isSyncEnabledFlow(): Flow<Boolean> {
    return dataStore.data.map { prefs ->
        try {
            prefs[YtmSyncKey] ?: true
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to read sync preference")
            false
        }
    }
}

/**
 * Flow-based alternative for UI code.
 * Emit true when user has valid authentication cookie.
 * Safe to use in Composables and Flows.
 */
fun Context.isUserLoggedInFlow(): Flow<Boolean> {
    return dataStore.data.map { prefs ->
        try {
            val cookie = prefs[InnerTubeCookieKey] ?: ""
            "SAPISID" in parseCookieString(cookie)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to check login cookie")
            false
        }
    }
}

fun Context.isInternetConnected(): Boolean {
    return try {
        // First check if we have a network connection
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        // Check if network has internet capability
        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }

        // For more accurate detection, try a simple socket connection
        // This is faster than HTTP and more reliable than just checking capabilities
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1500) // Google DNS, 1.5 second timeout
            socket.close()
            true
        } catch (e: Exception) {
            // If we can't reach Google DNS, try Cloudflare
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("1.1.1.1", 53), 1500) // Cloudflare DNS
                socket.close()
                true
            } catch (e2: Exception) {
                false
            }
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to check internet connectivity")
        false
    }
}
