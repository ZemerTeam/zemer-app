package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * `combinedClickable` + an expressive spring "bounce": the element dips to ~0.94 while pressed and
 * springs back (with a little overshoot) on release, giving every tap a tactile Material 3 bounce. A
 * drop-in replacement for `Modifier.combinedClickable` at card / row sites - the scale is a pure
 * draw-layer transform, so layout and click behaviour are unchanged.
 */
fun Modifier.bounceClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.90f,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Same expressive spring Metrolist uses for its per-site button bounce (MediumBouncy / Medium);
    // [pressedScale] is the dip depth - smaller = bolder (Metrolist uses 0.7 on buttons, we default a
    // gentler 0.9 for whole cards/rows).
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bounce_click",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

/**
 * The same expressive press bounce, but as a NON-consuming observer for shared components whose click is
 * supplied by the caller (the base `GridItem` / `ListItem`): it watches touch-down without consuming it
 * (so the caller's `combinedClickable` still fires the tap) and springs the element to [pressedScale]
 * while held, settling back on release. Applied once inside the shared card/row, so every item that
 * renders through them bounces app-wide - no per-call-site edits. The scale is a pure draw-layer
 * transform on the visual only; the touch target and layout are unchanged.
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
