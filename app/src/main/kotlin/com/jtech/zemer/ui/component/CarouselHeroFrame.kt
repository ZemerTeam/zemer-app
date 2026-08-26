@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * The ONE fling behavior for the full-bleed hero carousels (Latest Releases + the video hero):
 * [CarouselDefaults.singleAdvanceFlingBehavior] with its DEFAULT spring snap. Single advance comes
 * from the behavior's internal PagerSnapDistance.atMost(1), NOT from the animation spec; the default
 * spring is deliberate - it consumes the fling's release velocity for a continuous finger-to-animation
 * hand-off and settles proportionally to the remaining distance. Do NOT pass a fixed-duration tween
 * snap here: a tween ignores that velocity and hitches on every swipe (the "choppy carousel" bug).
 */
@Composable
fun heroCarouselFlingBehavior(state: CarouselState): TargetedFlingBehavior =
    CarouselDefaults.singleAdvanceFlingBehavior(state)

/**
 * The shared frame for a full-bleed carousel HERO - the Latest Releases hero and the Trending/Featured
 * Videos hero render through this ONE definition so their D-pad focus ring, mask clip/border, cover
 * artwork and now-playing scrim can never drift.
 *
 * A [CarouselItemScope] extension so it can [maskClip]/[maskBorder] against the live item mask. It draws
 * the [thumbnailUrl] cover (cover-crop), the now-playing [PlayingIndicatorBox] scrim, and a [maskBorder]
 * focus ring shown only in a key-driven session ([focusVisualsEnabled]); everything on top - the title
 * overlay, badges, play buttons, the tap-to-play spinner - goes in [overlay].
 *
 * The two per-hero differences are parameters, not forks: [activeShape] morphs the cover + scrim to an
 * expressive silhouette while active (Latest Releases; null keeps [shape]), and [activePop] is the
 * become-active pop scale (1f = none).
 */
@Composable
fun CarouselItemScope.CarouselHeroFrame(
    thumbnailUrl: String?,
    contentDescription: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusLabel: String,
    activeShape: Shape? = null,
    activePop: Float = 1f,
    overlay: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val ringColor by animateColorAsState(
        targetValue = if (focused && focusVisualsEnabled()) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = focusLabel,
    )
    val scrimShape = activeShape ?: shape
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(shape)
            .maskBorder(BorderStroke(3.dp, ringColor), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = activePop; scaleY = activePop }
                .then(if (isActive && activeShape != null) Modifier.clip(activeShape) else Modifier),
        )
        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = activePop; scaleY = activePop }
                .background(Color.Black.copy(alpha = ActiveBoxAlpha), shape = scrimShape),
        )
        overlay()
    }
}
