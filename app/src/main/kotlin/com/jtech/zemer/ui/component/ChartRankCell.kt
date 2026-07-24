package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.search.ChartMovement
import com.jtech.zemer.ui.theme.chartClimbColor
import com.jtech.zemer.ui.theme.chartFallColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The position column of an `auto-*` chart row: the rank, with a small triangle above it when the
 * song climbed and below it when it fell, `NEW`/`RE` under it for a debut or return, and nothing
 * drawn at all when it held its position.
 *
 * Direction is carried by position and colour, never magnitude — the convention every major chart
 * uses, because on a 50-row list a number on every row is noise and the eye only wants to find what
 * moved. The exact delta is still in the accessible label.
 *
 * Two things this must get right:
 *
 * - **[movement] == null draws no marker** — not a dash, not a zero, and never a diff against a
 *   device-local snapshot. Absent movement is normal: a curated non-chart playlist, a too-young rank
 *   history, or a per-chart formula change that reset that chart's baseline (which also blanks
 *   briefly, until the next successful run, when a generator tick is skipped). The rank still shows.
 * - **The glyph never carries the meaning alone.** ▲ and ▼ differ mainly by colour in most palettes,
 *   so the whole cell exposes ONE spoken label and hides its parts from the accessibility tree.
 *
 * Both marker slots are reserved whether or not they hold anything, so ranks line up down the list
 * instead of shifting by whether a row happens to have moved.
 */
@Composable
fun ChartRankCell(
    rank: Int,
    movement: ChartMovement?,
    modifier: Modifier = Modifier,
) {
    val description = when (movement) {
        null -> stringResource(R.string.chart_rank_only, rank)
        ChartMovement.New -> stringResource(R.string.chart_movement_new, rank)
        ChartMovement.Reentry -> stringResource(R.string.chart_movement_reentry, rank)
        ChartMovement.Unchanged -> stringResource(R.string.chart_movement_unchanged, rank)
        is ChartMovement.Up -> stringResource(R.string.chart_movement_up, movement.places, rank)
        is ChartMovement.Down -> stringResource(R.string.chart_movement_down, movement.places, rank)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(ChartRankColumnWidth)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        MarkerSlot {
            if (movement is ChartMovement.Up) Marker("▲", chartClimbColor())
        }
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                // Tabular figures: proportional digits make a 50-row column look ragged.
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
            ),
        )
        MarkerSlot {
            when (movement) {
                is ChartMovement.Down -> Marker("▼", chartFallColor())
                ChartMovement.New -> MarkerLabel(stringResource(R.string.chart_new))
                ChartMovement.Reentry -> MarkerLabel(stringResource(R.string.chart_reentry))
                else -> Unit
            }
        }
    }
}

/** Reserved whether or not it holds anything, so the rank sits at the same height on every row. */
@Composable
private fun MarkerSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.height(MARKER_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Sized through the type scale (UI standards rule 8): labelSmall is the smallest role we have. */
@Composable
private fun Marker(glyph: String, color: Color) {
    Text(text = glyph, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun MarkerLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Exposed so a rank-less row on a ranked chart can hold the column open. */
val ChartRankColumnWidth = 30.dp
private val MARKER_SLOT_HEIGHT = 14.dp

/**
 * The "movement since" date, formatted for the reader's locale — or null to hide the label, which is
 * also the correct rendering when there are no arrows to explain. An unparseable date hides the label
 * rather than showing a raw ISO string: the server owns this format, and a future change to it must
 * degrade quietly instead of leaking machine text into the header.
 */
@Composable
fun chartAnchorLabel(anchorDate: String?): String? {
    // The Compose configuration locale, not Locale.getDefault(): with a per-app language set
    // (Android 13+), the JVM default can still be the SYSTEM locale, which rendered an English date
    // inside an otherwise-Hebrew label. Reading it here also recomposes on a locale change instead
    // of leaving the previously formatted date on screen.
    val locale = LocalConfiguration.current.locales[0]
    return anchorDate?.takeIf { it.isNotBlank() }?.let { iso ->
        runCatching {
            LocalDate.parse(iso)
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }.getOrNull()
    }
}
