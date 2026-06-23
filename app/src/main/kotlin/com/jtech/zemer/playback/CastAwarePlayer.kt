package com.jtech.zemer.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Wraps the local ExoPlayer for the MediaLibrarySession so that play/pause from external transport
 * (the media notification, lock screen, Android Auto, headset buttons) controls the cast receiver
 * while casting, instead of starting local audio on top of the cast stream. Everything else
 * delegates to the wrapped player, so non-casting behavior is unchanged.
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
}
