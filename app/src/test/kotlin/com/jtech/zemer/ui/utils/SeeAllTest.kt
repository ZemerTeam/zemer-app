package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the shared "See all" arrow threshold used by the artist page + podcast channel page. */
class SeeAllTest {

    @Test
    fun `the arrow is hidden below the minimum`() {
        assertFalse(shouldShowSeeAll(0))
        assertFalse(shouldShowSeeAll(1))
        assertFalse(shouldShowSeeAll(SEE_ALL_MIN_ITEMS - 1))
    }

    @Test
    fun `the arrow shows at and above the minimum`() {
        assertTrue(shouldShowSeeAll(SEE_ALL_MIN_ITEMS))
        assertTrue(shouldShowSeeAll(SEE_ALL_MIN_ITEMS + 1))
        assertTrue(shouldShowSeeAll(500))
    }

    @Test
    fun `the minimum is four`() {
        assertEquals(4, SEE_ALL_MIN_ITEMS)
    }

    @Test
    fun `seeAllOnClick returns null below the minimum and the action at or above`() {
        val action = {}
        assertNull(seeAllOnClick(SEE_ALL_MIN_ITEMS - 1, action))
        assertSame(action, seeAllOnClick(SEE_ALL_MIN_ITEMS, action))
        assertSame(action, seeAllOnClick(SEE_ALL_MIN_ITEMS + 10, action))
    }
}
