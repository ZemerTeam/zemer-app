package com.jtech.zemer.distribution

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jtech.zemer.ui.screens.settings.ButtonSetupScreen

/**
 * GitHub flavor: registers the accessibility button-mapper setup screen. The Play flavor provides
 * a no-op (the button-mapper is not shipped on Play). See docs/play/PLAN.md §1.2.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.buttonSetupRoute(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    composable("settings/dpad") {
        ButtonSetupScreen(navController, scrollBehavior)
    }
}
