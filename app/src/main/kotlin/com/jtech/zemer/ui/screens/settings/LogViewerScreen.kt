package com.jtech.zemer.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.DebugLoggingEnabledKey
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.LogBufferTree
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
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
    val entries = remember { LogBufferTree.entries }
    var refreshTick by remember { mutableStateOf(0) }
    var filterText by remember { mutableStateOf("") }
    var showExportRangePicker by remember { mutableStateOf(false) }
    var exportFromMillis by remember { mutableLongStateOf(0L) }
    var exportToMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickingField by remember { mutableStateOf<ExportField?>(null) }

    LaunchedEffect(debugLogging) {
        while (isActive) {
            delay(1000)
            refreshTick++
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceGroupTitle(title = stringResource(R.string.log_viewer))

            SwitchPreference(
                title = { Text(stringResource(R.string.enable_debug_logging)) },
                description = stringResource(R.string.enable_debug_logging_desc),
                icon = { Icon(painterResource(R.drawable.info), null) },
                checked = debugLogging,
                onCheckedChange = onDebugLoggingChange,
            )

            PreferenceEntry(
                title = { Text(stringResource(R.string.clear_logs)) },
                onClick = {
                    LogBufferTree.clear()
                },
            )

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

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text(stringResource(R.string.log_filter_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider()

            val visibleEntries = entries
                .asSequence()
                .filter { entry ->
                    if (filterText.isBlank()) return@filter true
                    val tag = entry.tag ?: "Zemer"
                    val message = entry.message
                    tag.contains(filterText, ignoreCase = true) ||
                        message.contains(filterText, ignoreCase = true)
                }
                .toList()

            if (visibleEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                visibleEntries.takeLast(200).forEach { entry ->
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
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
                        exportLogs(context, from, to)
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_logs)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        confirmButton = {
            TextButton(onClick = { onExport(fromMillis, toMillis) }) {
                Text(stringResource(R.string.export_logs))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDateTimePicker(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDate = remember(initialMillis) { Date(initialMillis) }
    val calendar = remember(initialMillis) {
        java.util.Calendar.getInstance().apply { time = initialDate }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis.coerceAtLeast(0),
    )
    var pickingTime by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableLongStateOf(initialMillis) }

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
            initialHour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(java.util.Calendar.MINUTE),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = pickedDateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onConfirm(cal.timeInMillis)
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(stringResource(R.string.log_export_from), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(top = 8.dp))
                TimePicker(state = timePickerState)
            }
        }
    }
}

private fun exportLogs(
    context: android.content.Context,
    fromMillis: Long,
    toMillis: Long,
): String? {
    return try {
        val logs = LogBufferTree.entries.filter { it.timestamp in fromMillis..toMillis }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "zemer_logs_${dateFormat.format(Date(fromMillis))}_to_${dateFormat.format(Date(toMillis))}.txt"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, fileName)
        exportFile.writeText(buildLogText(logs))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            exportFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Zemer logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Export logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
        fileName
    } catch (e: Exception) {
        com.jtech.zemer.utils.reportException(e)
        null
    }
}

private fun buildLogText(entries: List<LogBufferTree.LogEntry>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    val sb = StringBuilder()
    sb.appendLine("# Zemer log export")
    sb.appendLine("# Range: ${dateFormat.format(Date(entries.firstOrNull()?.timestamp ?: 0))} - ${dateFormat.format(Date(entries.lastOrNull()?.timestamp ?: 0))}")
    sb.appendLine("# Entries: ${entries.size}")
    sb.appendLine()
    for (entry in entries) {
        val time = dateFormat.format(Date(entry.timestamp))
        val priority = LogBufferTree.priorityName(entry.priority)
        val tag = entry.tag ?: "Zemer"
        sb.append("[$time] $priority/$tag: ${entry.message}")
        entry.throwable?.let { t ->
            sb.append("\n")
            t.toString().lines().forEach { sb.append("    $it\n") }
        }
        sb.appendLine()
    }
    return sb.toString()
}
