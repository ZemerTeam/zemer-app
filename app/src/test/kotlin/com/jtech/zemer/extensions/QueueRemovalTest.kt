package com.jtech.zemer.extensions

import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRemovalTest {
    /** A queue whose windows carry these uids, in order. */
    private class Queue(private val uids: List<Any>) : Timeline() {
        override fun getWindowCount() = uids.size
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.apply { uid = uids[windowIndex] }
        override fun getPeriodCount() = uids.size
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period = period
        override fun getIndexOfPeriod(uid: Any) = uids.indexOf(uid)
        override fun getUidOfPeriod(periodIndex: Int): Any = uids[periodIndex]
    }

    @Test
    fun `selected items are found in the live queue, highest index first`() {
        val queue = Queue(listOf("a", "b", "c", "d", "e"))
        // tap order: d, then b
        assertEquals(listOf(3, 1), queue.windowIndicesOf(setOf("d", "b")))
    }

    @Test
    fun `a queue that moved after the selection still removes the selected items only`() {
        // "a" ended and was dropped, radio appended "f": the old indices of b and e are off by one
        val moved = Queue(listOf("b", "c", "d", "e", "f"))
        assertEquals(listOf(3, 0), moved.windowIndicesOf(setOf("e", "b")))
        // an item already gone is skipped, never an out-of-range index
        assertEquals(emptyList<Int>(), moved.windowIndicesOf(setOf("a")))
    }
}
