package com.jtech.zemer.utils

import com.jtech.zemer.constants.RefreshRateMode
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
 * Picks the display mode the refresh-rate setting should request, for any device's mode set (60-only,
 * 60/90, 60/120, 48/60/90/120 LTPO, 144, odd rates). Candidate modes are constrained to the current
 * physical resolution FIRST so a higher rate never silently drops the panel to a lower resolution that
 * merely happens to refresh faster.
 *
 * - [RefreshRateMode.SYSTEM] returns null (no forced mode): the window leaves `preferredDisplayModeId`
 *   at 0 so the OS runs its own adaptive refresh (high while interacting, low when idle).
 * - [RefreshRateMode.HIGH] selects the HIGHEST refresh rate at the current resolution.
 * - [RefreshRateMode.STANDARD] pins ~60Hz (an exact 60Hz mode if present, else the closest available).
 *
 * Returns null for SYSTEM and when there are no modes to choose from.
 */
fun selectRefreshRateMode(
    modes: List<DisplayModeInfo>,
    current: DisplayModeInfo?,
    mode: RefreshRateMode,
): DisplayModeInfo? {
    if (mode == RefreshRateMode.SYSTEM) return null
    val atCurrentResolution = modes.filter { m ->
        current == null || (m.physicalWidth == current.physicalWidth && m.physicalHeight == current.physicalHeight)
    }
    return when (mode) {
        RefreshRateMode.HIGH -> atCurrentResolution.maxByOrNull { it.refreshRate }
        RefreshRateMode.STANDARD ->
            atCurrentResolution.firstOrNull { abs(it.refreshRate - 60f) < 1f }
                ?: atCurrentResolution.minByOrNull { abs(it.refreshRate - 60f) }
        RefreshRateMode.SYSTEM -> null
    }
}

/**
 * The `WindowManager.LayoutParams.preferredRefreshRate` to request on pre-R devices (which can ask for
 * a rate but not a mode id). A resolved [chosen] mode uses its rate; with no mode, STANDARD still hints
 * 60f while SYSTEM/HIGH ask for 0f (no preference - let the platform decide).
 */
fun preferredRefreshRateHz(mode: RefreshRateMode, chosen: DisplayModeInfo?): Float =
    chosen?.refreshRate ?: if (mode == RefreshRateMode.STANDARD) 60f else 0f

/**
 * One-time migration from the legacy boolean [com.jtech.zemer.constants.EnableHighRefreshRateKey] to
 * [RefreshRateMode]. An explicit OFF (a deliberate battery choice) is preserved as STANDARD; ON or
 * unset maps to the new SYSTEM default - the old default was ON, which force-pinned the highest rate,
 * so keeping those users on HIGH would just carry the aggressive old default forward. Null = the old
 * key was never written.
 */
fun migrateRefreshRateMode(legacyHighRefreshEnabled: Boolean?): RefreshRateMode =
    if (legacyHighRefreshEnabled == false) RefreshRateMode.STANDARD else RefreshRateMode.SYSTEM

/**
 * The value to write to `WindowManager.LayoutParams.preferredDisplayModeId` (API >= R) for a chosen
 * [mode]. A null [mode] (no modes to pick from) maps to 0 = "system default", which CLEARS any
 * previously-forced mode - the earlier code left the stale high mode in place when selection returned
 * null, so toggling the setting off while the display briefly reported no modes never reset the panel.
 */
fun preferredDisplayModeId(mode: DisplayModeInfo?): Int = mode?.modeId ?: 0
