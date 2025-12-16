package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtech.zemer.auth.WebViewGoogleAuthManager
import kotlinx.coroutines.launch

/**
 * A simple dialog for creating an anonymous account for sync.
 *
 * @param authManager WebViewGoogleAuthManager instance
 * @param onDismiss Callback when dialog is dismissed
 * @param onAuthSuccess Callback when authentication succeeds
 * @param onAuthError Callback with exception when authentication fails
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousAuthEmailDialog(
    authManager: WebViewGoogleAuthManager,
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (Exception) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                Text(
                    text = "Creating account...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Text(
                    text = "Create Sync Account",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Create an anonymous account to sync and backup your preferences across devices. No Google account required.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Text(
                    text = "Your settings will be locked and backed up to prevent accidental changes.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            if (!isLoading) {
                Button(
                    onClick = {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                // Sign in anonymously
                                authManager.signInAnonymously().onSuccess { user ->
                                    isLoading = false
                                    onAuthSuccess()
                                }.onFailure { exception ->
                                    isLoading = false
                                    onAuthError(exception as? Exception ?: Exception(exception))
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                onAuthError(e)
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Account & Sync")
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}