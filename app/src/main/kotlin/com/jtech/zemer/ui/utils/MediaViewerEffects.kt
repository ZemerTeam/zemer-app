package com.jtech.zemer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.jtech.zemer.LocalPlayerConnection

/**
 * Silence the music player while a full-screen media viewer is on screen and resume it on close if it
 * was playing - and, while casting, pause/resume the receiver and route volume to the local video
 * (the VideoPlayerScreen pattern). Shared by the live story viewer and the saved-status viewer so the
 * pause/resume behaviour can't drift between them.
 */
@Composable
fun PauseMusicWhileActive() {
    val playerConnection = LocalPlayerConnection.current
    DisposableEffect(playerConnection) {
        val wasPlaying = playerConnection?.isPlaying?.value == true
        playerConnection?.player?.pause()
        playerConnection?.setVideoPlaybackActive(true)
        val pausedCast = playerConnection?.pauseCastForVideo() == true
        onDispose {
            playerConnection?.setVideoPlaybackActive(false)
            playerConnection?.resumeCastAfterVideo(pausedCast)
            if (wasPlaying) playerConnection?.player?.play()
        }
    }
}
