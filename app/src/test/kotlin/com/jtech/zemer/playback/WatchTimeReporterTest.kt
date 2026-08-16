package com.jtech.zemer.playback

import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Drives the [WatchTimeReporter] STATE MACHINE with a pure [PlaybackProbe] fake (no media3, no
 * Robolectric) and asserts via the observable OFFLINE-capture sink - which exercises the shared
 * session/segment/transition orchestration end to end: session open, segment accumulation, seek
 * exclusion, the genuine-play gate, the pause-history privacy semantics, the teardown end-position,
 * the buffering-is-not-a-pause rule, and the relay exclusion.
 *
 * The reporter's scope is Unconfined, so every `launch` (the ping consumer) runs synchronously and a
 * finished offline session has called `onOfflineListen` by the time the driving call returns.
 */
class WatchTimeReporterTest {

    // media3 Player constant values (kept as literals so the test never loads the Player class).
    private companion object {
        const val STATE_READY = 3            // Player.STATE_READY
        const val STATE_BUFFERING = 2        // Player.STATE_BUFFERING
        const val REASON_SEEK = 1            // Player.DISCONTINUITY_REASON_SEEK
    }

    private class FakeProbe : PlaybackProbe {
        override var positionMs = 0L
        override var isPlaying = false
        override var playbackState = STATE_READY
        override var playWhenReady = true
        override var currentMediaId: String? = "v1"
        override var hasCurrentMetadata = true
        override var volume = 1f
    }

    private val probe = FakeProbe()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var paused = false
    private var relay = false
    private val captured = mutableListOf<DeferredStatsRecord>()

    // Offline: fetchTracking returns null so every finished session takes the offline-capture branch.
    private fun reporter() = WatchTimeReporter(
        probe = probe,
        scope = scope,
        isCasting = { false },
        isRelay = { relay },
        historyPaused = { paused },
        fetchTracking = { null },
        onOfflineListen = { captured.add(it) },
    )

    @After
    fun tearDown() = scope.cancel()

    private fun play(from: Long) {
        probe.positionMs = from
        probe.isPlaying = true
        probe.playbackState = STATE_READY
    }

    @Test
    fun `a played range is captured as one deferred record`() {
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 30_000
        r.onPlaybackEnded()

        assertEquals(1, captured.size)
        with(captured.single()) {
            assertEquals("v1", videoId)
            assertEquals("0.0", st)
            assertEquals("30.0", et)
            assertEquals("30.0", rt)
            assertEquals("30.0", cmt)
        }
    }

    @Test
    fun `a seek is excluded - only genuinely played ranges are captured`() {
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 20_000
        // seek 20s -> 60s (the 20..60 stretch is skipped, never watched)
        r.onPositionDiscontinuity(0, 20_000, "v1", 0, 60_000, REASON_SEEK)
        probe.positionMs = 80_000
        r.onPlaybackEnded()

        with(captured.single()) {
            assertEquals("0.0,60.0", st)
            assertEquals("20.0,80.0", et)
            assertEquals("40.0", rt) // 20s + 20s watched, not the seeked-over 40s
        }
    }

    @Test
    fun `a listen shorter than the genuine-play gate is dropped`() {
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 5_000 // below the 10s gate
        r.onPlaybackEnded()

        assertEquals(0, captured.size)
    }

    @Test
    fun `paused-at-start captures nothing even if unpaused later`() {
        paused = true
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 30_000
        paused = false // unpaused before the listen ends
        r.onPlaybackEnded()

        assertEquals("a listen that started private is never queued", 0, captured.size)
    }

    @Test
    fun `onDestroy captures the tail up to the current position, not a stale flush point`() {
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 180_000 // advanced with no intervening flush
        r.onDestroy()

        // The teardown must use the player's current position; a last-known fallback would be 0 here
        // (no onProgress ran) and drop the whole tail below the gate.
        with(captured.single()) {
            assertEquals("180.0", et)
            assertEquals("180.0", rt)
        }
    }

    @Test
    fun `a relay session never captures`() {
        relay = true
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        probe.positionMs = 60_000
        r.onPlaybackEnded()

        assertEquals(0, captured.size)
    }

    @Test
    fun `a mid-track rebuffer does not split the played range`() {
        val r = reporter()
        play(0)
        r.onIsPlayingChanged(true)
        // rebuffer at 15s: isPlaying flips false while still wanting to play + BUFFERING
        probe.positionMs = 15_000
        probe.playbackState = STATE_BUFFERING
        probe.playWhenReady = true
        r.onIsPlayingChanged(false)
        // resume
        probe.playbackState = STATE_READY
        r.onIsPlayingChanged(true)
        probe.positionMs = 30_000
        r.onPlaybackEnded()

        // One continuous range, not "0..15,15..30" - the stall was not treated as a pause.
        with(captured.single()) {
            assertEquals("0.0", st)
            assertEquals("30.0", et)
        }
    }

    // Sanity: the deferred fetchTracking type is the real innertube model (compile check).
    @Suppress("unused")
    private fun typeCheck(): PlayerResponse.PlaybackTracking? = null
}
