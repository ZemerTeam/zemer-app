package com.jtech.zemer.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jtech.zemer.extensions.isInternetConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The internet-reachability gate the network-dependent onboarding steps share. */
data class OnboardingConnectivity(val isConnected: Boolean, val isChecking: Boolean)

/**
 * ONE implementation of the onboarding connectivity poll (Welcome / Density / Content filters shared
 * three byte-identical copies before this). Probes actual reachability via
 * [isInternetConnected][com.jtech.zemer.extensions.isInternetConnected] (a real DNS socket, so a
 * captive/no-internet network reads as offline) once up front, then re-checks every 2s and republishes
 * only on change. Kept as a plain socket poll rather than [com.jtech.zemer.utils.NetworkConnectivityObserver]
 * on purpose: the callback observer keys off NetworkCapabilities, which report "connected" on a network
 * whose internet is actually down.
 */
@Composable
fun rememberOnboardingConnectivity(): OnboardingConnectivity {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isConnected = withContext(Dispatchers.IO) { context.isInternetConnected() }
        isChecking = false
        while (true) {
            delay(2000)
            val now = withContext(Dispatchers.IO) { context.isInternetConnected() }
            if (now != isConnected) isConnected = now
        }
    }

    return OnboardingConnectivity(isConnected, isChecking)
}
