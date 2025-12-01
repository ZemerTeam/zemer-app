package com.jtech.zemer.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.get
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * WARNING: This function uses runBlocking() which blocks the calling thread.
 * ONLY use this during application initialization (App.kt) or in background contexts.
 * DO NOT use on the main/UI thread as this will cause ANR.
 *
 * Prefer the Flow-based alternative for UI code:
 * - Use `Context.isSyncEnabledFlow()` for Composables and reactive code
 */
fun Context.isSyncEnabled(): Boolean {
    return try {
        runBlocking {
            dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to check sync enabled status, defaulting to false")
        false
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
 * WARNING: This function uses runBlocking() which blocks the calling thread.
 * ONLY use this during application initialization (App.kt) or in background contexts.
 * DO NOT use on the main/UI thread as this will cause ANR.
 *
 * Prefer the Flow-based alternative for UI code:
 * - Use `Context.isUserLoggedInFlow()` for Composables and reactive code
 */
fun Context.isUserLoggedIn(): Boolean {
    return try {
        runBlocking {
            val cookie = dataStore[InnerTubeCookieKey] ?: ""
            "SAPISID" in parseCookieString(cookie) && isInternetConnected()
        }
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to check login status, defaulting to false")
        false
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
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}
