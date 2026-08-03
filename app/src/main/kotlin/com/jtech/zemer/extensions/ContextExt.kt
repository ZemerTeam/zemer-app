package com.jtech.zemer.extensions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.jtech.zemer.R
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.utils.dataStore
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Show a short (or [long]) toast — the one wrapper over `Toast.makeText(this, …, …).show()`. Two
 * overloads mirror the framework: a string-resource id and a [CharSequence].
 */
fun Context.toast(@StringRes resId: Int, long: Boolean = false) {
    Toast.makeText(this, resId, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Context.toast(text: CharSequence, long: Boolean = false) {
    Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

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
 * Copy [text] to the clipboard under [label] and confirm with a short toast — the one place the app
 * touches `ClipboardManager`. [text] is a `CharSequence` so an `AnnotatedString` copies verbatim.
 * [confirmationRes] is the toast string: the generic "copied" by default; link copies pass
 * `R.string.link_copied`.
 */
fun Context.copyToClipboard(
    label: CharSequence,
    text: CharSequence,
    @StringRes confirmationRes: Int = R.string.copied,
) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    toast(confirmationRes)
}

/**
 * Open a link found in user content (a status description/body). If the URL matches one of the app's
 * OWN registered deep links (YouTube, music/video.zemer.io - see the manifest intent filters), it is
 * handed to this app so it opens NATIVELY. Everything else is forced into the external browser and can
 * NEVER land in an in-app webview: the browser intent is pinned to the default browser package (resolved
 * via a scheme-only probe no app deep-links), so even a link some other app claims still opens in the
 * browser. Fail-soft: on any error it retries an unpinned open so the link still resolves.
 */
fun Context.openStatusLink(url: String) {
    val uri = Uri.parse(url)
    val viewIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    val appHandlesIt = runCatching {
        packageManager.queryIntentActivities(viewIntent, 0).any { it.activityInfo.packageName == packageName }
    }.getOrDefault(false)

    if (appHandlesIt) {
        // A registered deep link -> route to THIS app (native handling, not a webview).
        val opened = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (opened) return
    }
    openInExternalBrowser(uri)
}

private fun Context.openInExternalBrowser(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Pin to the default BROWSER (probe a scheme-only https URI that no app deep-links) so the link can
    // never resolve into this app or an in-app webview.
    val browserProbe = Intent(Intent.ACTION_VIEW, Uri.fromParts("https", "", null))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    runCatching { packageManager.resolveActivity(browserProbe, 0)?.activityInfo?.packageName }
        .getOrNull()
        ?.takeIf { it != packageName }
        ?.let { intent.setPackage(it) }
    runCatching { startActivity(intent) }.onFailure {
        // Pinned browser unavailable: retry unpinned so the link still opens somewhere sane.
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
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
