package com.jtech.zemer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavItemsTest {
    private val fallback = "home,search,library"

    @Test
    fun stripsArtistsAndPreservesOrder() {
        assertEquals(
            "home,search,library",
            removeBottomNavItem("home,artists,search,library", "artists", fallback),
        )
    }

    @Test
    fun stripsArtistsFromAnyPosition() {
        assertEquals("home,search,library", removeBottomNavItem("artists,home,search,library", "artists", fallback))
        assertEquals("home,search,library", removeBottomNavItem("home,search,library,artists", "artists", fallback))
    }

    @Test
    fun keepsCustomOrderAndOtherTabs() {
        assertEquals(
            "library,kid_zone,search,home",
            removeBottomNavItem("library,kid_zone,artists,search,home", "artists", fallback),
        )
    }

    @Test
    fun noOpWhenKeyAbsent() {
        assertEquals("home,search,library", removeBottomNavItem("home,search,library", "artists", fallback))
    }

    @Test
    fun trimsWhitespaceEntries() {
        assertEquals("home,search", removeBottomNavItem(" home , artists , search ", "artists", fallback))
    }

    @Test
    fun fallsBackWhenRemovingLeavesEmpty() {
        assertEquals(fallback, removeBottomNavItem("artists", "artists", fallback))
        assertEquals(fallback, removeBottomNavItem("artists, ,artists", "artists", fallback))
    }
}
