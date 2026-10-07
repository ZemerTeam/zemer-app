package com.jtech.zemer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.jtech.zemer.R
import kotlin.math.roundToInt

/**
 * A duration as a short human label, sized to fit a grid card's two subtitle lines (curated playlists,
 * the Stats screen): null (unknown) hides the label; under two hours it reads in minutes (a known sub-minute
 * runtime rounds up to 1 rather than "0 minutes"); from two hours up it reads in whole rounded hours —
 * "40 hours", not the "2426 minutes" that truncated the card text.
 */
data class RuntimeLabel(val count: Int, val isHours: Boolean)

fun runtimeOf(totalDurationSec: Int?): RuntimeLabel? {
    val minutes = totalDurationSec?.let { (it / 60).coerceAtLeast(1) } ?: return null
    return if (minutes < 120) {
        RuntimeLabel(minutes, isHours = false)
    } else {
        RuntimeLabel((totalDurationSec / 3600f).roundToInt(), isHours = true)
    }
}

/** The localized runtime label ("164 minutes" / "40 hours"), or null to hide it. */
@Composable
fun runtimeLabel(totalDurationSec: Int?): String? =
    runtimeOf(totalDurationSec)?.let { runtime ->
        if (runtime.isHours) {
            pluralStringResource(R.plurals.n_hour, runtime.count, runtime.count)
        } else {
            pluralStringResource(R.plurals.minute, runtime.count, runtime.count)
        }
    }
