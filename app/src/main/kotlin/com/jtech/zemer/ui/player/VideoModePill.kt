package com.jtech.zemer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.focusBorder

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
 * fullscreen button. Both segments carry the shared [focusBorder] treatment so the pill is
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
            // The theme's scrim token (the StatusStoryTopOverlay media-scrim pattern), never a literal.
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // The shared D-pad focus treatment (FocusBorder.kt) — never hand-rolled per component.
            .focusBorder(CircleShape)
            .background(if (selected) accentColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            // Selected sits on the accent fill -> its theme pair (onPrimary). Unselected sits on the
            // dark media scrim -> forced white, the shared StatusStoryTopOverlay over-media idiom.
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp),
        )
    }
}
