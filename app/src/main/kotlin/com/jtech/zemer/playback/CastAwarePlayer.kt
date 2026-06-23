package com.jtech.zemer.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Wraps the local ExoPlayer for the MediaLibrarySession so that transport from external surfaces
 * (the media notification, lock screen, Android Auto, headset buttons) controls the cast receiver
 * while casting, instead of starting local audio on top of the cast stream. Everything else delegates
 * to the wrapped player, so non-casting behavior is unchanged.
 *
 * Play / pause / seek are routed to the receiver, and the reported position/duration follow the
 * remote clock so the notification scrubber tracks the cast. The play/pause icon still reflects the
 * (paused) local player's state while casting — fully mirroring the remote PLAYING/PAUSED state into
 * the session would require synthesising Player.Events from the remote callbacks, which is avoided
 * here to keep the core (non-casting) notification behaviour untouched.
 */
class CastAwarePlayer(
    player: Player,
    private val discoveryHandler: FCastDiscoveryHandler,
) : ForwardingPlayer(player) {
    private val casting: Boolean get() = discoveryHandler.connectedDevice != null

    override fun play() {
        if (casting) discoveryHandler.play() else super.play()
    }

    override fun pause() {
        if (casting) discoveryHandler.pause() else super.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (casting) discoveryHandler.seek(positionMs / 1000.0) else super.seekTo(positionMs)
    }

    override fun getCurrentPosition(): Long =
        if (casting) CastPlayback.remoteSecondsToMs(discoveryHandler.remoteTime.value) else super.getCurrentPosition()

    override fun getDuration(): Long =
        if (casting) CastPlayback.remoteSecondsToMs(discoveryHandler.remoteDuration.value) else super.getDuration()
}
