package com.jtech.zemer.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A calm, one-shot title marquee: sit still for a moment, glide through the overflow once at a gentle
 * speed, then stop. Unlike the default [basicMarquee] (immediate, infinite loop), it reads as a hint
 * that there is more text rather than constant motion - the right feel for a column of list rows.
 *
 * Titles that already fit are unaffected (a marquee only animates on overflow). This is the shared
 * source for the now-playing/mini-player titles and the podcast list rows so they can't drift.
 */
fun Modifier.gentleMarquee(): Modifier =
    this.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
