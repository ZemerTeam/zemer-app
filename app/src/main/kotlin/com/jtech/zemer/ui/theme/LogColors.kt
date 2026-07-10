package com.jtech.zemer.ui.theme

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic logcat-style colors for the developer-mode Log viewer. These are deliberately
 * fixed hues (red/amber/green/blue), not colorScheme roles: the M3 tonal palette derives
 * primary/secondary/tertiary from ONE seed hue, so priority coloring through scheme roles
 * renders as indistinguishable same-hue tints. Defined in the theme layer (the one place
 * literal colors are allowed) with per-surface variants: brighter on dark, deeper on light.
 */
@Composable
fun logPriorityColor(priority: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (priority) {
        Log.ERROR, Log.ASSERT -> if (dark) Color(0xFFFF6B68) else Color(0xFFC62828)
        Log.WARN -> if (dark) Color(0xFFFFC66D) else Color(0xFFB26A00)
        Log.INFO -> if (dark) Color(0xFF7FBF7F) else Color(0xFF2E7D32)
        Log.DEBUG -> if (dark) Color(0xFF6A9FD8) else Color(0xFF1565C0)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
