package com.jtech.zemer.ui.screens

import com.jtech.zemer.ui.component.AppNameTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.PlaybackMode
import com.jtech.zemer.constants.PlaybackModeKey
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.rememberPreference
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.extensions.toast
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Compact login gate screen matching onboarding style.
 * Navigates to actual LoginScreen for Google login.
 */
@Composable
fun LoginGateScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isAnonymousLoading by remember { mutableStateOf(false) }

    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")

    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon and title
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    // The app LOGO, not a generic song glyph: small_icon is the launcher artwork's
                    // eighth-note silhouette, so every logo surface matches the app icon (#179).
                    painter = painterResource(R.drawable.small_icon),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                AppNameTitle(textAlign = TextAlign.Center)
                Text(
                    text = stringResource(R.string.sign_in_to_continue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Login buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Google Sign-In Button - navigates to existing LoginScreen
                Button(
                    onClick = {
                        navController.navigate("login")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.google_webview),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_with_google_webview),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Anonymous Sign-In Button
                OutlinedButton(
                    onClick = {
                        isAnonymousLoading = true
                        coroutineScope.launch {
                            try {
                                val httpClient = HttpClient()
                                val responseText = httpClient.get(
                                    "https://mc.alltech.dev/credentials"
                                ).bodyAsText()

                                val json = Json.parseToJsonElement(responseText)
                                val fetchedVisitorData = json.jsonObject["visitorData"]?.jsonPrimitive?.content
                                    ?.let { android.net.Uri.decode(it) }
                                val fetchedCookie = run {
                                    val raw = json.jsonObject["cookie"]?.jsonPrimitive?.content
                                        ?: json.jsonObject["innerTubeCookie"]?.jsonPrimitive?.content
                                    val trimmed = raw?.trim()
                                    if (trimmed != null &&
                                        ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                                            (trimmed.startsWith("'") && trimmed.endsWith("'")))
                                    ) {
                                        trimmed.drop(1).dropLast(1)
                                    } else {
                                        trimmed
                                    }
                                }
                                val fetchedDataSyncId = json.jsonObject["dataSyncId"]?.jsonPrimitive?.content
                                val fetchedAccountName = json.jsonObject["accountName"]?.jsonPrimitive?.content
                                val fetchedAccountEmail = json.jsonObject["accountEmail"]?.jsonPrimitive?.content
                                val fetchedAccountChannelHandle = json.jsonObject["accountChannelHandle"]?.jsonPrimitive?.content

                                if (!fetchedVisitorData.isNullOrEmpty() && fetchedVisitorData.startsWith("Cg") && fetchedVisitorData.length > 20) {
                                    visitorData = fetchedVisitorData
                                    YouTube.visitorData = fetchedVisitorData
                                    fetchedCookie
                                        ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                                        ?.let {
                                            innerTubeCookie = it
                                            runCatching { YouTube.cookie = it }
                                        }
                                    // Anonymous login must NOT set dataSyncId — the pooled
                                    // account's onBehalfOfUser breaks the player request (HTTP 400).
                                    dataSyncId = ""
                                    YouTube.dataSyncId = null
                                    fetchedAccountName?.let { accountName = it }
                                    fetchedAccountEmail?.let { accountEmail = it }
                                    fetchedAccountChannelHandle?.let { accountChannelHandle = it }

                                    // Small delay to let preferences propagate before navigating
                                    kotlinx.coroutines.delay(100)

                                    // Navigate directly to home
                                    navController.navigate("home") {
                                        popUpTo("login_gate") { inclusive = true }
                                    }
                                } else {
                                    context.toast(context.getString(R.string.login_failed_invalid_token))
                                }
                                httpClient.close()
                            } catch (e: Exception) {
                                val reason = e.message ?: context.getString(R.string.error_unknown)
                                context.toast(context.getString(R.string.login_failed_with_reason, reason))
                            } finally {
                                isAnonymousLoading = false
                            }
                        }
                    },
                    enabled = !isAnonymousLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isAnonymousLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.incognito),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnonymousLoading) stringResource(R.string.login_progress) else stringResource(R.string.login_as_anonymous),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // "My filter blocks playback": a login-less RELAY-mode entry for kosher-filtered devices
                // that block music.youtube.com / googlevideo.com. No Google, no pooled cookie — just set the
                // relay flag and enter; playback then streams over the whitelisted relay host instead of the
                // device resolving YouTube directly. Everything else already flows through *.zemer.io.
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            context.dataStore.edit { it[PlaybackModeKey] = PlaybackMode.RELAY.name }
                            navController.navigate("home") {
                                popUpTo("login_gate") { inclusive = true }
                            }
                        }
                    },
                    enabled = !isAnonymousLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.security),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.login_filter_blocks_playback),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
