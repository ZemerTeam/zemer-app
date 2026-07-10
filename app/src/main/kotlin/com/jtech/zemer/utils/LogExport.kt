package com.jtech.zemer.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure helpers for the Log viewer's export flow. Material3's DatePicker represents a
 * selected day as UTC-midnight millis, while log timestamps are local-zone instants —
 * these two functions are the only place that translation happens, in both directions.
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
}
