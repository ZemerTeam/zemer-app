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
}
