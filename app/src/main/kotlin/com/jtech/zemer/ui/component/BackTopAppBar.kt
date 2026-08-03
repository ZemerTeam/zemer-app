package com.jtech.zemer.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * The one shared top-app-bar container color for every ordinary screen, so bars never drift between
 * grey / black / tinted per screen. Matches the Home top bar: pure black under the AMOLED
 * (pure-black) theme, `surfaceContainer` otherwise. The container and scrolled-container colors are
 * the same so the bar does not change shade on scroll.
 *
 * Use this as `colors = zemerTopAppBarColors()` on any screen-level `TopAppBar`. The only bars that
 * should NOT use it are the deliberately full-bleed color screens (login gate, onboarding) and the
 * video player (fixed black over video).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun zemerTopAppBarColors(): TopAppBarColors {
    // The bar sits on the tinted surfaceContainer — a step above the body in dark mode, and true black
    // in pure-black mode (the scheme drives surfaceContainer to black there, so no hardcoding needed).
    // Title/icons stay neutral; the accent lives in content + the nav pill.
    val container = MaterialTheme.colorScheme.surfaceContainer
    return TopAppBarDefaults.topAppBarColors(
        containerColor = container,
        scrolledContainerColor = container,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The shared plain back-only [TopAppBar]: a bold [AppBarTitle]-style title and a [BackNavigationIcon]
 * navigation icon, on the shared [zemerTopAppBarColors]. Used by screen-level top bars that have no
 * other actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(
    title: @Composable () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        navigationIcon = { BackNavigationIcon(navController) },
        colors = zemerTopAppBarColors(),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}
