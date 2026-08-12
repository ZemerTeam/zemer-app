package com.jtech.zemer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.menu.VideoQualityMenu

/**
 * The in-player video QUALITY pill: a compact over-media chip showing the active rung
 * ("Auto" / "1080p"), opening the standard [VideoQualityMenu] bottom sheet. Shared verbatim by the
 * inline art-slot surface (BottomStart — opposite the fullscreen button) and the fullscreen overlay
 * (TopEnd), so the two can never drift. Renders nothing when there is at most one rung (no choice).
 *
 * Styling is [VideoModePill]'s over-media family — the theme scrim + hairline white ring + white
 * content, with the shared [focusBorder] for D-pad reachability; the label crossfades through
 * [AnimatedContent] so a quality change (user pick or the rebuffer guard's downgrade) visibly
 * settles rather than snapping.
 */
@Composable
fun VideoQualitySelector(
    qualities: List<VideoQualityRung>,
    currentQuality: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Override the picker presentation. The default (null) opens the shared bottom-sheet menu via
    // LocalMenuState — correct in the portrait inline player. The FULLSCREEN overlay passes its own
    // handler to show a landscape-aware in-overlay panel instead: the root-hosted bottom sheet is a
    // portrait sheet rendered under an immersive landscape window, which fights orientation, insets
    // and z-order (the "very buggy" fullscreen menu).
    onOpen: (() -> Unit)? = null,
) {
    if (qualities.size < 2) return
    val menuState = LocalMenuState.current
    val autoLabel = stringResource(R.string.video_quality_auto)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The shared over-media chrome (OverMediaChrome.kt) — same family as VideoModePill.
        modifier = modifier
            .overMediaChrome(CircleShape)
            .focusBorder(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.video_quality),
            ) {
                if (onOpen != null) {
                    onOpen()
                } else {
                    menuState.show {
                        VideoQualityMenu(
                            qualities = qualities,
                            currentQuality = currentQuality,
                            onSelect = onSelect,
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.tune),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        AnimatedContent(
            targetState = if (currentQuality == VideoQualityLogic.AUTO) autoLabel else currentQuality,
            label = "video_quality_label",
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}
