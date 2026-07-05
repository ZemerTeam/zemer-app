package com.jtech.zemer.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.playback.CastVolumeKeyAction
import com.jtech.zemer.playback.CastVolumeKeys
import com.jtech.zemer.playback.FCastDiscoveryHandler

/**
 * A [Modifier] that routes hardware volume keys to the cast receiver while the composable it is applied
 * to lives inside an overlay WINDOW (a Compose ModalBottomSheet / Dialog). Those windows never forward
 * key events to `MainActivity.dispatchKeyEvent`, so the Activity-level handler can't reach them.
 *
 * The app's minSdk is 26 and the G1 test device runs API 27, where the platform's OnUnhandledKeyEvent
 * mechanism (API 28+) does not exist — so this deliberately uses Compose's OWN focus-based key pipeline
 * (`onPreviewKeyEvent`) instead, which works from API 26. Apply it to the overlay's content ROOT (an
 * ancestor of the menu/dialog items): preview events travel root→leaf through the focused subtree, so
 * the ancestor keeps seeing volume keys even after focus moves to a child (e.g. via D-pad). It reuses
 * the same pure [CastVolumeKeys.decide] rule as the Activity path so the two can never disagree; when
 * not casting it consumes nothing and normal system-volume behaviour is untouched.
 *
 * @param seedFocus when true, the node is made focusable and grabs focus as the overlay appears, so
 *   key events flow even when the overlay has no other focused content (needed for the 3-dot player
 *   menu, whose items aren't auto-focused). Pass false for dialogs that auto-focus their own content
 *   (e.g. a text field) so this doesn't steal that focus — onPreviewKeyEvent still fires via whatever
 *   the dialog itself focuses.
 */
@Composable
fun castVolumeKeyModifier(seedFocus: Boolean = true): Modifier {
    val handler = LocalPlayerConnection.current?.service?.discoveryHandler
    val preview = Modifier.onPreviewKeyEvent { event -> onCastVolumeKeyEvent(event, handler) }
    if (!seedFocus) return preview

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(handler) { runCatching { focusRequester.requestFocus() } }
    return Modifier
        .focusRequester(focusRequester)
        .focusable()
        .then(preview)
}

/** Routes one Compose key event to the receiver volume while casting; returns true to consume it. */
private fun onCastVolumeKeyEvent(event: KeyEvent, handler: FCastDiscoveryHandler?): Boolean {
    if (handler == null) return false
    val native = event.nativeKeyEvent
    return when (CastVolumeKeys.decide(native.keyCode, native.action, handler.isConnected)) {
        CastVolumeKeyAction.AdjustUp -> {
            handler.adjustVolume(+1)
            true
        }
        CastVolumeKeyAction.AdjustDown -> {
            handler.adjustVolume(-1)
            true
        }
        CastVolumeKeyAction.Consume -> true
        CastVolumeKeyAction.Ignore -> false
    }
}
