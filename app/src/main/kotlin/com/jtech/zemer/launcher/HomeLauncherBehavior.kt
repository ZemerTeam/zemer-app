package com.jtech.zemer.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pure launcher decisions (JVM-tested in HomeLauncherRulesTest). */
object HomeLauncherRules {
    /** Never yank the user out of an in-progress sign-in. */
    private val HOME_PRESS_IGNORED_ROUTES = setOf("login", "login_gate")

    /** Only the Back that would finish the home activity (nothing left to pop) is swallowed. */
    fun swallowBack(isDefaultHome: Boolean, hasPreviousEntry: Boolean): Boolean = isDefaultHome && !hasPreviousEntry

    fun handleHomePress(currentRoute: String?): Boolean = currentRoute != null && currentRoute !in HOME_PRESS_IGNORED_ROUTES
}

/**
 * While Zemer is the default home app, Back at the root does nothing (it would finish the home activity
 * and bounce the launcher). Gated on the back stack, so Back on inner screens is never affected.
 */
@Composable
fun HomeLauncherBackGuard(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDefaultHome by remember { mutableStateOf(false) }

    // The default home can change in system settings while we're away, so re-read it on every resume.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            isDefaultHome = withContext(Dispatchers.IO) { HomeAppAlias.isDefaultHome(context) }
        }
        onPauseOrDispose { job.cancel() }
    }

    val entry by navController.currentBackStackEntryAsState()
    val hasPreviousEntry = entry != null && navController.previousBackStackEntry != null
    BackHandler(enabled = HomeLauncherRules.swallowBack(isDefaultHome, hasPreviousEntry)) { /* nowhere to go back to */ }
}

/**
 * A Home press ([homePresses], fed by MainActivity.onNewIntent) runs [closeOverlays] (player sheet, drawer,
 * search) and returns to the start screen, like a launcher's Home button.
 */
@Composable
fun HomeButtonEffect(navController: NavHostController, homePresses: Flow<Unit>, closeOverlays: suspend () -> Unit) {
    val latestCloseOverlays by rememberUpdatedState(closeOverlays)
    LaunchedEffect(navController, homePresses) {
        homePresses.collect {
            if (!HomeLauncherRules.handleHomePress(navController.currentDestination?.route)) return@collect
            latestCloseOverlays()
            navController.popBackStack(navController.graph.startDestinationId, inclusive = false)
        }
    }
}
