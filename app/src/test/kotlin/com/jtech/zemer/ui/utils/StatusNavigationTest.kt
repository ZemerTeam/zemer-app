package com.jtech.zemer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNavigationTest {
    @Test
    fun `storyRoute carries the creator id`() {
        assertEquals("story/cd98ac88-528f-473e-8939-eb8f56cbcc35", storyRoute("cd98ac88-528f-473e-8939-eb8f56cbcc35"))
    }
}
