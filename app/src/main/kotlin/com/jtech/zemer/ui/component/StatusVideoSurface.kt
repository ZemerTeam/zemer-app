package com.jtech.zemer.ui.component

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The shared ExoPlayer video surface for the status viewers: a full-bleed [PlayerView] bound to the
 * given [player]. The live story viewer uses it with no controls (taps drive the story); the saved
 * viewer enables controls for scrubbing. One definition so the two can't drift.
 *
 * Fill mode is ZOOM (fill the surface, crop the overflow), the WhatsApp/Instagram-stories behavior: a
 * tall/portrait status video fills the whole screen instead of being letterboxed into a thin strip that
 * left a black band for the caption/FAB to sit on. The overlays (segment bars, header, caption, FAB)
 * then sit ON the video with their own scrims.
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
                // Kill the transport controls, their auto-show, and the buffering spinner BEFORE binding
                // the player, so none of them flash when a new video is prepared (the story taps drive
                // playback; a brief play-button/seek-bar or buffering ring is not wanted).
                this.useController = useController
                controllerAutoShow = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        modifier = modifier,
    )
}
