package com.jtech.zemer.playback

/**
 * The read-only slice of the media3 `Player` that [WatchTimeReporter] needs. Extracting it behind this
 * interface decouples the reporter from `androidx.media3`, so its whole event/state machine is
 * JVM-unit-testable with a pure fake (the project has no Robolectric). [MusicService] adapts the real
 * `Player`; nothing here changes production behavior - each member returns exactly the `Player` value
 * the reporter read before.
 */
interface PlaybackProbe {
    /** `player.currentPosition`. */
    val positionMs: Long

    /** `player.isPlaying`. */
    val isPlaying: Boolean

    /** `player.playbackState` (compared against `Player.STATE_BUFFERING`). */
    val playbackState: Int

    /** `player.playWhenReady`. */
    val playWhenReady: Boolean

    /** `player.currentMediaItem?.mediaId`. */
    val currentMediaId: String?

    /** `player.currentMediaItem?.metadata != null` - whether the current item carries app metadata. */
    val hasCurrentMetadata: Boolean

    /** `player.volume` (<= 0 is the truthful "muted" read). */
    val volume: Float
}
