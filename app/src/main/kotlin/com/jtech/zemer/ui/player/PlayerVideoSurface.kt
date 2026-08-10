package com.jtech.zemer.ui.player

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.jtech.zemer.LocalPlayerConnection

/**
 * The single `TextureView` host for video mode (I6: one player, one surface). Used by BOTH the inline
 * album-art placement ([Thumbnail]) and the fullscreen overlay ([PlayerVideoFullscreen]) — they are
 * mutually exclusive in composition ([com.jtech.zemer.playback.PlayerVideoUiLogic]), so at any instant
 * exactly one instance exists and owns the player's output.
 *
 * On enter it attaches its surface to the service player via `PlayerConnection.setVideoSurface`; on
 * dispose it detaches. Compose disposes a leaving effect BEFORE running an entering one, so the
 * inline→fullscreen (and back) handoff never lands on a detached surface. `keepScreenOn` keeps the
 * display awake while the view is attached (no window-flag juggling).
 *
 * It is a `TextureView`, NOT a `SurfaceView`, on purpose: the inline video sits inside the player
 * sheet, which paints an opaque background over the art slot. A `SurfaceView` composites on its own
 * separate surface behind that opaque background, so decoded frames are queued but never visible
 * (the first-device-run "black video, audio fine" bug). A `TextureView` renders into the normal view
 * hierarchy, so it layers correctly above the sheet background and below the fullscreen button.
 *
 * **Aspect is driven by the real decoded video size, not a fixed 16:9.** A bare `TextureView` scales
 * frames to fill its bounds, so a hardcoded 16:9 box stretches any non-16:9 source. DIRECT muxed
 * formats are 16:9 (so DIRECT is unchanged — the fallback IS 16:9), but the RELAY `&kind=video` 360p
 * transcode need not be, and was being stretched. We read [Player.Listener.onVideoSizeChanged]
 * (honoring `pixelWidthHeightRatio` for non-square pixels) and letterbox/pillarbox the surface to the
 * true aspect inside the caller's box, centered. The caller gives a fill box; this sizes within it.
 *
 * The background/gradient stays artwork-derived (PlayerBackground.kt) — video frames never feed it.
 */
@Composable
fun PlayerVideoSurface(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val surfaceView = remember { TextureView(context).apply { keepScreenOn = true } }

    // The video's display aspect (w:h). Defaults to 16:9 until the first frame size arrives, so DIRECT
    // (always 16:9) is visually unchanged and any non-16:9 relay video is corrected rather than stretched.
    var aspect by remember { mutableFloatStateOf(16f / 9f) }

    DisposableEffect(surfaceView) {
        playerConnection.setVideoSurface(surfaceView)
        // Clear only THIS view (order-independent handoff): if the inline↔fullscreen swap already
        // attached the incoming surface, this leaving dispose must not detach it. See clearVideoSurface.
        onDispose { playerConnection.clearVideoSurface(surfaceView) }
    }

    DisposableEffect(playerConnection) {
        val player = playerConnection.player
        fun apply(size: VideoSize) {
            val w = size.width * size.pixelWidthHeightRatio
            val h = size.height.toFloat()
            if (w > 0f && h > 0f) aspect = w / h
        }
        apply(player.videoSize) // seed from the size already known (e.g. surface re-attach)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) = apply(videoSize)
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { surfaceView },
            // matchHeightConstraintsFirst for tall (portrait) videos so the surface fits WITHIN the box
            // in both dimensions regardless of aspect, letterboxing/pillarboxing rather than overflowing.
            modifier = Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f),
        )
    }
}
