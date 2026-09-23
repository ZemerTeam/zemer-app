package com.jtech.zemer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

/**
 * Picture-in-picture for video mode: the app shrinks to a floating video window when the user leaves
 * it while a video is showing (MainActivity.onUserLeaveHint). While in
 * the window only the video renders ([com.jtech.zemer.playback.PlayerVideoUiLogic.showPipVideo]);
 * closing the window stops the Activity, whose existing onStop revert returns playback to audio.
 */
private val VIDEO_PIP_ASPECT = Rational(16, 9)

/** Enters the PiP window; false when the device (some low-RAM and TV builds) or the user's per-app PiP setting refuses. */
fun Activity.enterVideoPip(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && runCatching {
        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(VIDEO_PIP_ASPECT).build())
    }.getOrDefault(false)

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
