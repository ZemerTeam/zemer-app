package com.jtech.zemer.distribution

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Play flavor: no-op. The accessibility button-mapper (and its setup screen) is not shipped on
 * Play (Accessibility API policy). See docs/play/PLAN.md §1.2.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.buttonSetupRoute(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) { /* no button-mapper setup route on Play */ }
