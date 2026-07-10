package com.jtech.zemer.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LogBufferTreeTest {

    @Before
    fun setUp() {
        LogBufferTree.clear()
        Timber.plant(LogBufferTree)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        LogBufferTree.clear()
    }

    @Test
    fun `keeps only the newest 500 entries`() {
        repeat(600) { i -> Timber.tag("T").d("msg %d", i) }

        val entries = LogBufferTree.entries
        assertEquals(500, entries.size)
        assertEquals("msg 100", entries.first().message)
        assertEquals("msg 599", entries.last().message)
    }

    @Test
    fun `clear empties the buffer`() {
        Timber.tag("T").d("msg")
        LogBufferTree.clear()

        assertTrue(LogBufferTree.entries.isEmpty())
    }

    @Test
    fun `revision bumps on log and on clear`() {
        val before = LogBufferTree.revision.value
        Timber.tag("T").d("msg")
        val afterLog = LogBufferTree.revision.value
        LogBufferTree.clear()
        val afterClear = LogBufferTree.revision.value

        assertTrue(afterLog > before)
        assertTrue(afterClear > afterLog)
    }

    @Test
    fun `priority names map to logcat letters`() {
        assertEquals("V", LogBufferTree.priorityName(2))
        assertEquals("D", LogBufferTree.priorityName(3))
        assertEquals("I", LogBufferTree.priorityName(4))
        assertEquals("W", LogBufferTree.priorityName(5))
        assertEquals("E", LogBufferTree.priorityName(6))
        assertEquals("A", LogBufferTree.priorityName(7))
        assertEquals("?", LogBufferTree.priorityName(99))
    }
}
