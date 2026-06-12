package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtech.zemer.extensions.isInternetConnected
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.jtech.zemer.R

@Composable
fun NetworkRequiredDialog(
    isConnected: Boolean,
    onRetry: () -> Unit,
    onNetworkAvailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRetrying by remember { mutableStateOf(false) }
    var currentConnectionState by remember { mutableStateOf(isConnected) }

    // Continuously check for network connectivity
    LaunchedEffect(Unit) {
        while (!currentConnectionState) {
            delay(1000) // Check every second
            currentConnectionState = context.isInternetConnected()
            if (currentConnectionState) {
                delay(500) // Small delay to ensure stable connection
                onNetworkAvailable()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRetrying) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.network_checking_connection),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.network_required_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.network_required_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isRetrying = true
                        onRetry()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}