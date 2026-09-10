package com.jtech.zemer.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickPicksPresentationTest {
    @Test
    fun `a small pool is shown whole and stable, a real library rotates`() {
        assertFalse(QuickPicksPresentation.rotates(0))
        assertFalse(QuickPicksPresentation.rotates(5))
        assertFalse(QuickPicksPresentation.rotates(QuickPicksPresentation.MIN_POOL_FOR_ROTATION - 1))
        assertTrue(QuickPicksPresentation.rotates(QuickPicksPresentation.MIN_POOL_FOR_ROTATION))
        assertTrue(QuickPicksPresentation.rotates(200))
    }

    @Test
    fun `local rows paint first on a cold start, but a refresh keeps the displayed rows until the final list`() {
        assertTrue(QuickPicksPresentation.showLocalRowsFirst(force = false, hasDisplayedRows = false))
        assertTrue(QuickPicksPresentation.showLocalRowsFirst(force = false, hasDisplayedRows = true))
        assertTrue(QuickPicksPresentation.showLocalRowsFirst(force = true, hasDisplayedRows = false))
        assertFalse(QuickPicksPresentation.showLocalRowsFirst(force = true, hasDisplayedRows = true))
    }

    @Test
    fun `displayed items keep their order and newcomers append in the new list's order`() {
        val previous = listOf("b", "a", "c")
        val next = listOf("x", "c", "a", "y", "b")
        assertEquals(listOf("b", "a", "c", "x", "y"), QuickPicksPresentation.keepDisplayedOrder(previous, next) { it })
    }

    @Test
    fun `items that left the pool simply drop out, nothing is re-added`() {
        val previous = listOf("gone", "a", "b")
        assertEquals(listOf("a", "b", "n"), QuickPicksPresentation.keepDisplayedOrder(previous, listOf("n", "b", "a")) { it })
    }

    @Test
    fun `first paint passes the shuffled list through untouched`() {
        val next = listOf("z", "y", "x")
        assertEquals(next, QuickPicksPresentation.keepDisplayedOrder(emptyList(), next) { it })
    }

    @Test
    fun `a duplicated previous id ranks by its first position`() {
        assertEquals(listOf("a", "b"), QuickPicksPresentation.keepDisplayedOrder(listOf("a", "b", "a"), listOf("b", "a")) { it })
    }
}
