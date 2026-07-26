package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListArtLeadingSpace
import com.jtech.zemer.search.ChartMovement
import com.jtech.zemer.ui.theme.chartClimbColor
import com.jtech.zemer.ui.theme.chartFallColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The position column of an `auto-*` chart row: the rank, then to its right a small triangle with
 * how far the song moved, `NEW`/`RE` for a debut or return, and nothing at all when it held its
 * position.
 *
 * The marker is drawn only on rows that actually moved — an unchanged row stays empty, so the
 * noise floor a dash-per-row would create never appears while the magnitude is still there on the
 * rows where it means something.
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
 * The rank sits right-aligned in a fixed sub-width and the whole cell is a fixed width, so both the
 * numbers and the titles beside them line up down the list whether or not a row has a marker.
 */
@Composable
fun ChartRankCell(
    rank: Int,
    movement: ChartMovement?,
    metrics: ChartRankMetrics,
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

    Row(
        // Centred on the rank's midline: the marker belongs to that number, so it reads as one
        // unit with it rather than as something sitting under the row.
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // FIXED width, and fixed sub-widths inside it. A min-width cell grows for "NEW" or a
            // three-digit rank, and every dp it grows shoves that row's artwork right while its
            // neighbours stay put — the art column has to be a straight line down the screen.
            // Anything too wide for its slot is clipped by softWrap = false; it can never wrap and
            // it can never push.
            .width(metrics.total)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // Each glyph centred in its own fixed slot, so the numbers line up with each other and the
        // markers line up with each other, whatever the row happens to contain.
        // END, not centre. The slot is measured for the chart's WIDEST rank, so centring a
        // narrower one puts its digits half a glyph off the column — "7" would not line up with
        // the "7" in "17", which is the exact raggedness tabular figures exist to prevent.
        Box(Modifier.width(metrics.rankWidth), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = rank.toString(),
                // Tabular figures: proportional digits make a 50-row column look ragged.
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
        }
        // The leading pad IS the centring: it makes the content's box span from here to the
        // artwork, so centring inside it lands on the true midpoint of the gap.
        Box(
            modifier = Modifier
                .width(metrics.markerSlot)
                .padding(start = ART_LEADING_SPACE),
            contentAlignment = Alignment.Center,
        ) {
            when (movement) {
                is ChartMovement.Up -> Marker("▲", movement.places, chartClimbColor())
                is ChartMovement.Down -> Marker("▼", movement.places, chartFallColor())
                // NEW takes the accent so it reads as a label rather than as part of the rank; RE
                // uses onSurfaceVariant — a TEXT role. `outline` is an M3 boundary role for
                // dividers and field borders with no contrast guarantee, and under dynamic colour
                // it washed the badge out to near-illegible grey on a light theme.
                ChartMovement.New ->
                    MarkerLabel(stringResource(R.string.chart_new), MaterialTheme.colorScheme.primary)
                ChartMovement.Reentry ->
                    MarkerLabel(stringResource(R.string.chart_reentry), MaterialTheme.colorScheme.onSurfaceVariant)
                ChartMovement.Unchanged, null -> Unit
            }
        }
    }
}

/** Sized through the type scale (UI standards rule 8): labelSmall is the smallest role we have. */
@Composable
private fun Marker(glyph: String, places: Int, color: Color) {
    // One Text, not two: two siblings can be broken apart onto separate lines, which is how "▲24"
    // became a triangle over a stacked "2"/"4".
    Text(
        text = "$glyph$places",
        // labelMedium, not labelSmall: ▲ and ▼ differ only in ORIENTATION, and orientation is the
        // one cue a red/green colour-blind reader has left now that the two share a slot rather
        // than sitting above and below the rank. At 11sp that triangle was too small to rely on.
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun MarkerLabel(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The empty space the shared `ListItem` leaves before its artwork, taken from the constants
 * `ListItem` itself lays out with — derived, never copied, so retuning the row padding moves the
 * marker's centring with it instead of silently drifting.
 */
private val ART_LEADING_SPACE = ListArtLeadingSpace

/** A hair of breathing room so a glyph never sits flush against the edge of its slot. */
private val SLOT_MARGIN = 4.dp

/**
 * The measured widths of a chart rank cell: the rank slot, the marker slot, and their total.
 *
 * Every row of one list is given the SAME metrics, which is what keeps the artwork on a straight
 * vertical line — but the numbers come from measuring the real text rather than from constants
 * chosen by eye. That makes the cell adapt on its own to the user's font scale, to a translated
 * `NEW`/`RE`, to a different typeface, and to a chart with three-digit positions; each of those
 * silently clipped or misaligned a hardcoded slot.
 */
data class ChartRankMetrics(val rankWidth: Dp, val markerSlot: Dp, val total: Dp)

/**
 * Measures the widest content the list can contain, once, and hands the result to every row.
 *
 * [maxRank] sizes the digits and [maxDelta] sizes the marker — they are measured SEPARATELY on
 * purpose. A delta is `prevRank - rank`, a distance on the PREVIOUS chart, so it is not bounded by
 * any rank in this response: a server returning the top 40 of a 200-row chart can carry a `▲183`,
 * and sizing that slot from `maxRank` would clip it to `▲18` — a wrong number on screen, silently
 * contradicting the magnitude the same cell speaks.
 *
 * The marker is also sized against the translated `NEW`/`RE` labels, whichever is widest.
 */
@Composable
fun rememberChartRankMetrics(maxRank: Int, maxDelta: Int): ChartRankMetrics {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rankStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    val markerStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val newLabel = stringResource(R.string.chart_new)
    val reLabel = stringResource(R.string.chart_reentry)

    return remember(measurer, density, rankStyle, markerStyle, newLabel, reLabel, maxRank, maxDelta) {
        // Tabular figures make every digit the same width, so one wide digit stands in for any.
        fun widest(value: Int) = "8".repeat(value.coerceAtLeast(1).toString().length)
        fun widthOf(text: String, style: TextStyle) =
            with(density) { measurer.measure(text, style).size.width.toDp() }

        val rankWidth = widthOf(widest(maxRank), rankStyle) + SLOT_MARGIN
        val markerWidth = maxOf(
            widthOf(newLabel, markerStyle),
            widthOf(reLabel, markerStyle),
            widthOf("▼${widest(maxDelta)}", markerStyle),
        ) + SLOT_MARGIN
        ChartRankMetrics(
            rankWidth = rankWidth,
            markerSlot = ART_LEADING_SPACE + markerWidth,
            total = rankWidth + ART_LEADING_SPACE + markerWidth,
        )
    }
}

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
