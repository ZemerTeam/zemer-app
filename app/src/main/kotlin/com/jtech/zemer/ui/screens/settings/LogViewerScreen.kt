package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.DebugLoggingEnabledKey
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.LogBufferTree
import com.jtech.zemer.utils.LogExport
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    val (debugLogging, onDebugLoggingChange) = rememberPreference(DebugLoggingEnabledKey, true)
    val revision by LogBufferTree.revision.collectAsState()
    val entries = remember(revision) { LogBufferTree.entries }
    var filterText by remember { mutableStateOf("") }
    var showExportRangePicker by remember { mutableStateOf(false) }
    var exportFromMillis by remember { mutableLongStateOf(0L) }
    var exportToMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickingField by remember { mutableStateOf<ExportField?>(null) }

    val visibleEntries = remember(entries, filterText) {
        if (filterText.isBlank()) entries
        else entries.filter { entry ->
            (entry.tag ?: "Zemer").contains(filterText, ignoreCase = true) ||
                entry.message.contains(filterText, ignoreCase = true)
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
    ) {
        // LazyColumn (not a verticalScroll Column): only visible rows compose, and the
        // focusable log rows are what lets D-pad focus travel into — and scroll — the list.
        LazyColumn(Modifier.weight(1f)) {
            item {
                PreferenceGroupTitle(title = stringResource(R.string.log_viewer))
            }

            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enable_debug_logging)) },
                    description = stringResource(R.string.enable_debug_logging_desc),
                    icon = { Icon(painterResource(R.drawable.info), null) },
                    checked = debugLogging,
                    onCheckedChange = onDebugLoggingChange,
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.clear_logs)) },
                    onClick = {
                        LogBufferTree.clear()
                    },
                )
            }

            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.export_logs)) },
                    description = stringResource(R.string.log_export_range),
                    onClick = {
                        exportFromMillis = entries.firstOrNull()?.timestamp ?: System.currentTimeMillis()
                        exportToMillis = entries.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                        showExportRangePicker = true
                    },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }

            item {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text(stringResource(R.string.log_filter_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
                HorizontalDivider()
            }

            if (visibleEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(visibleEntries) { entry ->
                    val color = when (entry.priority) {
                        android.util.Log.ERROR -> MaterialTheme.colorScheme.error
                        android.util.Log.WARN -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "${LogBufferTree.priorityName(entry.priority)}/${entry.tag ?: "Zemer"}: ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusBorder()
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (showExportRangePicker) {
        ExportRangeDialog(
            fromMillis = exportFromMillis,
            toMillis = exportToMillis,
            onFromClick = { pickingField = ExportField.FROM },
            onToClick = { pickingField = ExportField.TO },
            onDismiss = { showExportRangePicker = false },
            onExport = { from, to ->
                showExportRangePicker = false
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        LogExport.writeAndShare(context, from, to)
                    }
                    snackbarHostState.showSnackbar(
                        if (result != null) {
                            context.getString(R.string.logs_exported, result)
                        } else {
                            context.getString(R.string.logs_export_failed)
                        }
                    )
                }
            },
        )
    }

    pickingField?.let { field ->
        val initial = if (field == ExportField.FROM) exportFromMillis else exportToMillis
        ExportDateTimePicker(
            titleRes = if (field == ExportField.FROM) R.string.log_export_from else R.string.log_export_to,
            initialMillis = initial,
            onConfirm = { pickedMillis ->
                if (field == ExportField.FROM) exportFromMillis = pickedMillis
                else exportToMillis = pickedMillis
                pickingField = null
            },
            onDismiss = { pickingField = null },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.log_viewer)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        }
    )
}

private enum class ExportField { FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportRangeDialog(
    fromMillis: Long,
    toMillis: Long,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onDismiss: () -> Unit,
    onExport: (from: Long, to: Long) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.export_logs)) },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_export_range), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onFromClick, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.log_export_from), style = MaterialTheme.typography.labelSmall)
                        Text(dateFormat.format(Date(fromMillis)))
                    }
                }
                OutlinedButton(onClick = onToClick, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.log_export_to), style = MaterialTheme.typography.labelSmall)
                        Text(dateFormat.format(Date(toMillis)))
                    }
                }
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(onClick = { onExport(fromMillis, toMillis) }) {
                Text(stringResource(R.string.export_logs))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDateTimePicker(
    titleRes: Int,
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialLocal = remember(initialMillis) {
        Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault())
    }
    // The DatePicker speaks UTC-day millis, log timestamps are local instants —
    // LogExport owns the translation in both directions (seed here, confirm below).
    val initialUtcDay = remember(initialMillis) {
        LogExport.utcDayMillis(initialMillis, ZoneId.systemDefault())
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcDay,
    )
    var pickingTime by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableLongStateOf(initialUtcDay) }

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis ?: initialMillis
                    pickedDateMillis = selected
                    pickingTime = true
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        val timePickerState = rememberTimePickerState(
            initialHour = initialLocal.hour,
            initialMinute = initialLocal.minute,
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(
                        LogExport.localInstantMillis(
                            pickedDateMillis,
                            timePickerState.hour,
                            timePickerState.minute,
                            ZoneId.systemDefault(),
                        )
                    )
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(top = 8.dp))
                TimePicker(state = timePickerState)
            }
        }
    }
}

