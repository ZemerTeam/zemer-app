package com.jtech.zemer.playback

import android.view.KeyEvent

/** What [CastVolumeKeys.decide] says the caller should do with a dispatched key event. */
sealed interface CastVolumeKeyAction {
    /** Step the receiver's volume up one notch. */
    object AdjustUp : CastVolumeKeyAction
    /** Step the receiver's volume down one notch. */
    object AdjustDown : CastVolumeKeyAction
    /** Swallow the event without acting on it (see [CastVolumeKeys.decide] for why). */
    object Consume : CastVolumeKeyAction
    /** Not our concern — let the event fall through to the normal (system volume) handling. */
    object Ignore : CastVolumeKeyAction
}

/**
 * Pure decision for what a hardware volume key press should do while casting. Extracted from
 * [android.app.Activity.dispatchKeyEvent] so the routing rule is unit-testable without an Android
 * runtime.
 */
object CastVolumeKeys {
    /**
     * While casting, the hardware volume keys must drive the RECEIVER's volume instead of the
     * phone's — otherwise the buttons silently do nothing useful (or worse, ride the phone's own
     * media stream which isn't what's audible). ACTION_DOWN adjusts the receiver by one step per
     * event, which already repeats naturally while the key is held (the platform keeps delivering
     * ACTION_DOWN with an incrementing repeatCount). ACTION_UP (and any other action) is still
     * consumed rather than ignored: letting it fall through to the default handling pops the
     * system's own volume UI/toast even though the system volume didn't move, which is confusing.
     * Not casting, or a non-volume key, is always [CastVolumeKeyAction.Ignore] so system volume
     * control is completely unaffected.
     */
    fun decide(keyCode: Int, action: Int, isCasting: Boolean): CastVolumeKeyAction {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return CastVolumeKeyAction.Ignore
        }
        if (!isCasting) return CastVolumeKeyAction.Ignore
        if (action != KeyEvent.ACTION_DOWN) return CastVolumeKeyAction.Consume
        return if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            CastVolumeKeyAction.AdjustUp
        } else {
            CastVolumeKeyAction.AdjustDown
        }
    }
}
