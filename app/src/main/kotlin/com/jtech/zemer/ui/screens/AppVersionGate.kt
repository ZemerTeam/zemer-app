package com.jtech.zemer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.R
import com.jtech.zemer.appgate.AppConfigStore
import com.jtech.zemer.appgate.AppGate
import com.jtech.zemer.appgate.appGate
import com.jtech.zemer.appgate.gracePromptDue
import com.jtech.zemer.constants.AppUpdateGraceShownDayKey
import com.jtech.zemer.constants.NightlyUpdatesKey
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.UpdateDownloadFlow
import com.jtech.zemer.utils.UpdateChecker
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The current server-driven gate for this build (see [com.jtech.zemer.appgate.AppConfig]). */
@Composable
fun rememberAppGate(): AppGate {
    val config by AppConfigStore.config.collectAsState()
    return remember(config) { appGate(config, BuildConfig.VERSION_CODE, Instant.now()) }
}

/**
 * Full-screen, non-dismissible "Update required" (below the server's `minVersion`). Back leaves the app;
 * Update runs the normal update download/install flow, falling back to the server's `updateUrl`.
 */
@Composable
fun UpdateRequiredScreen(gate: AppGate.Blocked) {
    val activity = LocalContext.current as? Activity
    BackHandler { activity?.finish() }
    val update = rememberGateUpdate(gate.updateUrl)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.update),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_update_required_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = gate.message ?: stringResource(R.string.app_update_required_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = update::start, enabled = !update.checking) {
            Text(stringResource(R.string.update_action))
        }
    }
    GateUpdateDialog(update)
}

/** The dismissible "Please update by <date>" prompt (grace period), at most once per local day. */
@Composable
fun GraceUpdatePrompt(gate: AppGate.Grace) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(gate) {
        val today = LocalDate.now().toString()
        if (gracePromptDue(context.dataStore.getSuspend(AppUpdateGraceShownDayKey, ""), today)) {
            show = true
            context.dataStore.edit { it[AppUpdateGraceShownDayKey] = today }
        }
    }
    val update = rememberGateUpdate(gate.updateUrl)
    if (show) {
        val date = remember(gate.deadline) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(gate.deadline.atZone(ZoneId.systemDefault()))
        }
        DefaultDialog(
            onDismiss = { show = false },
            title = { Text(stringResource(R.string.app_update_grace_title, date)) },
            content = {
                Text(
                    text = gate.message ?: stringResource(R.string.app_update_grace_message, date),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.later)) }
                TextButton(onClick = { show = false; update.start() }) { Text(stringResource(R.string.update_action)) }
            },
        )
    }
    GateUpdateDialog(update)
}

/** Update action shared by both gate surfaces: check the user's channel, then the normal download flow. */
@Stable
private class GateUpdate(
    private val scope: CoroutineScope,
    private val context: Context,
    private val nightly: Boolean,
    private val updateUrl: String?,
) {
    var checking by mutableStateOf(false)
        private set
    var pending by mutableStateOf<UpdateChecker.UpdateResult.UpdateAvailable?>(null)

    fun start() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                when (val result = UpdateChecker.checkForUpdates(nightly)) {
                    is UpdateChecker.UpdateResult.UpdateAvailable -> pending = result
                    // No build from the updater (unreachable, filtered, "up to date"): the server's link,
                    // opened as a plain external link and fail-soft; else say so.
                    else -> if (!openUpdateUrl()) context.toast(R.string.app_update_check_failed)
                }
            } finally {
                checking = false
            }
        }
    }

    private fun openUpdateUrl(): Boolean = updateUrl != null && runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

@Composable
private fun rememberGateUpdate(updateUrl: String?): GateUpdate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (nightly) = rememberPreference(NightlyUpdatesKey, defaultValue = false)
    return remember(updateUrl, nightly) { GateUpdate(scope, context, nightly, updateUrl) }
}

@Composable
private fun GateUpdateDialog(update: GateUpdate) {
    val pending = update.pending ?: return
    UpdateDownloadFlow(
        latestVersion = pending.latestVersion,
        isNightly = pending.isNightly,
        notes = pending.notes,
        onDismiss = { update.pending = null },
    )
}
