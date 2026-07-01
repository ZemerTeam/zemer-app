package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Letter fast-scroll strip for a long alphabetically-sorted list (the Artists tab). Renders the
 * letters actually present in the data (see [alphabetIndexOf]) down the trailing edge; tap or drag
 * to jump, with a floating bubble showing the active letter while dragging.
 *
 * The strip shows at most the letters that fit ([thinAlphabetIndex]), but a drag maps the touch
 * fraction through the FULL [entries] list, so buckets hidden by thinning are still reachable
 * between the visible letters. [onSelect] receives the chosen entry; the caller owns the scroll
 * (list vs grid) and any header-item offset.
 */
@Composable
fun AlphabetScrollbar(
    entries: List<AlphabetIndexEntry>,
    onSelect: (AlphabetIndexEntry) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 28.dp,
    rowHeight: Dp = 14.dp,
    bubbleSize: Dp = 52.dp,
) {
    // A one-letter index cannot navigate anywhere; render nothing rather than a dead strip.
    if (entries.size < 2) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var activeIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .width(trackWidth)
            .fillMaxHeight()
    ) {
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val maxRows = max(1, (viewportHeightPx / rowHeightPx).toInt())
        val displayEntries = remember(entries, maxRows) { thinAlphabetIndex(entries, maxRows) }
        val contentHeightPx = displayEntries.size * rowHeightPx
        val contentTopPx = (viewportHeightPx - contentHeightPx) / 2f

        // Maps a touch y to an entry of the FULL index. The visible letters are evenly thinned
        // from the full list, so the fraction-based mapping stays aligned with what is drawn.
        fun entryIndexAt(y: Float): Int {
            val fraction = ((y - contentTopPx) / contentHeightPx).coerceIn(0f, 1f)
            return (fraction * entries.size).toInt().coerceAtMost(entries.size - 1)
        }

        fun selectAt(y: Float) {
            fingerY = y
            val index = entryIndexAt(y)
            if (index != activeIndex) {
                activeIndex = index
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect(entries[index])
            }
        }

        Column(
            // The gesture area is the FULL track, not just the centered letters: the empty
            // stretch above/below them must still scrub (entryIndexAt clamps), and track-level
            // handlers keep every y in a single coordinate space.
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(entries, contentTopPx) {
                    detectTapGestures { offset ->
                        selectAt(offset.y)
                        activeIndex = -1
                    }
                }
                .pointerInput(entries, contentTopPx) {
                    // A key change mid-gesture (the artists flow updating while a finger is down)
                    // cancels this coroutine WITHOUT running onDragCancel, so the reset must also
                    // live in a finally — else the bubble stays frozen until the next touch.
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                selectAt(offset.y)
                            },
                            onDragEnd = {
                                isDragging = false
                                activeIndex = -1
                            },
                            onDragCancel = {
                                isDragging = false
                                activeIndex = -1
                            },
                        ) { change, _ ->
                            selectAt(change.position.y)
                        }
                    } finally {
                        isDragging = false
                        activeIndex = -1
                    }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val activeLetter = entries.getOrNull(activeIndex)?.letter
            displayEntries.forEach { entry ->
                val isActive = isDragging && entry.letter == activeLetter
                Text(
                    text = entry.letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else null,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(trackWidth)
                        .height(rowHeight),
                )
            }
        }

        // Floating preview of the active letter, beside the finger, only while dragging (a tap
        // jumps instantly and needs no preview).
        if (isDragging && activeIndex in entries.indices) {
            val bubblePx = with(density) { bubbleSize.toPx() }
            val bubbleGapPx = with(density) { 12.dp.toPx() }
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset {
                        IntOffset(
                            x = (-bubblePx - bubbleGapPx).roundToInt(),
                            y = (fingerY - bubblePx / 2f)
                                .coerceIn(0f, viewportHeightPx - bubblePx)
                                .roundToInt(),
                        )
                    }
                    .size(bubbleSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entries[activeIndex].letter.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
