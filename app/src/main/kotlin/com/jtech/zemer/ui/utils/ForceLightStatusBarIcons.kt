package com.jtech.zemer.ui.utils

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.jtech.zemer.constants.DarkModeKey
import com.jtech.zemer.ui.screens.settings.DarkMode
import com.jtech.zemer.utils.rememberEnumPreference

/**
 * Forces LIGHT (white) status-bar icons for as long as the calling composable is in composition, then
 * hands the bar back to the theme-correct appearance — matching MainActivity.setSystemBarAppearance
 * (`isAppearanceLightStatusBars = !isDark`) — on dispose, never a stale snapshot. For full-bleed media
 * screens (the status story viewers) whose content runs behind the status bar: in light theme the
 * system clock/battery icons are otherwise dark over the media and vanish. The full player keeps its
 * own expansion-keyed variant (Player.kt) — this is the simple always-on case.
 */
@Composable
fun ForceLightStatusBarIcons() {
    val view = LocalView.current
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    DisposableEffect(useDarkTheme) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = false
            onDispose {
                WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !useDarkTheme
            }
        } else {
            onDispose { }
        }
    }
}
