package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A NON-consuming expressive press bounce for shared components whose click is supplied by the caller
 * (the base [GridItem] / [ListItem]): it watches touch-down without consuming it - so the caller's
 * `combinedClickable` still fires the tap - and springs the element down to [pressedScale] while held,
 * settling back with the expressive `MediumBouncy` spring on release.
 *
 * Applied once inside each shared card/row, so every item that renders through them (home, search,
 * library, playlists) bounces on tap with no per-call-site edits. The scale is a pure draw-layer
 * transform on the visual only; the touch target and layout are unchanged, and D-pad focus (which
 * never generates a pointer down) is unaffected.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.94f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press_bounce",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}
