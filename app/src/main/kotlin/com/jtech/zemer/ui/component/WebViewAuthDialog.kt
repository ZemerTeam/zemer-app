package com.jtech.zemer.ui.component

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jtech.zemer.R
import com.jtech.zemer.auth.WebViewGoogleAuthManager
import kotlinx.coroutines.launch

/**
 * A dialog that shows a WebView for Google OAuth authentication.
 *
 * @param authManager WebViewGoogleAuthManager instance
 * @param onDismiss Callback when dialog is dismissed
 * @param onAuthSuccess Callback with ID token when authentication succeeds
 * @param onAuthError Callback with exception when authentication fails
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewAuthDialog(
    authManager: WebViewGoogleAuthManager,
    onDismiss: () -> Unit,
    onAuthSuccess: (idToken: String) -> Unit,
    onAuthError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
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
                        text = "Signing in...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    Text(
                        text = "Sync Your Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Create an anonymous account to sync and backup your preferences across devices. You can link this to a Google account later.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    Button(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    authManager.signInWithGoogle().onSuccess { user ->
                                        isLoading = false
                                        onDismiss()
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
                        enabled = !isLoading
                    ) {
                        Text("Create Account & Sync")
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}