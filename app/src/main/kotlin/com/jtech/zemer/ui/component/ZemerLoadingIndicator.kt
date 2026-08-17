package com.jtech.zemer.ui.component

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The app's one content-loading spinner: the Material 3 Expressive [ContainedLoadingIndicator] (the
 * morphing shape on a filled container), used wherever a screen/section is loading its content - section
 * paging, "checking..." states. It matches the Metrolist upstream choice for content loaders, and is the
 * single place the experimental Expressive opt-in lives, so adopting the expressive spinner elsewhere is a
 * one-line swap to this and never a scattered `@OptIn`.
 *
 * This is deliberately NOT for tiny in-button "working..." states (16-24dp icon-swap spinners): the
 * morphing shape is designed for standalone/section loads, so those keep the standard
 * `CircularProgressIndicator`. Pull-to-refresh keeps the standard `PullToRefreshDefaults.Indicator` too
 * (also matching upstream).
 *
 * [color] maps to the indicator's `indicatorColor` and defaults to the theme color; pass a value only to
 * override (e.g. a specific container's on-color).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZemerLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    if (color == Color.Unspecified) {
        ContainedLoadingIndicator(modifier = modifier)
    } else {
        ContainedLoadingIndicator(modifier = modifier, indicatorColor = color)
    }
}
