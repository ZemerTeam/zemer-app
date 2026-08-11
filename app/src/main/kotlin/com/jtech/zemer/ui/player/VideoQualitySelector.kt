package com.jtech.zemer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.jtech.zemer.ui.component.focusBorder

/**
 * The in-player video QUALITY switcher: a compact over-media pill showing the active rung
 * ("Auto" / "1080p"), opening a dropdown of every rung the current video actually serves (the
 * decoder-capability-filtered ladder from VideoModeController) plus Auto. Shared verbatim by the
 * inline art-slot surface (BottomStart — opposite the fullscreen button) and the fullscreen overlay
 * (TopEnd), so the two can never drift. Renders nothing when there is at most one rung (no choice).
 *
 * Styling follows [VideoModePill]'s over-media idiom: theme scrim + hairline ring + white content,
 * with the shared [focusBorder] for D-pad reachability.
 */
@Composable
fun VideoQualitySelector(
    qualities: List<VideoQualityRung>,
    currentQuality: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (qualities.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.video_quality_auto)

    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .focusBorder(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.video_quality),
                ) { expanded = true }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Text(
                // Rung labels ("1080p") are server data, shown as-is; only Auto is localized.
                text = if (currentQuality == VideoQualityLogic.AUTO) autoLabel else currentQuality,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QualityChoiceItem(
                label = autoLabel,
                selected = currentQuality == VideoQualityLogic.AUTO,
            ) {
                expanded = false
                onSelect(VideoQualityLogic.AUTO)
            }
            qualities.forEach { rung ->
                QualityChoiceItem(
                    label = rung.label,
                    selected = currentQuality == rung.label,
                ) {
                    expanded = false
                    onSelect(rung.label)
                }
            }
        }
    }
}

@Composable
private fun QualityChoiceItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}
