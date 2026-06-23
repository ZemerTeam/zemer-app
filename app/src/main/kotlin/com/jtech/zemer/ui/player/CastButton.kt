package com.jtech.zemer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.CastEnabledKey
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MenuState
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.utils.rememberPreference

/**
 * The single launch path for the FCast device picker (shared by every cast button): start the on-demand
 * native-lib fetch + NSD discovery lazily, then open the picker in the shared menu bottom-sheet, reading
 * the current metadata at click time.
 */
fun openCastPicker(playerConnection: PlayerConnection, menuState: MenuState) {
    playerConnection.service.startDiscovery()
    menuState.show {
        CastPicker(playerConnection, playerConnection.mediaMetadata.value) { menuState.dismiss() }
    }
}

/** Shared state for every cast button: the connected flag and the click action. */
class CastButtonState(val connected: Boolean, val onClick: () -> Unit)

/**
 * Collects the cast-button state once — the connected-device flow + the enable pref — plus the launch
 * action. Returns null when the button must be hidden (no player connection, or casting disabled with
 * nothing connected), so each surface renders its own wrapper via `rememberCastButtonState()?.let { … }`.
 */
@Composable
fun rememberCastButtonState(): CastButtonState? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    val menuState = LocalMenuState.current
    val connectedDevice by playerConnection.service.discoveryHandler.connectedDeviceFlow.collectAsState()
    val castEnabled by rememberPreference(CastEnabledKey, defaultValue = false)
    if (!castEnabled && connectedDevice == null) return null
    return CastButtonState(connectedDevice != null) { openCastPicker(playerConnection, menuState) }
}

/** The cast glyph (connected vs not), shared by every cast-button surface. */
@Composable
fun CastIcon(connected: Boolean, idleTint: Color, size: Dp) {
    Icon(
        painter = painterResource(if (connected) R.drawable.cast_connected else R.drawable.cast),
        contentDescription = stringResource(R.string.cast_button_description),
        tint = if (connected) MaterialTheme.colorScheme.primary else idleTint,
        modifier = Modifier.size(size),
    )
}

/**
 * Cast button overlaid on the player artwork: a solid semi-opaque dark disc with a white icon, which stays
 * legible over **any** artwork (a radial black scrim vanished on dark album art — black on dark — hiding the
 * button). Opens the FCast device picker; hidden unless casting is enabled or a device is connected.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
) {
    val state = rememberCastButtonState() ?: return

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .focusBorder(CircleShape)
            .clickable(onClick = state.onClick),
    ) {
        CastIcon(connected = state.connected, idleTint = Color.White, size = 24.dp)
    }
}
