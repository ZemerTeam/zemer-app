package com.jtech.zemer.utils

import kotlin.math.abs

/**
 * The minimal shape of an `android.view.Display.Mode` the refresh-rate picker needs, so the selection
 * logic is pure and JVM-testable (Display.Mode can't be constructed off-device).
 */
data class DisplayModeInfo(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
)

/**
 * Picks the display mode the "Enable high refresh rate" setting should request. Modes are constrained to
 * the current physical resolution FIRST so forcing the highest rate never silently drops the panel to a
 * lower resolution that merely happens to refresh faster.
 *
 * - [high] = true selects the HIGHEST refresh rate at the current resolution (the whole point of the
 *   setting - the earlier `preferredDisplayModeId = 0` meant "system default", which left a 120Hz-capable
 *   panel at its 60Hz default and was a no-op).
 * - [high] = false pins ~60Hz (an exact 60Hz mode if present, else the closest).
 *
 * Returns the chosen mode, or null when there are no modes to choose from (leave the window preference
 * untouched).
 */
fun selectRefreshRateMode(
    modes: List<DisplayModeInfo>,
    current: DisplayModeInfo?,
    high: Boolean,
): DisplayModeInfo? {
    val atCurrentResolution = modes.filter { m ->
        current == null || (m.physicalWidth == current.physicalWidth && m.physicalHeight == current.physicalHeight)
    }
    return if (high) {
        atCurrentResolution.maxByOrNull { it.refreshRate }
    } else {
        atCurrentResolution.firstOrNull { abs(it.refreshRate - 60f) < 1f }
            ?: atCurrentResolution.minByOrNull { abs(it.refreshRate - 60f) }
    }
}
