package com.jtech.zemer.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import com.jtech.zemer.ui.screens.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher manners while Zemer is the default home app:
 * - Back at the root does nothing (it would otherwise finish the home activity and bounce the launcher);
 * - a Home press ([homePresses], fed by MainActivity.onNewIntent) returns to the Home screen.
 *
 * Compose it BEFORE the NavHost and the other BackHandlers so they keep priority: this only catches
 * the Back that nothing else wanted.
 */
@Composable
fun HomeLauncherBehavior(navController: NavHostController, homePresses: Flow<Unit>) {
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

    BackHandler(enabled = isDefaultHome) { /* a launcher has nowhere to go back to */ }

    LaunchedEffect(navController) {
        homePresses.collect {
            if (navController.currentDestination == null) return@collect // graph not set yet
            navController.navigate(Screens.Home.route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}
