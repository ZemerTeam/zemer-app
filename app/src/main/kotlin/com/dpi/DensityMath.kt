package com.dpi

import kotlin.math.roundToInt

/**
 * The pure density-scaling decision — STATELESS, so it is trivially idempotent and cannot be
 * corrupted. The scaled density is applied via `Resources.updateConfiguration`, which the framework
 * silently wipes on every system configuration delivery (rotation — including the fullscreen-video
 * forced landscape — display-size changes, ...), so re-application fires from several hooks and must
 * be safe from any of them, any number of times.
 *
 * There is no memory of what was applied: the target is always computed from the CURRENT native
 * density (the caller reads it from `Resources.getSystem()`, which the framework owns, never lets
 * an app override, and keeps updated on real density changes), and a write happens exactly when the
 * Resources under repair isn't already at that target. A genuine system density change — including
 * one that lands exactly on a previously applied value — therefore rescales by construction.
 */
internal object DensityMath {
    /** The dpi to apply now, or null when [currentDpi] is already correct (or inputs are nonsense). */
    fun targetDpi(currentDpi: Int, nativeDpi: Int, scale: Float): Int? {
        if (currentDpi <= 0 || nativeDpi <= 0) return null
        val target = (nativeDpi * scale).roundToInt()
        return target.takeIf { it > 0 && it != currentDpi }
    }
}
