package com.jtech.zemer.latestreleases

import android.text.format.DateUtils
import java.time.OffsetDateTime

/**
 * Formats a release's ISO-8601 `uploadDate` as a localized relative span ("2 days ago", "Yesterday",
 * "Today") via [DateUtils], so no new string resources are needed. Returns null on an unparseable
 * date, letting the card simply omit the line.
 */
fun LatestRelease.relativeDateLabel(now: Long = System.currentTimeMillis()): String? = try {
    val millis = OffsetDateTime.parse(uploadDate).toInstant().toEpochMilli()
    DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.DAY_IN_MILLIS).toString()
} catch (e: Exception) {
    null
}
