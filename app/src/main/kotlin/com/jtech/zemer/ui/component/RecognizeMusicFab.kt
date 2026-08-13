package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R

/**
 * Floating action button that opens the "Recognize music" screen. Shown only on the Home tab's
 * Music chip when enabled (the `recognizeMusicFab` preference, default on). Kept as its own
 * component so `MainActivity` only wires placement/visibility; the look comes from the shared
 * [ZemerFab], COMPACTED (owner ask): the standard 56dp FAB read too heavy floating over content,
 * so this one is 46dp with the shape and colors unchanged.
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
        modifier = modifier.size(46.dp),
    )
}
