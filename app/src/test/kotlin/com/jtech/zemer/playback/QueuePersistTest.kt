package com.jtech.zemer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the queue-content signature (issue #515): it must change on any content edit so the heavy
 * queue file is rewritten, and stay stable across plain playback progress so the every-10s save
 * skips the expensive re-serialization.
 */
class QueuePersistTest {

    @Test
    fun `same items and title produce the same signature`() {
        assertEquals(
            QueuePersist.signature(listOf("a", "b", "c"), "My queue"),
            QueuePersist.signature(listOf("a", "b", "c"), "My queue"),
        )
    }

    @Test
    fun `reordering items changes the signature`() {
        assertNotEquals(
            QueuePersist.signature(listOf("a", "b", "c"), "q"),
            QueuePersist.signature(listOf("a", "c", "b"), "q"),
        )
    }

    @Test
    fun `adding or removing an item changes the signature`() {
        val base = QueuePersist.signature(listOf("a", "b"), "q")
        assertNotEquals(base, QueuePersist.signature(listOf("a", "b", "c"), "q"))
        assertNotEquals(base, QueuePersist.signature(listOf("a"), "q"))
    }

    @Test
    fun `changing the title changes the signature`() {
        assertNotEquals(
            QueuePersist.signature(listOf("a", "b"), "one"),
            QueuePersist.signature(listOf("a", "b"), "two"),
        )
    }

    @Test
    fun `a null title is stable`() {
        assertEquals(
            QueuePersist.signature(listOf("a"), null),
            QueuePersist.signature(listOf("a"), null),
        )
    }

    // --- shouldWrite (the queue-file write decision) ---

    @Test
    fun `teardown always writes, even with an unchanged signature`() {
        assertEquals(true, QueuePersist.shouldWrite(blocking = true, signature = "s", lastWrittenSignature = "s"))
    }

    @Test
    fun `first save writes (no previous written signature)`() {
        assertEquals(true, QueuePersist.shouldWrite(blocking = false, signature = "s", lastWrittenSignature = null))
    }

    @Test
    fun `changed signature writes, unchanged skips`() {
        assertEquals(true, QueuePersist.shouldWrite(blocking = false, signature = "new", lastWrittenSignature = "old"))
        assertEquals(false, QueuePersist.shouldWrite(blocking = false, signature = "s", lastWrittenSignature = "s"))
    }

    // --- playerStateChanged (the state-file write decision) ---

    private fun state(position: Long = 1000L, playing: Boolean = true, timestamp: Long = 0L) =
        com.jtech.zemer.models.PersistPlayerState(
            playWhenReady = playing,
            repeatMode = 0,
            shuffleModeEnabled = false,
            volume = 1f,
            currentPosition = position,
            currentMediaItemIndex = 0,
            playbackState = 3,
            timestamp = timestamp,
        )

    @Test
    fun `first capture always counts as changed`() {
        assertEquals(true, QueuePersist.playerStateChanged(null, state()))
    }

    @Test
    fun `advancing position counts as changed - playing keeps writing every save`() {
        assertEquals(true, QueuePersist.playerStateChanged(state(position = 1000L), state(position = 11_000L)))
    }

    @Test
    fun `a paused, unmoved state is unchanged - the idle service goes write-silent`() {
        assertEquals(
            false,
            QueuePersist.playerStateChanged(
                state(position = 5000L, playing = false, timestamp = 1L),
                state(position = 5000L, playing = false, timestamp = 2L),
            ),
        )
    }

    @Test
    fun `a play-pause flip counts as changed even at the same position`() {
        assertEquals(
            true,
            QueuePersist.playerStateChanged(
                state(position = 5000L, playing = true),
                state(position = 5000L, playing = false),
            ),
        )
    }
}
