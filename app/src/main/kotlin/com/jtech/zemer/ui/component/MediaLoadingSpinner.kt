@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalPlayerAwareWindowInsets

/**
 * The ONE small loading spinner used OVER a card cover - the tap-to-play overlay while a tapped song /
 * video / episode resolves. It is the Material 3 Expressive [LoadingIndicator] (the same morphing shape
 * as the pull-to-refresh indicator), in its bare (un-contained) variant so it scales to any [size] and
 * reads on a dark scrim at small sizes where the contained chip would be too big.
 *
 * Rendered in a NEUTRAL [color] - the color of whatever it stands in for (the play icon / now-playing
 * equalizer white, an album track's `onBackground`) so a card's spinner is never a different color from
 * its playing animation - and NEVER the theme accent. The one place the experimental Expressive opt-in
 * lives for this spinner, so every card site is a single call, never a scattered `@OptIn`.
 *
 * For a section/content loader on a normal surface (Home pull-to-refresh, a loading section, the in-player
 * video buffering surface) use the contained, theme-colored [ZemerLoadingIndicator] instead.
 */
@Composable
fun MediaLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 24.dp,
) {
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color,
    )
}

/**
 * The ONE tap-to-play "preparing" overlay: a scrim ([scrimColor], clipped to [shape]) that dims the cover
 * with the centered [MediaLoadingSpinner] ([color]) on top, shown while a tapped item resolves. Shared by
 * the card thumbnail ([com.jtech.zemer.ui.component.ItemThumbnail]) and the video hero carousel so the two
 * can't drift. Fills its parent, so place it as the LAST child of the artwork box.
 */
@Composable
fun PreparingOverlay(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    scrimColor: Color = Color.Black.copy(alpha = ActiveBoxAlpha),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor, shape),
    ) {
        MediaLoadingSpinner(color = color)
    }
}

/**
 * The ONE expressive pull-to-refresh indicator (player-aware top-center placement), shared by the
 * Home screen and the whitelist browse scaffold so the look can't drift and the bare-LoadingIndicator
 * opt-in stays in this file (the R26 audit exemption). Place it as a direct child of the screen's
 * root [Box], AFTER the content so it draws on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.PullRefreshLoadingIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
) {
    PullToRefreshDefaults.LoadingIndicator(
        isRefreshing = isRefreshing,
        state = state,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    )
}
