package com.jtech.zemer.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A calm, one-shot title marquee: sit still for a moment, glide through the overflow once at a gentle
 * speed, then stop. Unlike the default [basicMarquee] (immediate, infinite loop), it reads as a hint
 * that there is more text rather than constant motion - the right feel for a column of list rows.
 *
 * Titles that already fit are unaffected (a marquee only animates on overflow). This is the shared
 * source for the now-playing/mini-player titles and the podcast list rows/cards so they can't drift.
 *
 * [focused] is the owning row/card's D-pad focus state. A one-shot glide fires ~once after the row
 * first composes, so a D-pad/TV user who navigates onto a long title later would otherwise never see
 * it scroll (against the full-D-pad-navigability mandate); passing focus here re-arms the glide
 * promptly on every focus GAIN. The marquee node restarts whenever its params change, so the re-arm
 * is pure param plumbing - see [gentleMarqueeParams] (JVM-tested) for the exact rules, including why
 * a focus LOSS must DISABLE the glide rather than fall back to the resting params. Touch sessions
 * never focus a row, so the default stays the calm appear-once glide - no change there.
 */
@Composable
fun Modifier.gentleMarquee(focused: Boolean = false): Modifier {
    var everFocused by remember { mutableStateOf(false) }
    if (focused && !everFocused) {
        // Latch after the frame lands - the focused branch already governs this frame's params.
        SideEffect { everFocused = true }
    }
    val params = gentleMarqueeParams(focused = focused, everFocused = everFocused)
    return basicMarquee(
        iterations = params.iterations,
        initialDelayMillis = params.initialDelayMillis,
        velocity = 30.dp,
    )
}

/** Animation params for [gentleMarquee]; a pure holder so the decision below is JVM-testable. */
data class GentleMarqueeParams(val iterations: Int, val initialDelayMillis: Int)

/**
 * The pure param spec behind [gentleMarquee]. The marquee node restarts its animation on ANY param
 * change (verified against Compose Foundation's MarqueeModifierNode.update), which is both the
 * re-arm mechanism and the trap the states below are shaped around:
 *
 * - Never focused: one glide after a long settle - the calm appear-once default (and the only state
 *   a touch session ever sees; its params never change, so it never restarts).
 * - Focused: one glide after a short settle. Reached from either other state it is always a param
 *   change, so every focus gain replays the glide promptly.
 * - Focus lost after a visit: iterations = 0 (basicMarquee's documented "no animation" - the node
 *   restarts but runs nothing, snapping the title back to its clipped start). Falling back to the
 *   resting params instead would BE a param change and replay the glide on every row a D-pad user
 *   leaves - a trailing cascade of gliding titles behind the cursor.
 */
fun gentleMarqueeParams(focused: Boolean, everFocused: Boolean): GentleMarqueeParams = when {
    focused -> GentleMarqueeParams(iterations = 1, initialDelayMillis = 600)
    everFocused -> GentleMarqueeParams(iterations = 0, initialDelayMillis = 600)
    else -> GentleMarqueeParams(iterations = 1, initialDelayMillis = 3000)
}
