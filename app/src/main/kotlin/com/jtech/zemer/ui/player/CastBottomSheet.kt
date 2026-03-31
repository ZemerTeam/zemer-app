package com.jtech.zemer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    devices: List<DeviceInfo>,
    connectedDevice: CastingDevice?,
    onDeviceSelected: (DeviceInfo) -> Unit,
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
                                imageVector = Icons.Default.CastConnected,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
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
                                imageVector = Icons.Default.Cast,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable {
                            onDeviceSelected(device)
                            onDismiss()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}