package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: backToMain popped one frame at a time, animating through every intermediate screen on a
 * long-press Back. It now resolves the target main route once (topmost main in the stack) and pops
 * straight there.
 */
class NavControllerUtilsTest {
    private val main = setOf("home", "library", "search")

    @Test
    fun `resolves the topmost main route in the stack`() {
        assertEquals("library", resolveBackToMainTarget(listOf("home", "library", "album", "playlist"), main))
    }

    @Test
    fun `resolves the base main route when only it is main`() {
        assertEquals("home", resolveBackToMainTarget(listOf("home", "album", "song"), main))
    }

    @Test
    fun `returns the current route when it is already a main route`() {
        assertEquals("search", resolveBackToMainTarget(listOf("home", "search"), main))
    }

    @Test
    fun `skips null routes and returns null when no main route is present`() {
        assertNull(resolveBackToMainTarget(listOf(null, "album", "playlist"), main))
    }
}
