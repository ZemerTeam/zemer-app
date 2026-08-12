package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.playback.VideoQualityLogic
import com.jtech.zemer.playback.VideoQualityRung
import com.jtech.zemer.ui.component.OnboardingChoiceCard
import com.jtech.zemer.ui.component.PreferenceGroupTitle

/**
 * The video-quality picker sheet, built ENTIRELY from shared components so it reads as the app:
 * [PreferenceGroupTitle] (the standard group heading) over a stack of [OnboardingChoiceCard]s (the
 * shared radio-select card — onboardingCardColors selected fill, filled radio, D-pad focusBorder,
 * all built in). Auto first with its supporting line, then every rung the current video serves,
 * high→low, each carrying its real resolution. Shown via `LocalMenuState` from
 * [com.jtech.zemer.ui.player.VideoQualitySelector]'s pill — the inline art slot and the fullscreen
 * overlay open this one sheet.
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
            .padding(bottom = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // The group title brings its own standard 16dp padding — the settings-screen layout.
        PreferenceGroupTitle(title = stringResource(R.string.video_quality))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingChoiceCard(
                selected = currentQuality == VideoQualityLogic.AUTO,
                title = stringResource(R.string.video_quality_auto),
                description = stringResource(R.string.video_quality_auto_description),
                onSelect = {
                    onSelect(VideoQualityLogic.AUTO)
                    onDismiss()
                },
            )
            qualities.forEach { rung ->
                OnboardingChoiceCard(
                    selected = currentQuality == rung.label,
                    title = rung.label,
                    description = stringResource(R.string.video_quality_resolution, rung.width, rung.height),
                    onSelect = {
                        onSelect(rung.label)
                        onDismiss()
                    },
                )
            }
        }
    }
}
