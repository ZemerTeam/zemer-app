package com.jtech.zemer.ui.component

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * The shared ExoPlayer video surface for the status viewers: a full-bleed [PlayerView] bound to the
 * given [player]. Both status viewers use it with no controls (taps drive the story). One definition
 * so the two can't drift.
 *
 * Fill mode is ZOOM (fill the surface, crop the overflow), the WhatsApp/Instagram-stories behavior: a
 * tall/portrait status video fills the whole screen instead of being letterboxed into a thin strip that
 * left a black band for the caption/FAB to sit on. The overlays (segment bars, header, caption, FAB)
 * then sit ON the video with their own scrims. A video WIDER than the surface (a landscape post) switches
 * to FIT instead, so it is letterboxed rather than cropped to its middle ([statusVideoResizeMode]).
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
                val view = this
                fun applyFit(videoSize: VideoSize) {
                    val metrics = view.resources.displayMetrics
                    view.resizeMode = statusVideoResizeMode(
                        videoWidth = videoSize.width * videoSize.pixelWidthHeightRatio,
                        videoHeight = videoSize.height.toFloat(),
                        surfaceWidth = (view.width.takeIf { it > 0 } ?: metrics.widthPixels).toFloat(),
                        surfaceHeight = (view.height.takeIf { it > 0 } ?: metrics.heightPixels).toFloat(),
                    )
                }
                // Seed from a size the player already knows (a view recreated mid-video), re-decide on a
                // new video and on a surface resize (rotation, foldable, window change).
                applyFit(player.videoSize)
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) applyFit(player.videoSize)
                }
                // Kept in the view's tag so onRelease detaches it from the (longer-lived) player.
                tag = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = applyFit(videoSize)
                }.also(player::addListener)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        modifier = modifier,
        onRelease = { view -> (view.tag as? Player.Listener)?.let(player::removeListener) },
    )
}

/**
 * FIT for square/landscape media wider (by aspect) than the surface, else the stories ZOOM fill. Portrait
 * media always ZOOMs: a 9:16 post is still "wider" than a 9:20 phone and must not be letterboxed.
 * Unknown size -> ZOOM.
 */
internal fun statusVideoResizeMode(
    videoWidth: Float,
    videoHeight: Float,
    surfaceWidth: Float,
    surfaceHeight: Float,
): Int {
    if (videoWidth <= 0f || videoHeight <= 0f || surfaceWidth <= 0f || surfaceHeight <= 0f) {
        return AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    val videoAspect = videoWidth / videoHeight
    return if (videoAspect >= 1f && videoAspect > surfaceWidth / surfaceHeight) AspectRatioFrameLayout.RESIZE_MODE_FIT
    else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
