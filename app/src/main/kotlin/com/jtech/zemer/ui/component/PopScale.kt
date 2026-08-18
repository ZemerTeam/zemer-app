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
        scale.popOnce()
    }
    return scale.value
}

/**
 * Like [rememberPopScale], but pops ONLY on the rising edge - when [active] goes from false to true
 * (a card BECOMING the currently-playing item). A true->false change (deactivation) does not pop, so a
 * track change bounces only the newly-active card, never the one it just left. The first composition
 * never pops regardless of the initial value.
 *
 * Use this for "pops as it becomes active" affordances; [rememberPopScale] (pop on ANY change) is for a
 * control whose every toggle should bounce.
 */
@Composable
fun rememberActivationPopScale(active: Boolean, key: Any? = Unit): Float {
    val scale = remember { Animatable(1f) }
    // [key] identifies the item. In an INDEX-keyed carousel a composition slot is reused for different
    // items as it scrolls, so reset the rising-edge tracking (to the current [active], never popping)
    // whenever the item at this slot changes - else the now-playing item scrolling into a slot fires a
    // spurious "became active" pop.
    var wasActive by remember(key) { mutableStateOf(active) }
    LaunchedEffect(active, key) {
        val rising = active && !wasActive
        wasActive = active
        if (rising) scale.popOnce()
    }
    return scale.value
}

/** The shared spring: a quick jump to 1.3x, then a bouncy settle back to 1. */
private suspend fun Animatable<Float, *>.popOnce() {
    animateTo(1.3f, spring(stiffness = Spring.StiffnessHigh))
    animateTo(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    )
}
