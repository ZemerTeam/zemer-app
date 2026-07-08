package com.jtech.zemer.ui.player

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.jtech.zemer.LocalPlayerConnection

/**
 * The single `SurfaceView` host for video mode (I6: one player, one surface). Used by BOTH the inline
 * album-art placement ([Thumbnail]) and the fullscreen overlay ([PlayerVideoFullscreen]) — they are
 * mutually exclusive in composition ([com.jtech.zemer.playback.PlayerVideoUiLogic]), so at any instant
 * exactly one instance exists and owns the player's output.
 *
 * On enter it attaches its surface to the service player via `PlayerConnection.setVideoSurface`; on
 * dispose it detaches. Compose disposes a leaving effect BEFORE running an entering one, so the
 * inline→fullscreen (and back) handoff never lands on a detached surface. `keepScreenOn` keeps the
 * display awake while the view is attached (no window-flag juggling).
 *
 * The background/gradient stays artwork-derived (PlayerBackground.kt) — video frames never feed it.
 */
@Composable
fun PlayerVideoSurface(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val surfaceView = remember { SurfaceView(context).apply { keepScreenOn = true } }

    DisposableEffect(surfaceView) {
        playerConnection.setVideoSurface(surfaceView)
        onDispose { playerConnection.setVideoSurface(null) }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}
