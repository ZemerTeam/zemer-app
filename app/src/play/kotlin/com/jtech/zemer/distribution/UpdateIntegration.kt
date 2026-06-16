package com.jtech.zemer.distribution

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.jtech.zemer.App

/**
 * Play flavor: no-op updater seams. The in-app self-updater is not shipped on Play (Device &
 * Network Abuse policy); updates come through Google Play. See docs/play/PLAN.md §1.1.
 */
object UpdateStartup {
    suspend fun check(app: App) { /* no in-app updater on Play */ }
}

@Composable
fun UpdateAvailabilityNotifier(onLatestVersion: (String) -> Unit) { /* no-op on Play */ }

@Composable
fun UpdateDownloadPrompt() { /* no-op on Play */ }

fun openLatestDownloadUrl(uriHandler: UriHandler) { /* no-op on Play */ }

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.updaterSettingsRoute(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) { /* no updater settings route on Play */ }
