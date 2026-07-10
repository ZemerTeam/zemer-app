package com.jtech.zemer.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jtech.zemer.R
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/**
 * The Log viewer's export flow: pure range/formatting helpers (unit-tested) plus the one
 * IO entry point [writeAndShare] the screen calls. Material3's DatePicker represents a
 * selected day as UTC-midnight millis, while log timestamps are local-zone instants —
 * the two time functions are the only place that translation happens, in both directions.
 */
object LogExport {

    /**
     * UTC-midnight millis of the local calendar day containing [localInstantMillis] — the
     * representation to seed a DatePicker with. Feeding the raw local instant instead
     * pre-selects the previous day whenever local time is ahead of UTC and before the
     * zone-offset hour (e.g. 00:30 in Israel).
     */
    fun utcDayMillis(localInstantMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(localInstantMillis).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Local-zone instant for the picker's selected UTC-day [utcDayMillis] at [hour]:[minute]
     * local wall-clock time. Decomposing the UTC-day with a default-zone calendar instead
     * lands on the previous day in every zone west of UTC.
     */
    fun localInstantMillis(utcDayMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(utcDayMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Entries whose timestamp falls inside the inclusive [fromMillis]..[toMillis] range. */
    fun filterRange(
        entries: List<LogBufferTree.LogEntry>,
        fromMillis: Long,
        toMillis: Long,
    ): List<LogBufferTree.LogEntry> = entries.filter { it.timestamp in fromMillis..toMillis }

    fun exportFileName(fromMillis: Long, toMillis: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        return "zemer_logs_${dateFormat.format(Date(fromMillis))}_to_${dateFormat.format(Date(toMillis))}.txt"
    }

    /**
     * The exported file's content. Timber embeds a logged Throwable's full stack trace in
     * the message before trees see it, so the entry message already carries the frames —
     * no separate throwable rendering is needed (or wanted: it duplicated the first line).
     */
    fun buildLogText(entries: List<LogBufferTree.LogEntry>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("# Zemer log export")
        sb.appendLine(
            "# Range: ${dateFormat.format(Date(entries.firstOrNull()?.timestamp ?: 0))} - " +
                dateFormat.format(Date(entries.lastOrNull()?.timestamp ?: 0))
        )
        sb.appendLine("# Entries: ${entries.size}")
        sb.appendLine()
        for (entry in entries) {
            val time = dateFormat.format(Date(entry.timestamp))
            val priority = LogBufferTree.priorityName(entry.priority)
            val tag = entry.tag ?: "Zemer"
            sb.appendLine("[$time] $priority/$tag: ${entry.message}")
        }
        return sb.toString()
    }

    /**
     * Writes the range's entries to a fresh file under cacheDir/exports (sweeping previous
     * exports so they don't accumulate) and opens the system share sheet for it.
     * Blocking IO — call from a background dispatcher. Returns the file name, or null on
     * failure (reported, not swallowed).
     */
    fun writeAndShare(context: Context, fromMillis: Long, toMillis: Long): String? {
        return try {
            val logs = filterRange(LogBufferTree.entries, fromMillis, toMillis)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            exportDir.listFiles()?.forEach { it.delete() }
            val exportFile = File(exportDir, exportFileName(fromMillis, toMillis))
            exportFile.writeText(buildLogText(logs))

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                exportFile,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_export_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.export_logs)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            exportFile.name
        } catch (e: Exception) {
            reportException(e)
            null
        }
    }
}
