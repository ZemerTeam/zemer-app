package com.jtech.zemer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.PlayerVideoUiLogic

/**
 * Picture-in-picture for video mode: the app shrinks to a floating video window when the user leaves
 * it (Home / recents) while a video is showing. Entry is the platform's own auto-enter on API 31+
 * ([setVideoPipAutoEnter], armed exactly while video mode is on - it fires only for Home/recents,
 * never for an Activity we launch); below that `MainActivity.onUserLeaveHint` calls [enterVideoPip]
 * under [PlayerVideoUiLogic.shouldEnterPipOnLeave]. In the window [VideoPipOverlay] is the one
 * surface owner; closing the window stops the Activity, whose existing onStop revert returns
 * playback to audio.
 */
private val VIDEO_PIP_ASPECT = Rational(16, 9)

/** Whether the platform decides PiP entry itself (auto-enter, API 31+). */
val pipAutoEnterSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** False on some low-RAM and TV builds, where PiP is not offered at all. */
private val Activity.pipAvailable: Boolean
    get() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

private fun videoPipParams(autoEnter: Boolean): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(VIDEO_PIP_ASPECT)
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter) }
        .build()

/** Enters the PiP window now (the pre-31 path); false when the device or the user's per-app PiP setting refuses. */
fun Activity.enterVideoPip(): Boolean =
    pipAvailable && runCatching { enterPictureInPictureMode(videoPipParams(autoEnter = false)) }.getOrDefault(false)

/**
 * API 31+: arms or disarms the platform's auto-enter, so leaving via Home/recents shrinks the app into
 * PiP exactly while [enabled] (video mode on). No-op below 31, where onUserLeaveHint drives entry.
 */
fun Activity.setVideoPipAutoEnter(enabled: Boolean) {
    if (!pipAutoEnterSupported || !pipAvailable) return
    runCatching { setPictureInPictureParams(videoPipParams(autoEnter = enabled)) }
}

/** Whether the hosting Activity is currently in the PiP window. */
@Composable
fun rememberIsInPipMode(): Boolean {
    val activity = LocalContext.current as? ComponentActivity ?: return false
    var inPip by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * The PiP window's content, drawn over the whole Activity while [inPip]: the video surface in video
 * mode (the one surface owner - inline and fullscreen yield, [PlayerVideoUiLogic.showPipVideo]),
 * else the current cover art ([PlayerVideoUiLogic.showPipArtwork]) - video mode is per-play, so a
 * track change, error or cast revert inside the window must show the audio that is still playing,
 * never the app's regular UI shrunk into a thumbnail-sized window.
 */
@Composable
fun VideoPipOverlay(playerConnection: PlayerConnection, isVideoMode: Boolean, inPip: Boolean) {
    if (!inPip) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        if (PlayerVideoUiLogic.showPipVideo(isVideoMode, inPip)) {
            CompositionLocalProvider(LocalPlayerConnection provides playerConnection) {
                PlayerVideoSurface(modifier = Modifier.fillMaxSize())
            }
        } else if (PlayerVideoUiLogic.showPipArtwork(isVideoMode, inPip)) {
            val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
