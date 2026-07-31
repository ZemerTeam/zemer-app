package com.jtech.zemer.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * The shared plain back-only [TopAppBar]: a title and a [BackNavigationIcon] navigation icon.
 * Used by screen-level top bars that have no other actions.
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
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}
