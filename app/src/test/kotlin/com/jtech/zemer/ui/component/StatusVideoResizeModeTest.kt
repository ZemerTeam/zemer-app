package com.jtech.zemer.ui.component

import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusVideoResizeModeTest {

    @Test
    fun `landscape and square media on a portrait screen fit, portrait media zooms, unknown zooms`() {
        val fit = AspectRatioFrameLayout.RESIZE_MODE_FIT
        val zoom = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        assertEquals(fit, statusVideoResizeMode(1920f, 1080f, 1080f, 2400f))
        assertEquals(fit, statusVideoResizeMode(1080f, 1080f, 1080f, 2400f))
        // A 9:16 portrait post on a taller 9:20 phone still fills (never letterboxed).
        assertEquals(zoom, statusVideoResizeMode(720f, 1280f, 1080f, 2400f))
        // Landscape media on a landscape surface that is even wider -> fill.
        assertEquals(zoom, statusVideoResizeMode(1920f, 1080f, 2400f, 1080f))
        assertEquals(zoom, statusVideoResizeMode(0f, 0f, 1080f, 2400f))
    }
}
