package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * A one-shot bouncy "pop" scale for toggle controls: whenever [state] changes (after the first
 * composition) the returned scale springs up and settles back to 1, giving a like / shuffle / repeat
 * tap an expressive Material 3 bounce. The initial value is skipped, so opening a screen never pops.
 *
 * Apply as `Modifier.graphicsLayer { val s = rememberPopScale(state); scaleX = s; scaleY = s }` (read
 * the value in the composable and set it on the layer) - it's a pure draw transform, so it never
 * affects layout or the control's behaviour.
 */
@Composable
fun rememberPopScale(state: Any?): Float {
    val scale = remember { Animatable(1f) }
    var seenFirst by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!seenFirst) {
            seenFirst = true
            return@LaunchedEffect
        }
        scale.animateTo(1.3f, spring(stiffness = Spring.StiffnessHigh))
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    return scale.value
}
