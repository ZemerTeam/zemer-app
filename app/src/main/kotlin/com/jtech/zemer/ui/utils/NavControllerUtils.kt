package com.jtech.zemer.ui.utils

import androidx.navigation.NavController
import com.jtech.zemer.ui.screens.Screens

/**
 * The main-screen route to return to from a deep back stack: the topmost main route currently in the
 * stack. Pure and unit-tested so [backToMain] can pop straight there in one step. Null-safe against
 * destinations with no route.
 */
fun resolveBackToMainTarget(backStackRoutes: List<String?>, mainRoutes: Set<String>): String? =
    backStackRoutes.lastOrNull { it in mainRoutes }

/**
 * Return to the nearest main screen. Resolves the target once and pops there in a single
 * [NavController.popBackStack], instead of popping one frame at a time, which animated through every
 * intermediate screen on a long-press Back.
 */
fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }.toSet()
    val target = resolveBackToMainTarget(
        currentBackStack.value.map { it.destination.route },
        mainRoutes,
    )
    if (target != null) popBackStack(target, inclusive = false)
}
