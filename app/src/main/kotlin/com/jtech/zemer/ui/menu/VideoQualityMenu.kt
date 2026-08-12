package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData

/**
 * The video-quality picker, presented as the app's standard menu bottom sheet (section-11 grouped
 * [Material3MenuGroup] cards — never a floating dropdown): Auto first, then every rung the current
 * video serves, high→low. The selected row is lifted onto `secondaryContainer` with a primary check
 * (the onboarding selected-card idiom) so the active choice reads at a glance; each rung carries its
 * real resolution as the supporting line. Shown via `LocalMenuState` from [VideoQualitySelector]'s
 * pill — both the inline art slot and the fullscreen overlay open this one sheet.
 */
@Composable
fun VideoQualityMenu(
    qualities: List<VideoQualityRung>,
    currentQuality: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Text(
            text = stringResource(R.string.video_quality),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
        )
        Material3MenuGroup(
            items = buildList {
                add(
                    qualityChoice(
                        label = stringResource(R.string.video_quality_auto),
                        description = stringResource(R.string.video_quality_auto_description),
                        selected = currentQuality == VideoQualityLogic.AUTO,
                    ) {
                        onSelect(VideoQualityLogic.AUTO)
                        onDismiss()
                    },
                )
                qualities.forEach { rung ->
                    add(
                        qualityChoice(
                            label = rung.label,
                            description = stringResource(
                                R.string.video_quality_resolution, rung.width, rung.height,
                            ),
                            selected = currentQuality == rung.label,
                        ) {
                            onSelect(rung.label)
                            onDismiss()
                        },
                    )
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun qualityChoice(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
): Material3MenuItemData =
    Material3MenuItemData(
        title = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        description = { Text(description) },
        trailingContent = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        // The selected row lifts onto secondaryContainer (the shared onboarding selected-card fill).
        cardColors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            null
        },
        onClick = onClick,
    )
