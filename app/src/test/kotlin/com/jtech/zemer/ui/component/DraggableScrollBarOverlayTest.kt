package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class DraggableScrollBarOverlayTest {
    @Test
    fun `thumb travel is the track minus the thumb`() {
        assertEquals(400f, thumbTravel(viewportHeight = 500f, thumbHeight = 100f))
    }

    @Test
    fun `a track shorter than the thumb gives no travel instead of an empty range`() {
        val travel = thumbTravel(viewportHeight = 6.4f, thumbHeight = 100f)
        assertEquals(0f, travel)
        // the crash: coerceIn(0f, negative) threw IllegalArgumentException
        assertEquals(0f, 50f.coerceIn(0f, travel))
    }
}
