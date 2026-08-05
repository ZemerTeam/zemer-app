package com.jtech.zemer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.focusBorder

private val SegmentSize = 28.dp
private val SegmentGap = 3.dp
private val ThumbPadding = 3.dp

/**
 * The Song/Video toggle, rendered as an **icon-only segmented pill** overlaid on a corner of the
 * artwork/video slot (see D7). It replaces the old text pill in `controlsContent`, which higher
 * display densities could clip. Living inside the art square, no downstream layout can cover it.
 *
 * Layout contract for the art slot (see [Thumbnail]): this pill sits at **TopStart**, the cast
 * button at TopEnd, and the fullscreen button at **BottomEnd** — diagonally opposite this pill so
 * they can never collide.
 *
 * Selection is an accent **thumb that slides** between the two segments (a gentle spring), with the
 * icon tints crossfading — the M3 connected-button feel without any Expressive-only API. Legibility
 * over arbitrary artwork *and* over playing video comes from the theme-scrim behind the icons plus
 * the accent thumb — the same white-on-scrim treatment as the fullscreen button. Both segments carry
 * the shared [focusBorder] treatment so the pill is D-pad reachable inside the player pager.
 *
 * Visibility is decided by the caller (`videoModeAvailable` — the single source of truth that
 * already encodes blocked/casting/rendition gating); this composable never re-derives it.
 */
@Composable
fun VideoModePill(
    isVideoMode: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    // The thumb slides from the Song slot (0) to the Video slot (one segment + gap across).
    val thumbOffset by animateDpAsState(
        targetValue = if (isVideoMode) SegmentSize + SegmentGap else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "video_mode_thumb",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            // The theme's scrim token (the StatusStoryTopOverlay media-scrim pattern), never a literal.
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            // A hairline ring lifts the pill off both artwork and video (the over-media white idiom).
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .padding(ThumbPadding),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(SegmentSize)
                .clip(CircleShape)
                .background(accentColor),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            VideoModeSegment(
                icon = R.drawable.music_note,
                contentDescription = stringResource(R.string.song),
                selected = !isVideoMode,
                onClick = { onSelect(false) },
            )
            VideoModeSegment(
                icon = R.drawable.ondemand_video,
                contentDescription = stringResource(R.string.video),
                selected = isVideoMode,
                onClick = { onSelect(true) },
            )
        }
    }
}

@Composable
private fun VideoModeSegment(
    @DrawableRes icon: Int,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Selected sits on the sliding accent thumb -> its theme pair (onPrimary). Unselected sits on
    // the dark media scrim -> forced white, the shared StatusStoryTopOverlay over-media idiom.
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.65f),
        label = "video_mode_tint",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SegmentSize)
            // The shared D-pad focus treatment (FocusBorder.kt) — never hand-rolled per component.
            .focusBorder(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // The sliding thumb IS the selection feedback; a ripple under it doubles up.
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
