package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * A NON-consuming expressive press bounce for shared components whose click is supplied by the caller
 * (the base [GridItem] / [ListItem]): it watches touch-down without consuming it - so the caller's
 * `combinedClickable` still fires the tap - and springs the element down to [pressedScale] while held,
 * settling back with the expressive `MediumBouncy` spring on release.
 *
 * Applied once inside each shared card/row, so every item that renders through them (home, search,
 * library, playlists) bounces on tap with no per-call-site edits. Like [rememberPopScale], the animation
 * is driven by an [Animatable] whose value is read ONLY inside the `graphicsLayer` draw lambda - so a
 * press never recomposes the item (only the layer redraws), which matters in long scrolling grids. It's a
 * pure draw-layer transform on the visual only; the touch target and layout are unchanged, and D-pad
 * focus (which never generates a pointer down) is unaffected.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.94f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(pressedScale) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    scale.animateTo(
                        targetValue = pressedScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
                waitForUpOrCancellation()
                scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    )
                }
            }
        }
}
