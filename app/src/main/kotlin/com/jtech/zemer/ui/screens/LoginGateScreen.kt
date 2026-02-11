package com.jtech.zemer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.utils.rememberPreference
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
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Sign in to continue",
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
                                    "https://ytzemer-token.usheraweiss.workers.dev/api/token"
                                ).bodyAsText()

                                val json = Json.parseToJsonElement(responseText)
                                val fetchedVisitorData = json.jsonObject["visitorData"]?.jsonPrimitive?.content
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
                                    fetchedDataSyncId?.let {
                                        val clean = it.substringBefore("||")
                                        dataSyncId = clean
                                        YouTube.dataSyncId = clean
                                    }
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
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.login_failed_invalid_token),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                httpClient.close()
                            } catch (e: Exception) {
                                val reason = e.message ?: context.getString(R.string.error_unknown)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.login_failed_with_reason, reason),
                                    Toast.LENGTH_SHORT
                                ).show()
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
            }
        }
    }
}
