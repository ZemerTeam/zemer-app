package com.jtech.zemer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R

/**
 * The Song/Video toggle, rendered as an **icon-only segmented pill** overlaid on a corner of the
 * artwork/video slot (see D7). It replaces the old text pill in `controlsContent`, which higher
 * display densities could clip. Living inside the art square, no downstream layout can cover it.
 *
 * Layout contract for the art slot (see [Thumbnail]): this pill sits at **TopStart**, the cast
 * button at TopEnd, and the fullscreen button at **BottomEnd** — diagonally opposite this pill so
 * they can never collide.
 *
 * Legibility over arbitrary artwork *and* over playing video comes from the dark scrim behind the
 * icons plus the accent fill on the selected segment — the same white-on-scrim treatment as the
 * fullscreen button. Both segments are `.focusable()` + accent-focus-bordered so the pill is
 * D-pad reachable inside the player pager.
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
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoModeSegment(
            icon = R.drawable.music_note,
            contentDescription = stringResource(R.string.song),
            selected = !isVideoMode,
            accentColor = accentColor,
            onClick = { onSelect(false) },
        )
        VideoModeSegment(
            icon = R.drawable.ondemand_video,
            contentDescription = stringResource(R.string.video),
            selected = isVideoMode,
            accentColor = accentColor,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun VideoModeSegment(
    @DrawableRes icon: Int,
    contentDescription: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) accentColor else Color.Transparent,
        label = "video_mode_pill_focus",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accentColor else Color.Transparent)
            .border(2.dp, borderColor, CircleShape)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp),
        )
    }
}
