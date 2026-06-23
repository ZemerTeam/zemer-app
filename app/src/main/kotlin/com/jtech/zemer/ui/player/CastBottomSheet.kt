package com.jtech.zemer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.focusBorder
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
        LazyColumn {
            if (connectedDevice != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.stop_casting)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.cast_connected),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.focusBorder().clickable {
                            onDisconnect()
                            onDismiss()
                        }
                    )
                }
            }

            if (devices.isEmpty() && connectedDevice == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.cast_no_devices))
                    }
                }
            } else {
                items(devices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.cast),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.focusBorder().clickable {
                            onDeviceSelected(device, streamUrl, contentType, metadata)
                            onDismiss()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Standard host for the cast device picker: collects the discovered devices + active connection and
 * wires connect / disconnect, so the full player, mini-player, and queue don't each re-implement it.
 */
@Composable
fun CastSheet(
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    onDismiss: () -> Unit,
) {
    val service = playerConnection.service
    val handler = service.discoveryHandler
    val devices by handler.discoveredDevicesFlow.collectAsState()
    val connectedDevice by handler.connectedDeviceFlow.collectAsState()
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
            handler.connectTo(
                deviceInfo = deviceInfo,
                streamUrl = url,
                contentType = type,
                metadata = metadata,
                resumePosition = playerConnection.player.currentPosition / 1000.0,
                onTrackEnded = {
                    playerConnection.seekToNext()
                    playerConnection.player.play()
                }
            )
        },
        onDisconnect = { handler.disconnect() },
        onDismiss = onDismiss,
    )
}