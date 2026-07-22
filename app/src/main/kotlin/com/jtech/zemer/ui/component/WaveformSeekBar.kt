package com.jtech.zemer.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtech.zemer.playback.AudioEffectsEngine
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

/**
 * A live waveform / spectrum seekbar. The bar heights are driven by the [AudioEffectsEngine]
 * FFT spectrum (so they react to the actual audio), while the played/unplayed split and the
 * drag handling come from the Material3 [Slider] (accessibility, keyboard/d-pad, semantics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    spectrumFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.35f),
    trackHeight: Dp = 40.dp,
) {
    val spectrum by spectrumFlow.collectAsState()
    val progress = if (valueRange.endInclusive > 0f) {
        (value / valueRange.endInclusive).coerceIn(0f, 1f)
    } else {
        0f
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier.height(trackHeight),
        thumb = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(activeColor),
            )
        },
        track = { state: SliderState ->
            WaveformTrack(
                state = state,
                spectrum = spectrum,
                progress = progress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                trackHeight = trackHeight,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaveformTrack(
    state: SliderState,
    spectrum: FloatArray,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
) {
    val sliderValue = if (state.valueRange.endInclusive > 0f) {
        (state.value / state.valueRange.endInclusive).coerceIn(0f, 1f)
    } else {
        0f
    }
    val p = if (progress > 0f) progress else sliderValue

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        val barCount = spectrum.size.coerceAtLeast(1)
        val gapPx = 2.dp.toPx()
        val totalGap = gapPx * (barCount - 1)
        val barWidth = ((size.width - totalGap) / barCount).coerceAtLeast(1f)
        val maxH = size.height
        for (i in spectrum.indices) {
            val mag = spectrum[i].coerceIn(0f, 1f)
            val h = maxH * (0.12f + mag * 0.88f)
            val x = i * (barWidth + gapPx)
            val yTop = (size.height - h) / 2f
            val isPlayed = (i.toFloat() / barCount) <= p
            drawRoundRect(
                color = if (isPlayed) activeColor else inactiveColor,
                topLeft = Offset(x, yTop),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
