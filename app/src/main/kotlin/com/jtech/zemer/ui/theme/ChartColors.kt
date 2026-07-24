package com.jtech.zemer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic climb/fall colors for chart-movement badges, defined here for the same reason as
 * [logPriorityColor]: the M3 tonal palette derives primary/secondary/tertiary from ONE seed hue, so
 * a scheme role cannot be relied on to read as "green". On a dynamic-color device `tertiary` follows
 * the wallpaper and can land on red — actively wrong for a song that climbed.
 *
 * The pair is defined together so up and down stay visually balanced; using a fixed green against
 * `colorScheme.error` would leave them tuned to different palettes. Brighter on dark, deeper on
 * light, matching the log viewer's variants.
 *
 * Color is never the only carrier of meaning here — the badge also renders an arrow glyph, a number,
 * and a spoken description — so these stay legible for color-blind users.
 */
@Composable
fun chartClimbColor(): Color =
    if (isDarkSurface()) Color(0xFF7FBF7F) else Color(0xFF2E7D32)

@Composable
fun chartFallColor(): Color =
    if (isDarkSurface()) Color(0xFFFF6B68) else Color(0xFFC62828)

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
