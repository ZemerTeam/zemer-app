package com.jtech.zemer.playback

/**
 * Pure decision logic for recovering from a **receiver playback error** while casting, extracted from
 * [CastController] so the escalation ladder is unit-testable without a player, the FCast SDK, or an
 * Android runtime (same pattern as [CastAutoAdvance]).
 *
 * Why this exists: googlevideo stream URLs are network-identity-bound, and the *receiver* fetches them
 * from its own address — on some networks (measured: T-Mobile home internet, where every IPv4/CGNAT
 * fetch is 403 and only IPv6 is served) the receiver's per-connection address-family choice makes a
 * fetch fail intermittently, either immediately ("Not authorized to access resource.") or on a
 * mid-track buffer-refill reconnect ("Could not read from resource."). Before this ladder existed the
 * error was swallowed and the cast session sat silent — indistinguishable from auto-advance breaking.
 *
 * The ladder for repeated errors on the SAME loaded track:
 *  1. [Action.RELOAD] — re-send the same URL; a fresh receiver connection re-rolls its address family,
 *     so a mid-track failure usually resumes (from the last reported position).
 *  2. [Action.RESOLVE_FRESH] — drop the cached URL, re-resolve, reload; covers an expired/poisoned URL.
 *  3. [Action.DIRECT_URL] — only when the failing load went through the phone-side stream relay
 *     ([CastStreamRelay]): hand the receiver the raw googlevideo URL instead. The error callback can't
 *     say WHY the receiver failed, and a receiver that can't fetch from the relay at all (cleartext-http
 *     policy, phone unreachable) would otherwise burn the whole ladder on relay URLs it can never use.
 *     Blind but strictly additive: it runs where the ladder previously abandoned the track.
 *  4. [Action.ADVANCE] — abandon the track and let the queue continue, capped by
 *     [MAX_CONSECUTIVE_ERROR_ADVANCES] consecutive abandoned tracks so a fully-broken network doesn't
 *     machine-gun through the whole queue; then [Action.GIVE_UP].
 */
object CastErrorRecovery {
    /**
     * Errors reported within this window of the previously handled one belong to the same failure —
     * a receiver's media pipeline can emit several error callbacks for one broken fetch, and they must
     * not each burn a rung of the ladder.
     */
    const val ERROR_BURST_WINDOW_MS = 1500L

    /**
     * Remote progress (sec) past which the track counts as genuinely playing: both the per-track
     * attempt count and the consecutive-abandoned-tracks count reset, so a later unrelated error starts
     * the ladder from the top instead of instantly skipping the track.
     */
    const val PROGRESS_RESET_SEC = 10.0

    /** Max consecutive tracks abandoned via [Action.ADVANCE] before recovery stops trying. */
    const val MAX_CONSECUTIVE_ERROR_ADVANCES = 3

    enum class Action { RELOAD, RESOLVE_FRESH, DIRECT_URL, ADVANCE, GIVE_UP }

    /**
     * The recovery action for the [attempt]-th error on the currently loaded track (0-based: the first
     * error is attempt 0). [canAdvance] is false when the queue can't move on (repeat-one, or no next
     * item) — abandoning the track is then meaningless, so the ladder gives up instead of looping the
     * same failing load forever. [canTryDirect] is true only when the receiver's failing load is a
     * relay URL — the de-relay rung is skipped entirely for a load that was already direct.
     */
    fun actionForAttempt(
        attempt: Int,
        consecutiveErrorAdvances: Int,
        canAdvance: Boolean,
        canTryDirect: Boolean,
    ): Action = when {
        attempt <= 0 -> Action.RELOAD
        attempt == 1 -> Action.RESOLVE_FRESH
        attempt == 2 && canTryDirect -> Action.DIRECT_URL
        canAdvance && consecutiveErrorAdvances < MAX_CONSECUTIVE_ERROR_ADVANCES -> Action.ADVANCE
        else -> Action.GIVE_UP
    }

    /** Whether an error report is a new failure rather than part of the previous one's burst. */
    fun isNewFailure(nowMs: Long, lastHandledMs: Long): Boolean = nowMs - lastHandledMs > ERROR_BURST_WINDOW_MS

    /** Whether this much remote progress proves real playback and resets the recovery counters. */
    fun progressResetsCounters(progressSec: Double): Boolean = progressSec >= PROGRESS_RESET_SEC
}
