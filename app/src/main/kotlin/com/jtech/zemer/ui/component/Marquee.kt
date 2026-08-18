package com.jtech.zemer.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Set true by [RefocusableMarqueeTitle] around a focused row/card's title so [gentleMarquee] can
 * re-arm its one-shot glide promptly the moment a D-pad/TV user focuses the row. Everywhere else
 * (now-playing / mini-player titles, and every touch session, where a row is never focused) it stays
 * false, so the calm appear-once behavior is unchanged.
 */
val LocalMarqueeTitleFocused = compositionLocalOf { false }

/**
 * A calm, one-shot title marquee: sit still for a moment, glide through the overflow once at a gentle
 * speed, then stop. Unlike the default [basicMarquee] (immediate, infinite loop), it reads as a hint
 * that there is more text rather than constant motion - the right feel for a column of list rows.
 *
 * Titles that already fit are unaffected (a marquee only animates on overflow). This is the shared
 * source for the now-playing/mini-player titles and the podcast list rows so they can't drift.
 *
 * Wrap a focusable row/card's title in [RefocusableMarqueeTitle] to re-arm the glide on focus.
 */
@Composable
fun Modifier.gentleMarquee(): Modifier =
    this.basicMarquee(
        iterations = 1,
        initialDelayMillis = if (LocalMarqueeTitleFocused.current) 600 else 3000,
        velocity = 30.dp,
    )

/**
 * Wraps a [gentleMarquee] title so the one-shot glide RE-ARMS each time the row/card gains focus.
 *
 * A one-shot marquee fires ~once after the row first composes and then snaps back to the clipped
 * start, so a D-pad/TV user who navigates onto a long title later never sees it scroll (against the
 * full-D-pad-navigability mandate). Passing the row's own [focused] state here re-keys the title on
 * every focus gain (replaying the glide) and shortens its settle delay so the re-arm is prompt.
 * Touch sessions never focus a row, so [focused] stays false and the behavior is the calm
 * appear-once glide - no change there.
 *
 * The caller supplies the actual `Text(..., Modifier.gentleMarquee())` as [content]; this owns only
 * the focus re-arm so the row/card composables don't each re-roll it.
 */
@Composable
fun RefocusableMarqueeTitle(focused: Boolean, content: @Composable () -> Unit) {
    var generation by remember { mutableIntStateOf(0) }
    LaunchedEffect(focused) { if (focused) generation++ }
    key(generation) {
        CompositionLocalProvider(LocalMarqueeTitleFocused provides focused) {
            content()
        }
    }
}
