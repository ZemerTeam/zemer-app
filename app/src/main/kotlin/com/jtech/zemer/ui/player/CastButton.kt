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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.CastEnabledKey
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.utils.rememberPreference

/**
 * Cast button overlaid on the player artwork (same pattern Metrolist uses): a radial-scrim-backed icon
 * that opens the FCast device picker in the shared menu bottom-sheet. Hidden unless casting is enabled in
 * settings (or a device is already connected). Tapping it starts the on-demand native-lib fetch + NSD
 * discovery lazily, then shows the picker (which reflects the download / search state).
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val service = playerConnection.service
    val menuState = LocalMenuState.current
    val connectedDevice by service.discoveryHandler.connectedDeviceFlow.collectAsState()
    val castEnabled by rememberPreference(CastEnabledKey, defaultValue = false)
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    if (!castEnabled && connectedDevice == null) return

    Box(modifier = modifier) {
        // Radial scrim so the icon stays legible over any artwork.
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .focusBorder(CircleShape)
                .clickable {
                    service.startDiscovery()
                    menuState.show { CastPicker(playerConnection, mediaMetadata) { menuState.dismiss() } }
                },
        ) {
            Icon(
                painter = painterResource(
                    if (connectedDevice != null) R.drawable.cast_connected else R.drawable.cast
                ),
                contentDescription = stringResource(R.string.cast_button_description),
                tint = if (connectedDevice != null) MaterialTheme.colorScheme.primary else tintColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
