package com.jtech.zemer.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import timber.log.Timber

/**
 * Wraps the notification provider so a late artwork update cannot crash the app. When the artwork loads
 * after the notification was built, media3 (through 1.10.1) re-posts it from the bitmap callback and, while
 * playing, calls `startForegroundService` with nothing catching Android 12+'s refusal (fatal on a Pixel 7a,
 * Android 17). media3's own `onUpdateNotification` path catches that same refusal; this does the same for
 * the callback path: that one update is skipped, and the next regular update posts the notification.
 */
@UnstableApi
class ForegroundSafeNotificationProvider(
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider by delegate {
    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification =
        delegate.createNotification(mediaSession, mediaButtonPreferences, actionFactory) { notification ->
            ignoringForegroundRefusal { onNotificationChangedCallback.onNotificationChanged(notification) }
        }
}

/** Runs [block]; a refused foreground-service start ([isRefusal]) is logged and dropped, anything else rethrown. */
internal inline fun ignoringForegroundRefusal(
    isRefusal: (Throwable) -> Boolean = ::isForegroundStartRefusal,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: IllegalStateException) {
        if (!isRefusal(e)) throw e
        Timber.w(e, "Notification update skipped: foreground service start not allowed")
    }
}

private fun isForegroundStartRefusal(e: Throwable): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
