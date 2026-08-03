package com.jtech.zemer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure regression tests for the Theme & Colors palette selection contract (no Android runtime).
 * Guards the "dynamic wins when ON" model and the accent-match highlighting the picker depends on.
 */
class ThemePaletteSelectionTest {
    private val dynamic = ThemePalette(nameRes = 0, seedColor = Color.Transparent)
    private val brand = ThemePalette(nameRes = 0, seedColor = BrandThemeColor)
    private val blue = ThemePalette(nameRes = 0, seedColor = Color(0xFF1E88E5))

    @Test
    fun `dynamic entry is selected exactly when dynamic is enabled`() {
        assertTrue(ThemePaletteSelection.isSelected(dynamic, selectedColor = BrandThemeColor, dynamicEnabled = true))
        assertFalse(ThemePaletteSelection.isSelected(dynamic, selectedColor = BrandThemeColor, dynamicEnabled = false))
    }

    @Test
    fun `a color entry is selected only when dynamic is off and the accent matches`() {
        assertTrue(ThemePaletteSelection.isSelected(brand, selectedColor = BrandThemeColor, dynamicEnabled = false))
        // Dynamic on: no fixed swatch is highlighted, not even the stored accent.
        assertFalse(ThemePaletteSelection.isSelected(brand, selectedColor = BrandThemeColor, dynamicEnabled = true))
        // A different stored accent leaves this swatch unselected.
        assertFalse(ThemePaletteSelection.isSelected(blue, selectedColor = BrandThemeColor, dynamicEnabled = false))
    }

    @Test
    fun `picking the dynamic entry turns dynamic on and preserves the stored accent`() {
        val selection = ThemePaletteSelection.onPicked(dynamic)
        assertTrue(selection.dynamicEnabled)
        assertNull(selection.seedColor)
    }

    @Test
    fun `picking a color entry turns dynamic off and sets that accent`() {
        val selection = ThemePaletteSelection.onPicked(blue)
        assertFalse(selection.dynamicEnabled)
        assertEquals(Color(0xFF1E88E5), selection.seedColor)
    }

    @Test
    fun `default accent is the brand palette`() {
        assertEquals(BrandThemeColor, DefaultAccentColor)
    }

    @Test
    fun `system entry is filtered out when unsupported`() {
        val supported = visiblePaletteColors(systemDynamicSupported = true)
        val unsupported = visiblePaletteColors(systemDynamicSupported = false)
        assertTrue(supported.any { it.seedColor == SystemWallpaperThemeColor })
        assertFalse(unsupported.any { it.seedColor == SystemWallpaperThemeColor })
    }

    @Test
    fun `pureBlack drives every surface token to black but keeps the accent`() {
        val scheme = darkColorScheme(primary = Color(0xFFFFAFB7))
        val black = scheme.pureBlack(true)
        listOf(
            black.surface, black.background, black.surfaceVariant, black.surfaceDim, black.surfaceBright,
            black.surfaceContainerLowest, black.surfaceContainerLow, black.surfaceContainer,
            black.surfaceContainerHigh, black.surfaceContainerHighest,
        ).forEach { assertEquals(Color.Black, it) }
        // Accent slot is deliberately untouched (the theme still shows through it on the black canvas).
        assertEquals(Color(0xFFFFAFB7), black.primary)
    }

    @Test
    fun `pureBlack is a no-op when not applied`() {
        val scheme = darkColorScheme()
        assertEquals(scheme.surface, scheme.pureBlack(false).surface)
    }
}
