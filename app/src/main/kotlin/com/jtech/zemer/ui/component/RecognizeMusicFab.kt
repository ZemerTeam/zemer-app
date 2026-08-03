package com.jtech.zemer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R

/**
 * Floating action button that opens the "Recognize music" screen. Shown above the bottom navigation
 * bar on main screens when enabled (the `recognizeMusicFab` preference, default on). Kept as its own
 * component so `MainActivity` only wires placement/visibility; the look comes from the shared [ZemerFab].
 */
@Composable
fun RecognizeMusicFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZemerFab(
        icon = R.drawable.mic,
        contentDescription = stringResource(R.string.recognize_music),
        onClick = onClick,
        modifier = modifier,
    )
}
