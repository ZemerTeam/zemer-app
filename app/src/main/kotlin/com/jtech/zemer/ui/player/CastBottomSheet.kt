package com.jtech.zemer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import kotlinx.coroutines.launch
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.Metadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    devices: List<DeviceInfo>,
    connectedDevice: CastingDevice?,
    streamUrl: String? = null,
    contentType: String? = null,
    metadata: Metadata? = null,
    onDeviceSelected: (DeviceInfo, String?, String?, Metadata?) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.cast_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (devices.isEmpty() && connectedDevice == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.cast_no_devices))
            }
        } else {
            val rows = buildList {
                if (connectedDevice != null) {
                    add(
                        Material3MenuItemData(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.cast_connected),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            title = { Text(stringResource(R.string.stop_casting)) },
                            onClick = { onDisconnect(); onDismiss() },
                        )
                    )
                }
                devices.forEach { device ->
                    add(
                        Material3MenuItemData(
                            icon = { Icon(painter = painterResource(R.drawable.cast), contentDescription = null) },
                            title = { Text(device.name) },
                            onClick = { onDeviceSelected(device, streamUrl, contentType, metadata); onDismiss() },
                        )
                    )
                }
            }
            Material3MenuGroup(
                items = rows,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Standard host for the cast device picker: wires connect / disconnect, so the full player,
 * mini-player, and queue don't each re-implement it. The caller passes the device list + active
 * connection it already collects for its cast button (so the flows aren't collected twice).
 */
@Composable
fun CastSheet(
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    devices: List<DeviceInfo>,
    connectedDevice: CastingDevice?,
    onDismiss: () -> Unit,
) {
    val service = playerConnection.service
    val handler = service.discoveryHandler
    CastBottomSheet(
        devices = devices,
        connectedDevice = connectedDevice,
        streamUrl = service.currentStreamUrl,
        contentType = service.currentContentType,
        metadata = mediaMetadata?.let {
            Metadata(
                title = "${it.title} - ${it.artists.joinToString(", ") { a -> a.name }}",
                thumbnailUrl = it.thumbnailUrl
            )
        },
        onDeviceSelected = { deviceInfo, url, type, metadata ->
            playerConnection.player.pause()
            playerConnection.scope.launch {
                // Resolve the stream URL if it wasn't cached yet (e.g. casting a song that was never
                // played locally) — otherwise the device connects but plays nothing.
                val streamUrl = url
                    ?: playerConnection.player.currentMediaItem?.mediaId?.let { service.resolveStreamUrl(it) }
                handler.connectTo(
                    deviceInfo = deviceInfo,
                    streamUrl = streamUrl,
                    contentType = type ?: service.currentContentType,
                    metadata = metadata,
                    resumePosition = playerConnection.player.currentPosition / 1000.0,
                    onTrackEnded = {
                        playerConnection.seekToNext()
                        playerConnection.player.play()
                    }
                )
            }
        },
        onDisconnect = { handler.disconnect() },
        onDismiss = onDismiss,
    )
}
