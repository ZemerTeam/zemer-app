package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.App
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.constants.AccountChannelHandleKey
import com.jtech.zemer.constants.AccountEmailKey
import com.jtech.zemer.constants.AccountNameKey
import com.jtech.zemer.constants.DataSyncIdKey
import com.jtech.zemer.constants.InnerTubeCookieKey
import com.jtech.zemer.constants.UseLoginForBrowse
import com.jtech.zemer.constants.VisitorDataKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.SettingsCardGroup
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.ui.component.InfoLabel
import com.jtech.zemer.utils.Updater
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.AccountSettingsViewModel
import com.jtech.zemer.viewmodels.HomeViewModel
import com.jtech.zemer.extensions.toast
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.YouTube
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

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

        // Personal Google account identity — shown ONLY for a personal login (non-empty dataSyncId).
        // Anonymous logins sign into a shared, pooled account, so surfacing its name/email here would
        // be wrong and leak the pooled identity across anon users; gate strictly on dataSyncId, never
        // on the SAPISID-based isLoggedIn (which is true for anonymous too).
        if (dataSyncId.isNotBlank()) {
            SignedInAccountCard(
                name = accountNamePref.ifBlank { accountName.takeIf { it != "Guest" }.orEmpty() },
                email = accountEmail,
                handle = accountChannelHandle,
                imageUrl = accountImageUrl,
            )
            Spacer(Modifier.height(12.dp))
        }

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

        // Offer Anonymous login only while signed out — once logged in, the Google button above is
        // the single Logout control, so rendering this button too produced a duplicate Logout button.
        if (!isLoggedIn) {
        Spacer(Modifier.height(8.dp))

        // Login as Anonymous
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
                                "https://mc.alltech.dev/credentials"
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

                            val decodedVisitorData = fetchedVisitorData?.let { android.net.Uri.decode(it) }
                            if (!decodedVisitorData.isNullOrEmpty() && decodedVisitorData.startsWith("Cg") && decodedVisitorData.length > 20) {
                                onVisitorDataChange(decodedVisitorData)
                                YouTube.visitorData = decodedVisitorData
                                fetchedCookie
                                    ?.takeIf { parseCookieString(it).containsKey("SAPISID") }
                                    ?.let {
                                        onInnerTubeCookieChange(it)
                                        runCatching { YouTube.cookie = it }
                                    }
                                // Anonymous login must NOT set dataSyncId (breaks playback).
                                onDataSyncIdChange("")
                                YouTube.dataSyncId = null
                                fetchedAccountName?.let { onAccountNameChange(it) }
                                fetchedAccountEmail?.let { onAccountEmailChange(it) }
                                fetchedAccountChannelHandle?.let { onAccountChannelHandleChange(it) }
                                tokenTestResult = "success"
                                android.util.Log.i("TokenTest", "✓ Anonymous token valid!")
                                context.toast(context.getString(R.string.login_success_restart), long = true)
                            } else {
                                tokenTestResult = "invalid"
                                android.util.Log.w("TokenTest", "✗ Invalid token format")
                                context.toast(context.getString(R.string.login_failed_invalid_token))
                            }
                            httpClient.close()
                        } catch (e: Exception) {
                            tokenTestResult = "error"
                            android.util.Log.w("TokenTest", "✗ Login failed: ${e.message}")
                            val reason = e.message ?: context.getString(R.string.error_unknown)
                            context.toast(context.getString(R.string.login_failed_with_reason, reason))
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

        // Account-personalization controls are personal-account only. "More content" routes browse
        // through the login (an anonymous/pooled account would personalize to the shared pool), and
        // "Auto sync" only runs for a personal account anyway — so gate both on dataSyncId, not the
        // SAPISID-based isLoggedIn (which is true for anonymous too).
        if (dataSyncId.isNotBlank()) {
            SettingsCardGroup(
                horizontalPadding = 0.dp, // the sheet's column already pads 16dp
                rows = listOf(
                    {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.more_content)) },
                            description = null,
                            icon = { Icon(painterResource(R.drawable.add_circle), null) },
                            checked = useLoginForBrowse,
                            onCheckedChange = {
                                YouTube.useLoginForBrowse = it
                                onUseLoginForBrowseChange(it)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.yt_sync)) },
                            icon = { Icon(painterResource(R.drawable.cached), null) },
                            checked = ytmSync,
                            onCheckedChange = onYtmSyncChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                ),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Logout confirmation dialog
        if (showLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                horizontalAlignment = Alignment.Start,
                title = { Text(stringResource(R.string.logout_keep_library_title)) },
                content = {
                    Text(stringResource(R.string.logout_keep_library_message))
                },
                buttons = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                accountSettingsViewModel.clearAllLibraryData()
                                App.forgetAccount(context)
                                context.toast(context.getString(R.string.logged_out))
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_clear_library))
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                App.forgetAccount(context)
                                context.toast(context.getString(R.string.logged_out))
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_keep_library))
                    }
                }
            )
        }

        if (latestVersionName != BuildConfig.VERSION_NAME) {
            SettingsCardGroup(
                horizontalPadding = 0.dp, // the sheet's column already pads 16dp
                rows = listOf(
                    {
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                ),
            )
        }
    }
}

/**
 * Shows the signed-in personal Google account (avatar + name + email + handle). Callers must gate
 * this on a personal login (non-empty dataSyncId) — anonymous sessions use a shared pooled account
 * whose identity must never be surfaced here. Display only; no actions, so no focus treatment needed.
 */
@Composable
private fun SignedInAccountCard(
    name: String,
    email: String,
    handle: String,
    imageUrl: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.account),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = stringResource(R.string.account),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.signed_in_as),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (email.isNotBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (handle.isNotBlank()) {
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
