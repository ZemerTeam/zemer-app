package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.jtech.zemer.ui.screens.settings.AccountSettings

@Composable
fun AccountSettingsDialog(
    navController: NavController,
    onDismiss: () -> Unit,
    latestVersionName: String
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onDismiss()
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                // A dialog is its own window: volume keys while casting need the overlay handler,
                // same as the Dialog.kt dialogs.
                modifier = Modifier
                    .then(castVolumeKeyModifier())
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(28.dp)),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AccountSettings(
                    navController = navController,
                    onClose = onDismiss,
                    latestVersionName = latestVersionName
                )
            }
        }
    }
}
