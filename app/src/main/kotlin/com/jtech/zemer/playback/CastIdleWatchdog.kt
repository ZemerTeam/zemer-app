package com.jtech.zemer.playback

import org.fcast.sender_sdk.PlaybackState

/**
 * Pure decision logic for **auto-ending a cast session that has gone dead**, extracted from
 * [CastController] so the timeouts are unit-testable without a player, the FCast SDK, or an Android
 * runtime (same pattern as [CastAutoAdvance] and [CastErrorRecovery]).
 *
 * Why this exists: a cast session can be left hanging with nothing playing and never tear down —
 * holding the phone-side stream relay, the foreground service, and wake locks open pointlessly:
 *  - the receiver is **paused** and the user has walked away (a deliberate pause, so a generous grace),
 *  - the receiver is supposedly **playing but its clock is frozen** — cut off mid-track without the SDK
 *    ever reporting `Disconnected` (a crashed/frozen receiver whose TCP socket lingers), a much shorter
 *    grace because nothing is going to recover it.
 * (The third dead-session case — [CastErrorRecovery] exhausting its ladder — ends the session directly
 * from the GIVE_UP rung; this watchdog covers the silent stalls that emit no error callback at all.)
 *
 * The watchdog reads a single "time since the remote clock last moved forward" measure ([idleForMs]):
 * genuine playback advances it, a pause or a freeze does not. It runs in [CastController]'s existing
 * 1 Hz cast poll, so there is no extra wakeup when not casting.
 */
object CastIdleWatchdog {
    /**
     * How long the receiver may sit **paused** before the session is auto-ended. Deliberately long: a
     * pause is a user action (a phone call, a break), and losing the cast on a short pause would be
     * hostile — this only reclaims a session the user has clearly abandoned.
     */
    const val PAUSED_IDLE_TIMEOUT_MS = 20L * 60_000L

    /**
     * How long the remote clock may be frozen while **not** paused (playing/buffering/idle, i.e. the
     * receiver is meant to be making progress but isn't) before the session is auto-ended. Much shorter
     * than the paused grace: a track that reports zero forward progress for this long has been cut off,
     * and no amount of waiting will recover it. Long enough to ride out a genuine buffering hiccup.
     */
    const val STALLED_IDLE_TIMEOUT_MS = 3L * 60_000L

    /**
     * Whether a cast session should be auto-ended, given the receiver's [state] and how long the remote
     * clock has been frozen ([idleForMs] — reset to 0 by any real forward progress or a fresh load).
     *
     * Position is deliberately NOT considered. During healthy playback the idle timer is reset on every
     * track load, so [idleForMs] only reaches these (minutes-long) timeouts when playback is genuinely
     * stuck — including a queue that has run out and left the receiver frozen at the end of its last
     * track (there is no next item, so auto-advance can't rescue it; the watchdog is the only exit). A
     * track boundary during healthy playback resolves in seconds via auto-advance, far below any timeout.
     */
    fun shouldEndIdleSession(state: PlaybackState?, idleForMs: Long): Boolean {
        val timeout = if (CastPlayback.isPaused(state)) PAUSED_IDLE_TIMEOUT_MS else STALLED_IDLE_TIMEOUT_MS
        return idleForMs >= timeout
    }
}
