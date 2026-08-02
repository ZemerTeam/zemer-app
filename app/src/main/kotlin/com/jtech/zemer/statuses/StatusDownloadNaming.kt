package com.jtech.zemer.statuses

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure gallery-path pieces for saved statuses, kept UI-free so they are JVM-testable. Files live at
 * `Pictures|Movies / Zemer / Status / <creator> / <posted date time>.<ext>`, so the creator is a folder
 * level and the filename is just the status's POSTED date and time (colons are illegal in file names, so
 * the time uses hyphens), e.g. `.../Zemer/Status/Shira Choir/2026-08-01 21-14-32.mp4`. MediaStore
 * auto-suffixes the rare same-creator/same-second collision.
 */
private val FILE_STAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss", Locale.US)

/** Characters illegal (or troublesome) in a MediaStore display name / folder segment. */
private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\n\r\t]""")

/** The file's base name (no extension): the status's posted date and time in [zone]. */
fun statusDownloadStamp(postedAt: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        FILE_STAMP_FMT.format(ZonedDateTime.parse(postedAt).withZoneSameInstant(zone))
    }.getOrDefault("unknown-date")

/** Filesystem-safe creator name for the folder segment: illegal chars removed, whitespace collapsed. */
fun sanitizeCreatorForFile(name: String): String =
    name.replace(ILLEGAL_FILENAME_CHARS, "").replace(Regex("""\s+"""), " ").trim()
        .ifBlank { "Status" }
