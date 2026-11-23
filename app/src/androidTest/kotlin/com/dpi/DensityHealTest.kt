package com.dpi

import android.content.res.Configuration
import android.content.res.Resources
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.roundToInt

/** Heal restores the display scale after it is wiped, including by a WebView load (#654). */
class DensityHealTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resources = context.resources
    private val nativeDpi = Resources.getSystem().configuration.densityDpi
    private val target = (nativeDpi * 0.65f).roundToInt()
    private val density = DensityConfiguration(0.65f)

    @Test
    fun healRestoresAWipedScale() = instrumentation.runOnMainSync {
        density.heal(resources)
        assertEquals(target, resources.configuration.densityDpi)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(Configuration(resources.configuration).apply { densityDpi = nativeDpi }, resources.displayMetrics)
        assertNotEquals(target, resources.configuration.densityDpi)

        density.heal(resources)
        assertEquals(target, resources.configuration.densityDpi)
    }

    @Test
    fun healHoldsTheScaleAcrossAWebViewLoad() = instrumentation.runOnMainSync {
        density.heal(resources)
        // Only the process's first WebView load drops the scale; the app's startup prewarm may have been first.
        WebView(context).destroy()
        density.heal(resources)
        assertEquals(target, resources.configuration.densityDpi)
    }
}
