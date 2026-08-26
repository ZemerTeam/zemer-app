package com.dpi

import kotlin.math.roundToInt

/**
 * The pure density-scaling decision, extracted so it is unit-testable and IDEMPOTENT. The scaled
 * density is applied via `Resources.updateConfiguration`, which the framework silently wipes on
 * every system configuration delivery (rotation — including the fullscreen-video forced landscape —
 * display-size changes, ...), so re-application must be safe to run at any time, from any hook:
 *
 * - the incoming dpi equal to the LAST APPLIED value means our scale is still in place -> no-op
 *   (returning a value would compound the scale on every call);
 * - any other incoming dpi is treated as the CURRENT NATIVE base — the startup value, a value the
 *   framework just reset, or a genuinely new system density (user changed Display size) — and the
 *   scale is computed from it, never from a density captured once at startup;
 * - a scale that rounds to the incoming dpi itself is a no-op (avoids a pointless update).
 */
internal object DensityMath {
    /** The dpi to apply now, or null when nothing should be written. */
    fun targetDpi(currentDpi: Int, lastAppliedDpi: Int?, scale: Float): Int? {
        if (currentDpi <= 0) return null
        if (currentDpi == lastAppliedDpi) return null
        val target = (currentDpi * scale).roundToInt()
        return target.takeIf { it > 0 && it != currentDpi }
    }
}
