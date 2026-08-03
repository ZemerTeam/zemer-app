package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNavigationTest {
    @Test
    fun `storyRoute carries the creator id`() {
        assertEquals("story/cd98ac88-528f-473e-8939-eb8f56cbcc35", storyRoute("cd98ac88-528f-473e-8939-eb8f56cbcc35"))
    }

    @Test
    fun `savedStatusRoute carries the creator id, with optional start`() {
        assertEquals("saved_status/c1", savedStatusRoute("c1"))
        assertEquals("saved_status/c1?start=p9", savedStatusRoute("c1", "p9"))
    }
}
