package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The app's standard D-pad focus treatment for a clickable row/card: an animated `surfaceVariant`
 * background and `outline` border that appear when the element is focused. Apply it BEFORE the
 * `.clickable {}` in the chain so the ripple is clipped to [shape]:
 *
 * ```
 * Modifier.fillMaxWidth().focusBorder(shape).clickable { ... }.padding(...)
 * ```
 *
 * This is the single source of truth for the focus border that `Material3MenuItemRow`,
 * `Material3SettingsItemRow` and `PreferenceEntry` each used to hand-roll — upstream (Metrolist)
 * rows omit it, ours must not (docs/ui/standards.md section 11).
 */
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val showRing = isFocused && focusVisualsEnabled()
    val backgroundColor by animateColorAsState(
        targetValue = if (showRing) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "focus_border_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (showRing) MaterialTheme.colorScheme.outline else Color.Transparent,
        label = "focus_border_outline",
    )
    this
        .clip(shape)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .background(backgroundColor)
        .border(width = 1.5.dp, color = borderColor, shape = shape)
}

/**
 * Whether focus visuals (rings/borders/fills) should render at all: true only while the session's
 * LAST input was a key (D-pad / TV remote / keyboard), the one audience the treatment exists for.
 * Touch users kept seeing rings wherever screens programmatically request initial focus for D-pad
 * convenience - on a touchscreen those are pure noise, so touch mode suppresses the VISUAL while
 * focus itself (and every focusRequester/bringIntoView behavior) is untouched. The input mode is
 * snapshot-backed, so a first key press flips the visuals on immediately.
 */
@Composable
fun focusVisualsEnabled(): Boolean =
    LocalInputModeManager.current.inputMode == InputMode.Keyboard
