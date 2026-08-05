package com.jtech.zemer.ui.player

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
 * The background/gradient stays artwork-derived (PlayerBackground.kt) — video frames never feed it.
 */
@Composable
fun PlayerVideoSurface(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val surfaceView = remember { TextureView(context).apply { keepScreenOn = true } }

    DisposableEffect(surfaceView) {
        playerConnection.setVideoSurface(surfaceView)
        // Clear only THIS view (order-independent handoff): if the inline↔fullscreen swap already
        // attached the incoming surface, this leaving dispose must not detach it. See clearVideoSurface.
        onDispose { playerConnection.clearVideoSurface(surfaceView) }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}
