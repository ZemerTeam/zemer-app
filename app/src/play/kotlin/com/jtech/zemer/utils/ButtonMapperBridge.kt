package com.jtech.zemer.utils

import com.jtech.zemer.MainActivity

/**
 * Play flavor: no-op button-mapper bridge.
 *
 * The accessibility button-mapper is not shipped on Play (Accessibility API policy), so there is no
 * AccessibilityService feeding key events. MainActivity still calls register/unregister in its
 * lifecycle; both are no-ops here. See docs/play/PLAN.md §1.2.
 */
object ButtonMapperBridge {
    fun register(activity: MainActivity) { /* no-op on Play */ }
    fun unregister(activity: MainActivity) { /* no-op on Play */ }
}
