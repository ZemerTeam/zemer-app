package com.jtech.zemer.playback

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wraps the local ExoPlayer for the MediaLibrarySession so that transport from external surfaces
 * (the media notification, lock screen, Android Auto, headset buttons) controls the cast receiver
 * while casting, instead of starting local audio on top of the cast stream. Everything else delegates
 * to the wrapped player, so non-casting behavior is unchanged.
 *
 * Play / pause / seek (including the content position/duration the media-style notification scrubber
 * reads, and seekBack/seekForward/seekTo-index from external surfaces) are routed to the receiver and
 * the remote clock so the notification tracks the cast. The play/pause **state** is mirrored too: while
 * casting the getters report the receiver's state (so the notification shows the right play/pause icon,
 * not the paused local player's), and a remote state change re-notifies the session's listeners so the
 * notification actually refreshes. None of this fires while not casting — non-casting playback and its
 * notification stay pure delegation to the wrapped player.
 */
class CastAwarePlayer(
    player: Player,
    private val discoveryHandler: FCastDiscoveryHandler,
    scope: CoroutineScope,
) : ForwardingPlayer(player) {
    private val casting: Boolean get() = discoveryHandler.isConnected
    private val remotePositionMs: Long get() = CastPlayback.remoteSecondsToMs(discoveryHandler.remoteTime.value)
    private val remoteDurationMs: Long get() = CastPlayback.remoteSecondsToMs(discoveryHandler.remoteDuration.value)
    private val remotePlaying: Boolean get() = CastPlayback.isPlaying(discoveryHandler.remotePlaybackState.value)

    // Listeners the MediaLibrarySession registered, tracked here (in addition to the wrapped player's own
    // forwarding) so a remote play-state change can re-notify them — the paused local player never fires
    // those events while casting, so without this the notification icon would stay frozen on "paused".
    private val listeners = mutableListOf<Player.Listener>()

    init {
        // Runs on the service scope (Main = the player's application thread). Only re-notifies while
        // casting; the first remote PLAYING after connect flips the notification to "playing", and a
        // pause/resume from the TV's own remote keeps it in sync.
        scope.launch {
            discoveryHandler.remotePlaybackState.collect {
                if (casting) notifyPlayStateChanged()
            }
        }
    }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    private fun notifyPlayStateChanged() {
        val events = Player.Events(
            FlagSet.Builder()
                .addAll(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
                .build(),
        )
        // Snapshot — a listener may add/remove itself during dispatch. Property reads below hit the
        // overridden (casting) getters, so the session re-reads the receiver's state.
        listeners.toList().forEach { l ->
            l.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            l.onPlaybackStateChanged(playbackState)
            l.onIsPlayingChanged(isPlaying)
            l.onEvents(this, events)
        }
    }

    // While casting, present the receiver's play state so external surfaces show the correct play/pause
    // icon instead of the paused local player's. Kept mutually consistent (READY + playWhenReady) so
    // however the session derives isPlaying, it agrees.
    override fun getPlayWhenReady(): Boolean = if (casting) remotePlaying else super.getPlayWhenReady()
    override fun getPlaybackState(): Int = if (casting) Player.STATE_READY else super.getPlaybackState()
    override fun isPlaying(): Boolean = if (casting) remotePlaying else super.isPlaying()

    override fun play() {
        if (casting) discoveryHandler.play() else super.play()
    }

    override fun pause() {
        if (casting) discoveryHandler.pause() else super.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (casting) discoveryHandler.seek(CastPlayback.msToRemoteSeconds(positionMs)) else super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        // While casting the receiver only holds the current item, so route the position to it (the
        // index is meaningless remotely). Local cross-item seeks are unaffected.
        if (casting) discoveryHandler.seek(CastPlayback.msToRemoteSeconds(positionMs)) else super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekBack() {
        if (casting) seekRemoteBy(-seekBackIncrement) else super.seekBack()
    }

    override fun seekForward() {
        if (casting) seekRemoteBy(seekForwardIncrement) else super.seekForward()
    }

    /** Seek the receiver by [deltaMs] from the remote clock, clamped to [0, duration]. */
    private fun seekRemoteBy(deltaMs: Long) {
        var target = (remotePositionMs + deltaMs).coerceAtLeast(0L)
        val durMs = remoteDurationMs
        if (durMs > 0L) target = target.coerceAtMost(durMs)
        discoveryHandler.seek(CastPlayback.msToRemoteSeconds(target))
    }

    override fun getCurrentPosition(): Long = if (casting) remotePositionMs else super.getCurrentPosition()
    override fun getDuration(): Long = if (casting) remoteDurationMs else super.getDuration()

    // The media-style notification builds its scrubber from the content position/duration, not
    // getCurrentPosition/getDuration — so these must follow the remote clock too, else the scrubber
    // sits at the paused local player's position while casting.
    override fun getContentPosition(): Long = if (casting) remotePositionMs else super.getContentPosition()
    override fun getContentDuration(): Long = if (casting) remoteDurationMs else super.getContentDuration()
    override fun getBufferedPosition(): Long = if (casting) remotePositionMs else super.getBufferedPosition()
    override fun getContentBufferedPosition(): Long = if (casting) remotePositionMs else super.getContentBufferedPosition()
}
