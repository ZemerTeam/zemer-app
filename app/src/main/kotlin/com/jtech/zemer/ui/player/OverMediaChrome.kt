package com.jtech.zemer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The ONE over-media chrome treatment for controls floating on artwork/video — the theme scrim fill
 * plus an optional hairline white ring that lifts the control off arbitrary frames. [VideoModePill],
 * [VideoQualitySelector] and the [PlayerVideoSurface] buffering ring all render through this so the
 * over-media family cannot drift (per-site copies of the scrim/ring literals are exactly how it
 * would). Content on top follows the over-media idiom: forced white, accent only for selection.
 */
@Composable
fun Modifier.overMediaChrome(
    shape: Shape,
    scrimAlpha: Float = OverMediaScrimAlpha,
    ring: Boolean = true,
): Modifier {
    val base = clip(shape).background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
    return if (ring) base.border(1.dp, Color.White.copy(alpha = OverMediaRingAlpha), shape) else base
}

/** The standard scrim opacity for interactive over-media pills. */
const val OverMediaScrimAlpha = 0.55f

/** A lighter scrim for passive over-media chrome (the buffering ring's backing circle). */
const val OverMediaPassiveScrimAlpha = 0.45f

private const val OverMediaRingAlpha = 0.15f
