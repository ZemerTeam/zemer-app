package com.jtech.zemer.statuses

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure, UI-free timeline math behind the story viewer: which status a creator resumes on, and how a
 * creator's posts group into per-date windows for the "jump to date" sheet. Extracted from StoryScreen
 * so it is testable without an Android runtime; every function is deterministic given its inputs (the
 * calendar zone is injectable, defaulting to the device zone the viewer actually uses).
 */

/** One date the creator posted on: its ISO date, the index of its first post, and its post count. */
data class StatusDateGroup(val iso: String, val startIndex: Int, val count: Int)

/** Where a creator opens: the entry date's first index ([floor]) and the status to resume on ([index]). */
data class ResumePos(val floor: Int, val index: Int)

private val postedAtFmt = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

/**
 * Format a post's UTC/offset timestamp for display in [zone] (the DEVICE's zone by default), or "" if
 * unparseable. Converting to the device zone first avoids showing times off by the user's UTC offset.
 */
fun formatPostedAt(postedAt: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
    postedAtFmt.format(ZonedDateTime.parse(postedAt).withZoneSameInstant(zone))
} catch (_: Exception) { "" }

/** The post's calendar date ("YYYY-MM-DD") in [zone] (device zone by default), or null if unparseable. */
fun statusLocalDate(postedAt: String, zone: ZoneId = ZoneId.systemDefault()): String? = try {
    ZonedDateTime.parse(postedAt).withZoneSameInstant(zone).toLocalDate().toString()
} catch (_: Exception) { null }

/**
 * The WhatsApp resume position: default to TODAY's date window (or the newest date if none today) and
 * land on its first UNSEEN status, else its newest. Shared by the driver and the cube preview face so
 * the status shown mid-swipe is exactly the one that plays once the pager settles (no jump). [floor] is
 * the entry date's first index, so tapping back never descends below where the creator was opened.
 */
fun resumePos(
    posts: List<StatusPost>,
    seen: Set<String>,
    todayIso: String,
    zone: ZoneId = ZoneId.systemDefault(),
): ResumePos {
    if (posts.isEmpty()) return ResumePos(0, 0)
    val defaultIso = if (posts.any { statusLocalDate(it.postedAt, zone) == todayIso }) todayIso
    else statusLocalDate(posts.last().postedAt, zone)
    val wStart = posts.indexOfFirst { statusLocalDate(it.postedAt, zone) == defaultIso }.coerceAtLeast(0)
    val wEnd = posts.indexOfLast { statusLocalDate(it.postedAt, zone) == defaultIso }.coerceAtLeast(wStart)
    val index = (wStart..wEnd).firstOrNull { posts[it].id !in seen } ?: wEnd
    return ResumePos(wStart, index)
}

/**
 * Group a creator's posts (sorted oldest-first, so a date's posts are contiguous) by local date, in
 * chronological order - the data behind the "jump to date" sheet.
 */
fun statusDateGroups(
    posts: List<StatusPost>?,
    zone: ZoneId = ZoneId.systemDefault(),
): List<StatusDateGroup> {
    if (posts.isNullOrEmpty()) return emptyList()
    val groups = mutableListOf<StatusDateGroup>()
    var i = 0
    while (i < posts.size) {
        val d = statusLocalDate(posts[i].postedAt, zone) ?: "?"
        var j = i + 1
        while (j < posts.size && (statusLocalDate(posts[j].postedAt, zone) ?: "?") == d) j++
        groups.add(StatusDateGroup(iso = d, startIndex = i, count = j - i))
        i = j
    }
    return groups
}
