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
}
