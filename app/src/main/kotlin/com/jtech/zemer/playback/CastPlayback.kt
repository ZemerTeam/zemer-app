package com.jtech.zemer.playback

import org.fcast.sender_sdk.PlaybackState

/**
 * Pure mappings for FCast remote playback state and clock units, extracted so they are unit-testable
 * and so the rest of the app never re-derives them (the previous code matched play state via
 * `state.toString().contains("Playing")` — a stringly-typed check that silently breaks if the SDK
 * enum is ever renamed, and re-implemented the seconds↔milliseconds conversion at five call sites
 * where a single dropped `* 1000` would desync the seek bar).
 *
 * The FCast SDK reports position/duration in **seconds**; the app's player works in **milliseconds**.
 */
object CastPlayback {
    /** True only when the remote device is actively playing (not paused, buffering, or idle). */
    fun isPlaying(state: PlaybackState?): Boolean = state == PlaybackState.PLAYING

    /**
     * True when the receiver is deliberately paused. The stall-based end detector must NOT treat a
     * paused-near-the-end track as "finished" — pausing freezes the remote clock, which otherwise
     * looks identical to a stall and would auto-skip a track the user paused on purpose.
     */
    fun isPaused(state: PlaybackState?): Boolean = state == PlaybackState.PAUSED

    /**
     * Whether the receiver should be treated as playing for a toggle decision: its reported state once
     * the first playbackStateChanged has arrived, else the play intent ([shouldPlay]). This is the same
     * fallback the UI's isPlaying uses, so a tap in the pre-first-report window (button already showing
     * "pause" from the connect intent) toggles what the button shows instead of re-asserting play().
     */
    fun isRemotePlaying(state: PlaybackState?, shouldPlay: Boolean): Boolean =
        if (state != null) isPlaying(state) else shouldPlay

    /**
     * The play intent a remote state change implies: PLAYING -> keep playing, PAUSED -> keep paused,
     * transient/unknown states (buffering, idle, null) -> no change. Lets a pause/resume from the TV's
     * own remote be mirrored into our intent without fighting it.
     */
    fun playIntentForState(state: PlaybackState?): Boolean? = when (state) {
        PlaybackState.PLAYING -> true
        PlaybackState.PAUSED -> false
        else -> null
    }

    /** Remote clock (seconds) → app/player milliseconds. */
    fun remoteSecondsToMs(seconds: Double): Long = (seconds * 1000).toLong()

    /** App/player milliseconds → remote clock (seconds). */
    fun msToRemoteSeconds(ms: Long): Double = ms / 1000.0

    /**
     * Whether the LOCAL ExoPlayer should be told to start playing. While connected to a receiver it
     * never should — the receiver is the one that plays, and starting local audio on top of it is the
     * dual-playback bug (a new queue's songs can resolve asynchronously well after the initial
     * cast-connect pause, so callers must re-check the live connection state here rather than trusting
     * an earlier pause to still hold).
     */
    fun shouldStartLocalPlayback(playWhenReady: Boolean, isCasting: Boolean): Boolean =
        playWhenReady && !isCasting

    /**
     * Whether opening a full-screen local video (which plays audio through the phone) should pause the
     * cast receiver so the two don't play at once. Only when actually casting AND the receiver is
     * playing — a receiver the user already paused is left alone so we don't later resume something we
     * never interrupted.
     */
    fun shouldPauseCastForVideo(isCasting: Boolean, remoteState: PlaybackState?): Boolean =
        isCasting && isPlaying(remoteState)

    /**
     * Whether closing the local video should resume the cast receiver: only if we were the one that
     * paused it ([pausedByVideo]) and the session is still connected. A session that dropped or ended
     * while the video was open must not be revived.
     */
    fun shouldResumeCastAfterVideo(pausedByVideo: Boolean, isCasting: Boolean): Boolean =
        pausedByVideo && isCasting

    /**
     * Whether swiping the app from recents (with "stop music on task clear" enabled) should also end
     * the cast session. Pausing the local player is a no-op on the receiver — its own socket and the
     * stream relay keep it playing — so "stop on task clear" wouldn't actually stop the music unless
     * the session is disconnected. Only relevant while connected.
     */
    fun shouldEndCastOnTaskClear(stopOnTaskClear: Boolean, isCasting: Boolean): Boolean =
        stopOnTaskClear && isCasting

    /** Fraction of full [0.0, 1.0] volume a single hardware-key press adjusts by. */
    const val VOLUME_STEP = 1.0 / 15

    /**
     * The receiver volume [current] would move to after one press in [direction] (+1 up / -1 down),
     * clamped to the device's valid range so repeated presses at either end are no-ops instead of
     * drifting out of bounds.
     */
    fun steppedVolume(current: Double, direction: Int, step: Double = VOLUME_STEP): Double =
        (current + direction * step).coerceIn(0.0, 1.0)
}
