package com.jtech.zemer.playback

import com.jtech.zemer.playback.ListenAccumulator.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenAccumulatorTest {

    @Test
    fun `no swap ⇒ pass through, emitting the play time`() {
        val acc = ListenAccumulator()
        assertEquals(Result.Emit(5000), acc.onStatsReady("X", 5000))
    }

    @Test
    fun `single swap ⇒ suppress the swap-ended segment, emit the total at the real end`() {
        val acc = ListenAccumulator()
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 4000)) // audio segment ended by the swap
        assertEquals(Result.Emit(4000 + 3000), acc.onStatsReady("X", 3000)) // video segment = real end
    }

    @Test
    fun `multiple toggles within one listen accumulate once`() {
        val acc = ListenAccumulator()
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 2000)) // audio → video
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 3000)) // video → audio
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 1000)) // audio → video
        assertEquals(Result.Emit(2000 + 3000 + 1000 + 500), acc.onStatsReady("X", 500)) // real end
    }

    @Test
    fun `a swap-ended zero-playtime segment is still suppressed`() {
        val acc = ListenAccumulator()
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 0))
        assertEquals(Result.Emit(6000), acc.onStatsReady("X", 6000))
    }

    @Test
    fun `a real track transition clears a stale swap mark so the next listen is not suppressed`() {
        val acc = ListenAccumulator()
        acc.onSwap("X") // e.g. a swap that never produced its stats-ready
        acc.onTrackTransition()
        assertEquals(Result.Emit(7000), acc.onStatsReady("X", 7000))
    }

    @Test
    fun `stash does not leak across a real end into a later listen`() {
        val acc = ListenAccumulator()
        acc.onSwap("X")
        assertEquals(Result.Suppress, acc.onStatsReady("X", 1000))
        assertEquals(Result.Emit(1000 + 2000), acc.onStatsReady("X", 2000)) // real end clears stash
        // A brand-new listen of the same id must not re-add the old stash.
        assertEquals(Result.Emit(500), acc.onStatsReady("X", 500))
    }

    @Test
    fun `a stats-ready for a different id than the pending swap is emitted, not suppressed`() {
        val acc = ListenAccumulator()
        acc.onSwap("X")
        // The previous item Y ends normally while a swap on X is pending — Y must still emit.
        assertEquals(Result.Emit(3000), acc.onStatsReady("Y", 3000))
    }
}
