package com.jtech.zemer.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.PureBlackKey
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

// The app's BRAND palette, used as the theme whenever the dynamic theme is OFF - on EVERY device, no more
// wallpaper-derived colors: a maroon/pink scheme instead of the generic default. [BrandFallbackSeed] seeds
// the whole scheme; the exact primary family below is pinned on the dark scheme so it matches the design.
// (With the dynamic toggle ON the album-art color is used on any version.)
private val BrandFallbackSeed = Color(0xFFFFAFB7)
private val BrandPrimaryDark = Color(0xFFFFAFB7)
private val BrandOnPrimaryDark = Color(0xFF5E1122)
private val BrandPrimaryContainerDark = Color(0xFF60383E)
private val BrandOnPrimaryContainerDark = Color(0xFFFFD9DD)

/**
 * Whether the UI should render AMOLED pure-black surfaces right now: the preference is on AND
 * dark theme is active (same derivation as MainActivity's theme setup). Use this instead of
 * reading PureBlackKey directly so light mode never goes pure-black.
 */
@Composable
fun rememberPureBlack(): Boolean {
    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val systemDark = isSystemInDarkTheme()
    return remember(pureBlackEnabled, darkMode, systemDark) {
        pureBlackEnabled && (if (darkMode == DarkMode.AUTO) systemDark else darkMode == DarkMode.ON)
    }
}

@Composable
fun ZemerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    // Dynamic theme OFF (the default sentinel) -> the app's BRAND palette on EVERY device (no
    // wallpaper-derived colors); dynamic ON -> the album-art seed.
    val useBrandFallback = (themeColor == DefaultThemeColor)

    // materialKolor from a seed: the album-art color (dynamic ON) or the app's brand seed (dynamic OFF).
    val baseColorScheme = rememberDynamicColorScheme(
        seedColor = if (useBrandFallback) BrandFallbackSeed else themeColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot // Keep existing style
    )

    // Neutralize surfaces to avoid overly tinted backgrounds from vivid seeds.
    val neutralDefaults = if (darkTheme) darkColorScheme() else lightColorScheme()
    val mergedColorScheme = baseColorScheme.copy(
        surface = neutralDefaults.surface,
        surfaceVariant = neutralDefaults.surfaceVariant,
        background = neutralDefaults.background,
        onSurface = neutralDefaults.onSurface,
        onSurfaceVariant = neutralDefaults.onSurfaceVariant,
        outline = neutralDefaults.outline,
        outlineVariant = neutralDefaults.outlineVariant,
    )

    // Pin the exact brand primary family on the dark fallback so it matches the design colors
    // (primary FFAFB7 / primaryContainer 60383E); the light fallback uses the seed-generated tones.
    val brandedColorScheme = if (useBrandFallback && darkTheme) {
        mergedColorScheme.copy(
            primary = BrandPrimaryDark,
            onPrimary = BrandOnPrimaryDark,
            primaryContainer = BrandPrimaryContainerDark,
            onPrimaryContainer = BrandOnPrimaryContainerDark,
        )
    } else {
        mergedColorScheme
    }

    // Apply pureBlack modification if needed
    val colorScheme = remember(brandedColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) brandedColorScheme.pureBlack(true) else brandedColorScheme
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Use the defined AppTypography
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
