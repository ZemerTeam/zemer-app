package com.jtech.zemer.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import android.os.Build
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.constants.DynamicThemeKey
import com.jtech.zemer.constants.PureBlackKey
import com.jtech.zemer.constants.SelectedThemeColorKey
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.theme.BrandThemeColor
import com.jtech.zemer.ui.theme.SystemWallpaperThemeColor
import com.jtech.zemer.ui.theme.DefaultAccentColor
import com.jtech.zemer.ui.theme.ThemePalette
import com.jtech.zemer.ui.theme.ThemePaletteSelection
import com.jtech.zemer.ui.theme.ZemerTheme
import com.jtech.zemer.ui.theme.visiblePaletteColors
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * Settings -> Theme & Colors: the app's theme picker. A live mockup preview sits above a card with a
 * Theme Mode row (system / light / dark / pure-black) and a Color Palette row (the dynamic album-art
 * palette plus every fixed accent). The brand accent is the default selection; picking any accent
 * turns the dynamic palette off, picking the dynamic entry turns it on ("dynamic wins when ON").
 *
 * Selection logic is the pure [ThemePaletteSelection]; all color literals live in `ui/theme/`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val systemDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        defaultValue = DefaultAccentColor.toArgb(),
    )
    val (dynamicEnabled, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = false)

    val selectedThemeColor = Color(selectedThemeColorInt)

    val onPalettePicked: (ThemePalette) -> Unit = { palette ->
        val selection = ThemePaletteSelection.onPicked(palette)
        selection.seedColor?.let { onSelectedThemeColorChange(it.toArgb()) }
        onDynamicThemeChange(selection.dynamicEnabled)
    }

    val palettes = remember(systemDynamicSupported) { visiblePaletteColors(systemDynamicSupported) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top spacer clears the overlaying app bar; the whole screen scrolls so it stays usable on
        // short screens (the mockup + full palette can exceed the viewport).
        Spacer(modifier = Modifier.height(80.dp))

        Box(
            modifier = Modifier
                .width(120.dp)
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            ThemeMockup(
                darkMode = darkMode,
                pureBlack = pureBlack,
                themeColor = selectedThemeColor,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ThemeControls(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            palettes = palettes,
            selectedThemeColor = selectedThemeColor,
            dynamicEnabled = dynamicEnabled,
            systemDynamicSupported = systemDynamicSupported,
            onPalettePicked = onPalettePicked,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    BackTopAppBar(
        title = { AppBarTitle(stringResource(R.string.theme_colors)) },
        navController = navController,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ThemeControls(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    palettes: List<ThemePalette>,
    selectedThemeColor: Color,
    dynamicEnabled: Boolean,
    systemDynamicSupported: Boolean,
    onPalettePicked: (ThemePalette) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // System (AUTO)
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.AUTO,
                        targetPureBlack = false,
                        showIcon = true,
                        onClick = { onDarkModeChange(DarkMode.AUTO) },
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )

                    // Light
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.OFF,
                        targetPureBlack = false,
                        showIcon = false,
                        onClick = {
                            onDarkModeChange(DarkMode.OFF)
                            onPureBlackChange(false)
                        },
                    )

                    // Dark
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = false,
                        showIcon = false,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(false)
                        },
                    )

                    // Pure black
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = true,
                        showIcon = false,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(true)
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.color_palette),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(palettes) { palette ->
                        Column(
                            modifier = Modifier.width(64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PaletteItem(
                                palette = palette,
                                isSelected = ThemePaletteSelection.isSelected(
                                    palette = palette,
                                    selectedColor = selectedThemeColor,
                                    dynamicEnabled = dynamicEnabled,
                                ),
                                systemDynamicSupported = systemDynamicSupported,
                                onClick = { onPalettePicked(palette) },
                            )
                            Text(
                                text = stringResource(palette.nameRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCircle(
    darkMode: DarkMode,
    pureBlack: Boolean,
    targetMode: DarkMode,
    targetPureBlack: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isSelected = darkMode == targetMode && pureBlack == targetPureBlack

    val effectiveDark = when (targetMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    // Preview fill from the brand seed (the app has no system wallpaper path).
    val modeColorScheme = rememberDynamicColorScheme(
        seedColor = BrandThemeColor,
        isDark = effectiveDark,
        style = PaletteStyle.TonalSpot,
    )
    val fillColor = if (targetPureBlack) Color.Black else modeColorScheme.surface

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "borderWidth",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )

    val interactionSource = remember { MutableInteractionSource() }

    val contentDesc = when {
        targetPureBlack -> stringResource(R.string.cd_pure_black_mode)
        targetMode == DarkMode.OFF -> stringResource(R.string.cd_light_mode)
        targetMode == DarkMode.ON -> stringResource(R.string.cd_dark_mode)
        else -> stringResource(R.string.cd_system_mode)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusBorder(CircleShape)
            .clip(CircleShape)
            .background(fillColor)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(width = borderWidth, color = MaterialTheme.colorScheme.inversePrimary, shape = CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        when {
            showIcon -> Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                tint = modeColorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )

            isSelected -> AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                ),
                exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.3f, animationSpec = tween(150)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PaletteItem(
    palette: ThemePalette,
    isSelected: Boolean,
    systemDynamicSupported: Boolean,
    onClick: () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDynamic = palette.seedColor == Color.Transparent
    val isSystem = palette.seedColor == SystemWallpaperThemeColor
    val context = LocalContext.current

    // The System swatch previews the real Android 12+ wallpaper colors; the dynamic swatch is an icon
    // (no fixed colors to show); every other swatch previews its materialKolor scheme.
    val colorScheme = if (isSystem && systemDynamicSupported) {
        if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = if (isDynamic || isSystem) BrandThemeColor else palette.seedColor,
            isDark = isSystemDark,
            style = PaletteStyle.TonalSpot,
        )
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "cornerRadius",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "borderWidth",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )

    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val contentDesc = stringResource(R.string.cd_palette_item, stringResource(palette.nameRes))

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusBorder(shape)
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(width = borderWidth, color = MaterialTheme.colorScheme.inversePrimary, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick)
            .semantics { contentDescription = contentDesc },
    ) {
        if (isDynamic) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(color = colorScheme.onPrimary, topLeft = Offset(0f, 0f), size = Size(w, h / 2))
                drawRect(color = colorScheme.secondary, topLeft = Offset(0f, h / 2), size = Size(w / 2, h / 2))
                drawRect(color = colorScheme.tertiary, topLeft = Offset(w / 2, h / 2), size = Size(w / 2, h / 2))
            }
        }
    }
}

/** A small phone-shaped preview that renders the picked theme so changes are visible before applying. */
@Composable
private fun ThemeMockup(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    ZemerTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor,
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (~20%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                    }
                }

                // Content (~60%)
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp)),
                        )
                    }
                }

                // FAB (~20%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(modifier = Modifier.size(18.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
                }
            }
        }
    }
}
