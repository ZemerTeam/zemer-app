package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.App
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.R
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.UseLoginForBrowse
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.ui.component.InfoLabel
import com.jtech.zemer.utils.Updater
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.AccountSettingsViewModel
import com.jtech.zemer.viewmodels.HomeViewModel
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.YouTube
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
)
{
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val hasVisitorToken = remember(visitorData) { visitorData.startsWith("Cg") }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var isTestingToken by remember { mutableStateOf(false) }
    var tokenTestResult by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 1. Login with Google (WebView)
        Button(
            onClick = {
                if (isLoggedIn) {
                    showLogoutDialog = true
                } else {
                    onClose()
                    navController.navigate("login")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    painter = painterResource(R.drawable.google_webview),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isLoggedIn) stringResource(R.string.action_logout)
                    else stringResource(R.string.login_with_google_webview)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 3. Login as Anonymous
        Button(
            onClick = {
                if (isLoggedIn) {
                    showLogoutDialog = true
                } else {
                    isTestingToken = true
                    tokenTestResult = null
                    scope.launch {
                        try {
                            val httpClient = HttpClient()
                            val responseText = httpClient.get(
                                "https://ytzemer-token.usheraweiss.workers.dev/api/token"
                            ).bodyAsText()

                            val json = kotlinx.serialization.json.Json.parseToJsonElement(responseText)
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
                                onVisitorDataChange(fetchedVisitorData)
                                YouTube.visitorData = fetchedVisitorData
                                fetchedCookie
                                    ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                                    ?.let {
                                        onInnerTubeCookieChange(it)
                                        runCatching { YouTube.cookie = it }
                                    }
                                fetchedDataSyncId?.let {
                                    val clean = it.substringBefore("||")
                                    onDataSyncIdChange(clean)
                                    YouTube.dataSyncId = clean
                                }
                                fetchedAccountName?.let { onAccountNameChange(it) }
                                fetchedAccountEmail?.let { onAccountEmailChange(it) }
                                fetchedAccountChannelHandle?.let { onAccountChannelHandleChange(it) }
                                tokenTestResult = "success"
                                android.util.Log.i("TokenTest", "✓ Anonymous token valid!")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.login_success_restart),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                tokenTestResult = "invalid"
                                android.util.Log.w("TokenTest", "✗ Invalid token format")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.login_failed_invalid_token),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            httpClient.close()
                        } catch (e: Exception) {
                            tokenTestResult = "error"
                            android.util.Log.w("TokenTest", "✗ Login failed: ${e.message}")
                            val reason = e.message ?: context.getString(R.string.error_unknown)
                            Toast.makeText(
                                context,
                                context.getString(R.string.login_failed_with_reason, reason),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isTestingToken = false
                        }
                    }
                }
            },
            enabled = !isTestingToken || isLoggedIn,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (!isLoggedIn && isTestingToken) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.incognito),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        isLoggedIn -> stringResource(R.string.action_logout)
                        isTestingToken -> stringResource(R.string.login_progress)
                        else -> stringResource(R.string.login_as_anonymous)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showTokenEditor) {
            val text = """
                ***INNERTUBE COOKIE*** =$innerTubeCookie
                ***VISITOR DATA*** =$visitorData
                ***DATASYNC ID*** =$dataSyncId
                ***ACCOUNT NAME*** =$accountNamePref
                ***ACCOUNT EMAIL*** =$accountEmail
                ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
            """.trimIndent()

            TextFieldDialog(
                initialTextFieldValue = TextFieldValue(text),
                onDone = { data ->
                    data.split("\n").forEach {
                        when {
                            it.startsWith("***INNERTUBE COOKIE*** =") -> onInnerTubeCookieChange(it.substringAfter("="))
                            it.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(it.substringAfter("="))
                            it.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(it.substringAfter("="))
                            it.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(it.substringAfter("="))
                            it.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(it.substringAfter("="))
                            it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(it.substringAfter("="))
                        }
                    }
                },
                onDismiss = { showTokenEditor = false },
                singleLine = false,
                maxLines = 20,
                isInputValid = {
                    it.isNotEmpty() && "SAPISID" in parseCookieString(it)
                },
                extraContent = {
                    InfoLabel(text = stringResource(R.string.token_adv_login_description))
                }
            )
        }

        PreferenceEntry(
            title = {
                Text(
                    when {
                        !isLoggedIn -> stringResource(R.string.advanced_login)
                        showToken -> stringResource(R.string.token_shown)
                        else -> stringResource(R.string.token_hidden)
                    }
                )
            },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onClick = {
                if (!isLoggedIn) showTokenEditor = true
                else if (!showToken) showToken = true
                else showTokenEditor = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        )

        Spacer(Modifier.height(4.dp))

        if (isLoggedIn) {
            SwitchPreference(
                title = { Text(stringResource(R.string.more_content)) },
                description = null,
                icon = { Icon(painterResource(R.drawable.add_circle), null) },
                checked = useLoginForBrowse,
                onCheckedChange = {
                    YouTube.useLoginForBrowse = it
                    onUseLoginForBrowseChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )

            Spacer(Modifier.height(4.dp))

            SwitchPreference(
                title = { Text(stringResource(R.string.yt_sync)) },
                icon = { Icon(painterResource(R.drawable.cached), null) },
                checked = ytmSync,
                onCheckedChange = onYtmSyncChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Logout confirmation dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Keep library data?") },
                text = { Text("Do you want to keep your downloaded songs, playlists, and library data?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                App.forgetAccount(context)
                                Toast.makeText(context, context.getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text("Keep")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                accountSettingsViewModel.clearAllLibraryData()
                                App.forgetAccount(context)
                                Toast.makeText(context, context.getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                }
            )
        }

        if (latestVersionName != BuildConfig.VERSION_NAME) {
            PreferenceEntry(
                title = { Text(text = stringResource(R.string.new_version_available)) },
                description = latestVersionName,
                icon = {
                    BadgedBox(badge = { Badge() }) {
                        Icon(painterResource(R.drawable.update), null)
                    }
                },
                onClick = {
                    Updater.getCachedDownloadUrl()?.let { uriHandler.openUri(it) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}
