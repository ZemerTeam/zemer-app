package com.jtech.zemer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the cast receiver's volume (0.0–1.0) — the source of truth the player-menu slider observes
 * while casting. The tracked value starts as an optimistic 1.0 placeholder, but the receiver's REAL
 * level is unknown until either the receiver reports it (volumeChanged) or the user sets an absolute
 * level from the slider. Until then, relative hardware-key steps are refused ([step] returns null):
 * stepping from the placeholder would SET a receiver that never reported — e.g. a TV sitting at 20% —
 * to ~93% on the first volume-DOWN press. Absolute sets are always safe (the user is pointing at the
 * level they want), so the slider works immediately and also unlocks stepping.
 */
class RemoteVolumeTracker {
    private val _volume = MutableStateFlow(1.0)

    /** Tracked receiver volume for the UI. */
    val volume: StateFlow<Double> = _volume.asStateFlow()

    // Receiver reports arrive on SDK callback threads while steps/sets run on the main thread.
    @Volatile
    private var known = false

    /** Forgets the previous connection's level for a fresh one (connectTo). */
    fun reset() {
        _volume.value = 1.0
        known = false
    }

    /** The receiver reported its actual volume (volumeChanged) — e.g. its own remote moved it. */
    fun onReceiverReport(volume: Double) {
        _volume.value = volume.coerceIn(0.0, 1.0)
        known = true
    }

    /** Absolute set from the UI slider; returns the coerced value to send to the receiver. */
    fun setAbsolute(volume: Double): Double {
        val v = volume.coerceIn(0.0, 1.0)
        _volume.value = v
        known = true
        return v
    }

    /**
     * One hardware-key step in [direction] (+1 up / -1 down); returns the new value to send, or null
     * while the receiver's actual level is still unknown (see class doc — never step the placeholder).
     */
    fun step(direction: Int): Double? {
        if (!known) return null
        return setAbsolute(CastPlayback.steppedVolume(_volume.value, direction))
    }
}
