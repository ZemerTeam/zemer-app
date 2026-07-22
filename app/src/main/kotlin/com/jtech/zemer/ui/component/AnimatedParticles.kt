package com.jtech.zemer.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight reactive particle field that pulses with the live playback amplitude (from
 * [com.jtech.zemer.playback.AudioEffectsEngine]). Particles drift upward and their size/opacity
 * scale with the current amplitude, giving the "animated thumbnail" feel Namida has.
 */
@Composable
fun AnimatedParticles(
    amplitudeFlow: StateFlow<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    particleCount: Int = 30,
) {
    val amplitude by amplitudeFlow.collectAsState()
    val transition = rememberInfiniteTransition(label = "particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 6000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleProgress",
    )

    val a = amplitude.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        for (i in 0 until particleCount) {
            val seed = i * 12.9898f
            val x = (abs(sin(seed)) * size.width)
            val speed = 0.04f + (i % 5) * 0.012f
            val baseY = (abs(cos(seed * 1.7f)))
            var y = ((baseY - progress * speed) % 1f)
            if (y < 0f) y += 1f
            val drawY = y * size.height
            val baseR = (2f + (i % 4) * 1.5f)
            val radius = baseR + a * 10f
            val alpha = (0.08f + a * 0.5f) * (1f - y)
            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0f, 0.6f)),
                radius = radius,
                center = Offset(x, drawY),
            )
        }
    }
}
