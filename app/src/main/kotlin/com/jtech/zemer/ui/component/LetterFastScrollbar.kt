package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Fast scroller for a long alphabetically-sorted list (the Artists tab), in the Material 3 /
 * Contacts idiom: a thin rounded thumb hugging the trailing edge — faint while idle so it is
 * always discoverable, brighter while the list moves, accent-colored while dragged. Dragging the
 * thumb scrubs the whole list and floats a large bubble with the current index letter
 * ([letterFor], see [alphabetBucketOf]) beside the finger.
 *
 * The component is container-agnostic: the caller supplies the current [scrollProgress] (0..1) and
 * performs the actual jump in [onScrollToItem] (list vs grid, plus any header-item offset). The
 * thumb is thinner than the grid's edge gutter, so it never overlaps artwork or names, and ONLY
 * the thumb (plus a small slop zone around it) is touchable — taps and flings anywhere else on
 * the edge pass through to the rows.
 *
 * Deliberately touch-only (no focus treatment): it is a scrub accelerator over a list that is
 * itself fully D-pad navigable.
 */
@Composable
fun LetterFastScrollbar(
    itemCount: Int,
    scrollProgress: Float,
    listScrollInProgress: Boolean,
    letterFor: (Int) -> Char,
    onScrollToItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 32.dp,
    thumbHeight: Dp = 56.dp,
    thumbWidth: Dp = 6.dp,
    thumbEndPadding: Dp = 4.dp,
    grabSlop: Dp = 12.dp,
    bubbleSize: Dp = 44.dp,
) {
    // A one-item list cannot scrub anywhere; render nothing rather than a dead thumb.
    if (itemCount < 2) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var fingerY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        // The root has no pointer handling, so it is transparent to input — only the thumb's own
        // grab zone below intercepts touches.
        modifier = modifier
            .width(trackWidth)
            .fillMaxHeight()
    ) {
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val grabSlopPx = with(density) { grabSlop.toPx() }
        val maxThumbY = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)

        // The thumb CENTER tracks the finger, so the mapping anchors on the thumb's center travel
        // range — the top and bottom of the list are reachable without overshooting.
        fun indexAt(y: Float): Int {
            val fraction = ((y - thumbHeightPx / 2f) / maxThumbY).coerceIn(0f, 1f)
            return (fraction * (itemCount - 1)).roundToInt()
        }

        fun scrubTo(y: Float) {
            fingerY = y
            val index = indexAt(y)
            if (index != draggedIndex) {
                val previous = draggedIndex
                draggedIndex = index
                // Haptics per letter change, not per item — per item buzzes continuously on a
                // large catalog.
                if (previous < 0 || letterFor(previous) != letterFor(index)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onScrollToItem(index)
            }
        }

        val thumbY = if (isDragging) {
            (fingerY - thumbHeightPx / 2f).coerceIn(0f, maxThumbY)
        } else {
            scrollProgress.coerceIn(0f, 1f) * maxThumbY
        }

        // The grab zone: the thumb plus slop above/below. It is FROZEN in place for the duration
        // of a drag (the visual thumb chases the finger, but a gesture area moving under the
        // pointer would feed its own motion back into the drag deltas); local drag positions are
        // mapped to track space through the frozen anchor.
        var dragAnchorTopPx by remember { mutableFloatStateOf(0f) }
        val restingGrabTop = (thumbY - grabSlopPx).coerceIn(0f, (viewportHeightPx - thumbHeightPx - 2 * grabSlopPx).coerceAtLeast(0f))
        val grabTop = if (isDragging) dragAnchorTopPx else restingGrabTop
        Box(
            modifier = Modifier
                .offset { IntOffset(0, grabTop.roundToInt()) }
                .width(trackWidth)
                .height(thumbHeight + grabSlop * 2)
                .pointerInput(itemCount) {
                    // A key change mid-gesture (the artists flow updating while a finger is down)
                    // cancels this coroutine WITHOUT running onDragCancel, so the reset must also
                    // live in a finally — else the bubble stays frozen until the next touch.
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragAnchorTopPx = grabTop
                                isDragging = true
                                scrubTo(grabTop + offset.y)
                            },
                            onDragEnd = {
                                isDragging = false
                                draggedIndex = -1
                            },
                            onDragCancel = {
                                isDragging = false
                                draggedIndex = -1
                            },
                        ) { change, _ ->
                            scrubTo(dragAnchorTopPx + change.position.y)
                        }
                    } finally {
                        isDragging = false
                        draggedIndex = -1
                    }
                }
        )

        // Faint at rest (discoverable without polluting the edge), brighter while the list moves,
        // accent while dragged.
        val thumbColor by animateColorAsState(
            targetValue = when {
                isDragging -> MaterialTheme.colorScheme.secondary
                listScrollInProgress -> LocalContentColor.current.copy(alpha = 0.7f)
                else -> LocalContentColor.current.copy(alpha = 0.3f)
            },
            label = "fastScrollThumbColor",
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY.roundToInt()) }
                .padding(end = thumbEndPadding)
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(thumbColor),
        )

        // Floating preview of the current index letter, beside the finger, only while dragging.
        if (isDragging && draggedIndex in 0 until itemCount) {
            val bubblePx = with(density) { bubbleSize.toPx() }
            val bubbleGapPx = with(density) { 16.dp.toPx() }
            // The track is far narrower than the bubble, so plain size() would be clamped to the
            // track width (a squished pill). requiredSize escapes the constraint but then centers
            // the bubble in the clamped box — the x offset compensates for that shift so the
            // bubble's right edge lands one gap left of the track.
            val centeringShiftPx = (bubblePx - constraints.maxWidth) / 2f
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset {
                        IntOffset(
                            x = (-bubblePx - bubbleGapPx + centeringShiftPx).roundToInt(),
                            y = (fingerY - bubblePx / 2f)
                                .coerceIn(0f, viewportHeightPx - bubblePx)
                                .roundToInt(),
                        )
                    }
                    .requiredSize(bubbleSize)
                    // A real shadow: primaryContainer alone has almost no contrast against the
                    // dark theme background, so the bubble reads as a glitch without one.
                    .shadow(6.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letterFor(draggedIndex).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
