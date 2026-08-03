package com.jtech.zemer.ui.theme

import androidx.compose.ui.graphics.Color
import com.jtech.zemer.R

/**
 * One selectable entry in Settings -> Theme & Colors' Color Palette row.
 *
 * [seedColor] seeds the whole Material scheme (via materialKolor) when the entry is picked.
 * [Color.Transparent] is the sentinel for the "dynamic" palette, which is not a fixed seed at all:
 * it enables album-art theming (DynamicThemeKey) and is selected/deselected by that flag rather than
 * by matching a color. Every other entry is a concrete accent the user can pin.
 *
 * Color literals deliberately live in `ui/theme/` (exempt from the ui-audit R8-hex ratchet) so the
 * picker screen under `ui/screens/` stays free of hardcoded colors.
 */
data class ThemePalette(
    val nameRes: Int,
    val seedColor: Color,
)

/**
 * The app's brand seed (maroon/pink) and the default selected accent. materialKolor's generated tones
 * drift from the exact design hexes, so [ZemerTheme] pins the primary family below on the dark scheme
 * whenever this seed is active; the light scheme uses the seed-generated tones.
 */
val BrandThemeColor = Color(0xFFFFAFB7)
internal val BrandPrimaryDark = Color(0xFFFFAFB7)
internal val BrandOnPrimaryDark = Color(0xFF5E1122)
internal val BrandPrimaryContainerDark = Color(0xFF60383E)
internal val BrandOnPrimaryContainerDark = Color(0xFFFFD9DD)

/**
 * Sentinel accent for the "System" palette: Android 12+ wallpaper-based Material You colors. It is not
 * a real seed (real accents are all opaque, 0xFFrrggbb); [ZemerTheme] special-cases this exact value to
 * the platform dynamic scheme. Distinct from [Color.Transparent], which the "dynamic" (album-art) entry
 * uses for display/selection. The picker only offers this entry on devices that support it.
 */
val SystemWallpaperThemeColor = Color(0x00000001)

/**
 * The Color Palette row, in display order: the dynamic (album-art) sentinel first, the brand accent
 * (default) second, then a spectrum of accents. The crimson entry is nudged off [DefaultThemeColor]
 * (0xFFED5564) so no swatch collides with that legacy sentinel.
 */
val PaletteColors: List<ThemePalette> = listOf(
    ThemePalette(R.string.palette_dynamic, Color.Transparent),
    ThemePalette(R.string.palette_system, SystemWallpaperThemeColor),
    ThemePalette(R.string.palette_brand, BrandThemeColor),
    ThemePalette(R.string.palette_crimson, Color(0xFFEC5464)),
    ThemePalette(R.string.palette_rose, Color(0xFFD81B60)),
    ThemePalette(R.string.palette_purple, Color(0xFF8E24AA)),
    ThemePalette(R.string.palette_deep_purple, Color(0xFF5E35B1)),
    ThemePalette(R.string.palette_indigo, Color(0xFF3949AB)),
    ThemePalette(R.string.palette_blue, Color(0xFF1E88E5)),
    ThemePalette(R.string.palette_sky_blue, Color(0xFF039BE5)),
    ThemePalette(R.string.palette_cyan, Color(0xFF00ACC1)),
    ThemePalette(R.string.palette_teal, Color(0xFF00897B)),
    ThemePalette(R.string.palette_green, Color(0xFF43A047)),
    ThemePalette(R.string.palette_light_green, Color(0xFF7CB342)),
    ThemePalette(R.string.palette_lime, Color(0xFFC0CA33)),
    ThemePalette(R.string.palette_yellow, Color(0xFFFDD835)),
    ThemePalette(R.string.palette_amber, Color(0xFFFFB300)),
    ThemePalette(R.string.palette_orange, Color(0xFFFB8C00)),
    ThemePalette(R.string.palette_deep_orange, Color(0xFFF4511E)),
    ThemePalette(R.string.palette_brown, Color(0xFF6D4C41)),
    ThemePalette(R.string.palette_grey, Color(0xFF757575)),
    ThemePalette(R.string.palette_blue_grey, Color(0xFF546E7A)),
)

/**
 * The default accent for a fresh install: the Zemer brand palette, on every device. (System/wallpaper
 * and the other accents stay available in the picker; they are just not the default.) This is the
 * value stored under `SelectedThemeColorKey` when the user has never picked one.
 */
val DefaultAccentColor: Color = BrandThemeColor

/**
 * The palette entries to display. The System (wallpaper) entry is offered only on devices whose
 * platform supports dynamic wallpaper colors (Android 12+); everywhere else it is dropped.
 */
fun visiblePaletteColors(systemDynamicSupported: Boolean): List<ThemePalette> =
    if (systemDynamicSupported) PaletteColors
    else PaletteColors.filterNot { it.seedColor == SystemWallpaperThemeColor }

/**
 * Pure selection logic for the Color Palette row, extracted from the picker so it is unit-testable
 * without an Android runtime. The "dynamic" entry (a [Color.Transparent] seed) is driven by the
 * `dynamicEnabled` flag; every other entry (including the System wallpaper sentinel) by an exact
 * accent match.
 */
object ThemePaletteSelection {
    private val ThemePalette.isDynamic: Boolean get() = seedColor == Color.Transparent

    /** Whether [palette] is the currently active selection given the persisted accent + dynamic flag. */
    fun isSelected(palette: ThemePalette, selectedColor: Color, dynamicEnabled: Boolean): Boolean =
        if (palette.isDynamic) dynamicEnabled else !dynamicEnabled && palette.seedColor == selectedColor

    /**
     * The preference change a tap on [palette] should apply. [seedColor] is the accent to persist, or
     * null to leave the stored accent untouched (the dynamic entry keeps the last accent as its
     * idle/no-artwork fallback). [dynamicEnabled] is the album-art flag to write.
     */
    data class Selection(val seedColor: Color?, val dynamicEnabled: Boolean)

    fun onPicked(palette: ThemePalette): Selection =
        if (palette.isDynamic) Selection(seedColor = null, dynamicEnabled = true)
        else Selection(seedColor = palette.seedColor, dynamicEnabled = false)
}
