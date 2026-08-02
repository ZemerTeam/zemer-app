package com.jtech.zemer.ui.component

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The shared ExoPlayer video surface for the status viewers: a fit-scaled [PlayerView] bound to the
 * given [player]. The live story viewer uses it with no controls (taps drive the story); the saved
 * viewer enables controls for scrubbing. One definition so the two can't drift.
 */
@Composable
fun StatusVideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    useController: Boolean = false,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                this.useController = useController
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier,
    )
}
