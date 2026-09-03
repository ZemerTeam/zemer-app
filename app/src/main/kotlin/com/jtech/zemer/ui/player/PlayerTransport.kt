package com.jtech.zemer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.jtech.zemer.R
import com.jtech.zemer.constants.PlayerBackgroundStyle
import com.jtech.zemer.constants.PlayerHorizontalPadding
import com.jtech.zemer.constants.SliderStyle
import com.jtech.zemer.ui.component.PlayerSliderTrack
import com.jtech.zemer.ui.theme.PlayerSliderColors
import com.jtech.zemer.ui.component.focusVisualsEnabled
import com.jtech.zemer.utils.makeTimeString
import me.saket.squiggles.SquigglySlider

/*
 * The player's transport pieces, shared by the full player and the lyrics screen (one implementation,
 * two hosts): the seek bar in the user's slider style (+ LIVE bar for station broadcasts) with its time
 * labels, and the spring-grow transport cluster (wide labelled play/pause flanked by circular skips).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    sliderStyle: SliderStyle,
    isStationBroadcast: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    accentColor: Color,
    textColor: Color,
    playerBackground: PlayerBackgroundStyle,
    useDarkTheme: Boolean,
    onSliderPositionChange: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = (sliderPosition ?: position).toFloat()
    val valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat())
    // A TAP fires onValueChange and onValueChangeFinished inside one frame, before recomposition delivers the new
    // [sliderPosition] parameter — a closure over the parameter would seek to the stale (null) value and do nothing,
    // while a drag happened to work only because it recomposes along the way. Keep the latest value in state that
    // the finish callback reads directly, so tap-to-seek and drag-to-seek behave the same.
    val latest = remember { mutableStateOf<Long?>(null) }
    val onValueChange: (Float) -> Unit = { latest.value = it.toLong(); onSliderPositionChange(it.toLong()) }
    val onValueChangeFinished = { (latest.value ?: sliderPosition)?.let(onSeek); latest.value = null; onSliderPositionChange(null) }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding - 8.dp)) {
            // A broadcast has no transport: the seek slider is replaced by the read-only LIVE bar.
            if (isStationBroadcast) StationLiveBar(position = position, duration = duration, accentColor = accentColor)
            else when (sliderStyle) {
                SliderStyle.DEFAULT -> Slider(
                    value = value, valueRange = valueRange, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
                    colors = PlayerSliderColors.defaultSliderColors(accentColor, playerBackground, useDarkTheme),
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp),
                )
                SliderStyle.SQUIGGLY -> SquigglySlider(
                    value = value, valueRange = valueRange, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
                    colors = PlayerSliderColors.squigglySliderColors(accentColor, playerBackground, useDarkTheme),
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp),
                    squigglesSpec = SquigglySlider.SquigglesSpec(amplitude = if (isPlaying) 2.dp else 0.dp, strokeWidth = 3.dp),
                )
                SliderStyle.SLIM -> Slider(
                    value = value, valueRange = valueRange, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
                    thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                    track = { sliderState -> PlayerSliderTrack(sliderState = sliderState, colors = PlayerSliderColors.slimSliderColors(accentColor, playerBackground, useDarkTheme)) },
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding - 8.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding + 4.dp),
        ) {
            Text(text = makeTimeString(sliderPosition ?: position), style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "", style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Spring-grow-on-press transport cluster: wide labelled play/pause flanked by circular skips. */
@Composable
fun PlayerTransportRow(
    isPlaying: Boolean,
    ended: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    accentColor: Color,
    playButtonContainerColor: Color,
    playButtonContentColor: Color,
    sideButtonContainerColor: Color,
    sideButtonContentColor: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isStationBroadcast: Boolean = false, // Zemer Station: play/stop only — skips are shown disabled and no-op (docs/stations)
    modifier: Modifier = Modifier,
) {
    val skipPrevInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val skipNextInteraction = remember { MutableInteractionSource() }
    val playPressed by playPauseInteraction.collectIsPressedAsState()
    // Cap the play button to the width left after the two skip buttons (≤60.dp each while pressed) plus the
    // two 16.dp gaps, so the cluster shrinks to fit instead of overflowing on narrow widths.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxPlayButtonWidth = (maxWidth - (60.dp * 2 + 16.dp * 2)).coerceAtLeast(72.dp)
        val playButtonWidth by animateDpAsState(
            targetValue = (if (playPressed) 164.dp else 150.dp).coerceAtMost(maxPlayButtonWidth),
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "play_width",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TransportSkipButton(iconRes = R.drawable.skip_previous, contentDescription = null, enabled = canSkipPrevious && !isStationBroadcast, interactionSource = skipPrevInteraction, accentColor = accentColor, containerColor = sideButtonContainerColor, contentColor = sideButtonContentColor, onSkip = onPrevious)
            val playButtonFocused = remember { mutableStateOf(false) }
            val playButtonBorderColor = animateColorAsState(targetValue = if (playButtonFocused.value && focusVisualsEnabled()) accentColor else Color.Transparent, label = "play_button_focus")
            FilledIconButton(
                onClick = onPlayPause,
                interactionSource = playPauseInteraction,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = playButtonContainerColor, contentColor = playButtonContentColor),
                modifier = Modifier
                    .width(playButtonWidth)
                    .height(68.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(3.dp, playButtonBorderColor.value, RoundedCornerShape(32.dp))
                    .focusable()
                    .onFocusChanged { playButtonFocused.value = it.isFocused },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painter = painterResource(when { ended -> R.drawable.replay; isPlaying -> R.drawable.pause; else -> R.drawable.play }), contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(when { ended -> R.string.replay; isPlaying -> R.string.pause; else -> R.string.play }), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TransportSkipButton(iconRes = R.drawable.skip_next, contentDescription = null, enabled = canSkipNext && !isStationBroadcast, interactionSource = skipNextInteraction, accentColor = accentColor, containerColor = sideButtonContainerColor, contentColor = sideButtonContentColor, onSkip = onNext)
        }
    }
}

@Composable
internal fun TransportSkipButton(
    iconRes: Int,
    contentDescription: String?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    accentColor: Color,
    containerColor: Color,
    contentColor: Color,
    onSkip: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    var focused by remember { mutableStateOf(false) }
    val pressed by interactionSource.collectIsPressedAsState()
    // Stop the long-press seek the moment the finger lifts: combinedClickable has no release
    // callback, so without this the repeat loop would keep seeking until the next tap.
    LaunchedEffect(pressed) {
        if (!pressed) {
            repeatJob?.cancel()
            repeatJob = null
        }
    }
    val borderColor by animateColorAsState(
        targetValue = if (focused && focusVisualsEnabled()) accentColor else Color.Transparent,
        label = "skip_focus",
    )
    val size by animateDpAsState(
        targetValue = if (pressed) 60.dp else 56.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "skip_size",
    )
    FilledTonalIconButton(
        // The button's own onClick is the live tap handler; the combinedClickable below adds
        // long-press-to-seek (its onClick mirrors this for the rare case it wins the gesture).
        onClick = {
            repeatJob?.cancel()
            onSkip()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(32.dp))
            .border(3.dp, borderColor, RoundedCornerShape(32.dp))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    repeatJob?.cancel()
                    onSkip()
                },
                onLongClick = {
                    repeatJob = coroutineScope.launch {
                        while (isActive) {
                            onSkip()
                            delay(200)
                        }
                    }
                },
            ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}
