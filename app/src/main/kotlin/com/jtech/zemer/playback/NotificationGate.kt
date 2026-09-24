package com.jtech.zemer.playback

/**
 * Issue #109, both halves. media3 posts the media notification for ANY prepared player with a
 * queue, playing or paused, and builds it ASYNCHRONOUSLY (a post to the player looper, then the
 * main executor).
 *
 * 1. The reboot half: `MusicService` restores the persisted queue and prepares it on every
 *    creation, and after a reboot SystemUI's media-resumption scanner binds the exported browse
 *    service (`android.media.browse.MediaBrowserService`) with no user in the loop - so a phone
 *    nobody touched showed a "paused" notification and a launcher badge for an app nobody opened.
 *    The notification is gated on USER INTENT, not on who created the service: posted once the
 *    app's own UI has bound, an explicit start command arrived (widget tap, media button, the
 *    activity's own start, a sticky restart), or playback actually runs. A controller merely
 *    connecting (the boot scanner, an auto-reconnecting headset, Android Auto) is not intent.
 * 2. The task-clear half: "Stop music on task clear" pauses, removes the foreground notification
 *    and stops the service - but the pause had already scheduled media3's async paused-notification
 *    post, which then landed on the dead service as a zombie notification that only a force-stop
 *    cleared. While the service is stopping for a task clear, nothing may be scheduled.
 *
 * Pure so both rules are unit-tested.
 */
object NotificationGate {
    /**
     * Whether the media notification may be posted right now. [startInForegroundRequired] is
     * media3 telling the service playback is ongoing and it MUST go foreground - never vetoed,
     * except while the service is already stopping for a task clear (playback was just paused for
     * exactly that; the service's own removal is the last word).
     */
    fun shouldPost(
        userIntentSeen: Boolean,
        playWhenReady: Boolean,
        startInForegroundRequired: Boolean,
        stoppingOnTaskClear: Boolean = false,
    ): Boolean =
        !stoppingOnTaskClear && (userIntentSeen || playWhenReady || startInForegroundRequired)
}
