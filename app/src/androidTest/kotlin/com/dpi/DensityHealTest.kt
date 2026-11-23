package com.dpi

import android.content.res.Resources
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.roundToInt

/** The density override is lost when a WebView first loads in the process, and heal restores it (#654). */
class DensityHealTest {
    @Test
    fun aWebViewLoadDropsTheScaleAndHealRestoresIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resources = context.resources
        val target = (Resources.getSystem().configuration.densityDpi * 0.65f).roundToInt()
        val density = DensityConfiguration(0.65f)
        instrumentation.runOnMainSync {
            density.heal(resources)
            assertEquals(target, resources.configuration.densityDpi)

            WebView(context).destroy()
            assertNotEquals("the WebView load is what drops the scale", target, resources.configuration.densityDpi)

            density.heal(resources)
            assertEquals(target, resources.configuration.densityDpi)
        }
    }
}
