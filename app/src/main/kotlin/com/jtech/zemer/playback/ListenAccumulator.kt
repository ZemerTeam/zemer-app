package com.jtech.zemer.playback

/**
 * Guards the tracking/history signal across audio↔video rendition swaps (I4). A swap ends the current
 * ExoPlayer `PlaybackStats` session mid-listen, so `onPlaybackStatsReady` fires — without this guard a
 * single toggle would double-fire the `play` tracking event, double-insert the history `Event`, and
 * double-register YouTube playback for one listen.
 *
 * The rule: a stats-ready that was caused by a swap is **suppressed**, its play time **stashed**; the
 * next non-swap (real) end **emits once** with the accumulated total. Pure + JVM-tested; the controller
 * calls [onSwap] immediately before it issues a swap, and [MusicService] routes every stats-ready
 * through [onStatsReady]. State is memory-only (process death mid-video loses the stash — acceptable,
 * telemetry is fire-and-forget).
 */
class ListenAccumulator {
    private var accumulatingId: String? = null
    private var stashedMs: Long = 0L
    private var swapPendingForId: String? = null

    sealed interface Result {
        /** Suppress all listen side effects for this stats-ready (a swap ended the session mid-listen). */
        data object Suppress : Result

        /** Emit the listen once with the accumulated total play time (real end of the listen). */
        data class Emit(val totalMs: Long) : Result
    }

    /** Mark that a rendition swap is about to end [mediaId]'s current stats session. Call right before the swap. */
    fun onSwap(mediaId: String) {
        swapPendingForId = mediaId
    }

    /**
     * A `PlaybackStats` session ended for [mediaId] with [playTimeMs] actual play time. Returns whether to
     * [Result.Suppress] it (swap-caused) or [Result.Emit] the accumulated listen (real end).
     */
    fun onStatsReady(mediaId: String, playTimeMs: Long): Result {
        if (swapPendingForId == mediaId) {
            // Swap-caused end: stash this segment's play time and suppress every side effect.
            if (accumulatingId != mediaId) {
                accumulatingId = mediaId
                stashedMs = 0L
            }
            stashedMs += playTimeMs
            swapPendingForId = null
            return Result.Suppress
        }
        // Real end of the listen: emit the stash (if it belongs to this id) plus this final segment. Do
        // NOT touch swapPendingForId here — it can only belong to a different id in this branch (a match
        // would have taken the Suppress path above), and clearing another item's pending mark is wrong.
        val total = (if (accumulatingId == mediaId) stashedMs else 0L) + playTimeMs
        if (accumulatingId == mediaId) {
            accumulatingId = null
            stashedMs = 0L
        }
        return Result.Emit(total)
    }

    /**
     * A real track transition happened — clear any stale swap mark so a dangling mark (a swap whose
     * stats-ready never arrived) can never suppress a later real listen. Deliberately does NOT clear the
     * accumulating stash: the departed listen's final stats-ready still needs it, and that Emit path
     * clears the stash itself — so this stays correct regardless of whether the transition callback or
     * the final stats-ready fires first.
     */
    fun onTrackTransition() {
        swapPendingForId = null
    }
}
