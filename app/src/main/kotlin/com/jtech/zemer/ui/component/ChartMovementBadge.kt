package com.jtech.zemer.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import com.jtech.zemer.search.ChartMovement
import com.jtech.zemer.ui.theme.chartClimbColor
import com.jtech.zemer.ui.theme.chartFallColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A track's movement on an `auto-*` chart: `NEW` / `RE` pills, or ▲/▼ with the number of places.
 *
 * Two rules from the server's render guide are load-bearing:
 *
 * - **[movement] == null renders nothing** — not a dash, not a zero. Absent movement is normal (a
 *   curated non-chart playlist, a too-young rank history, or a ranking-formula change that reset the
 *   baseline) and must never fall back to a device-local diff, which would show different arrows to
 *   different users looking at the same chart.
 * - **The glyph never carries the meaning alone.** ▲ and ▼ differ mainly by colour in most palettes,
 *   so the whole badge exposes one spoken description and hides its parts from the accessibility
 *   tree. [rank] is the row's CURRENT 1-based position (its index in the list) — never `prevRank`,
 *   which is where the song was.
 */
@Composable
fun ChartMovementBadge(
    movement: ChartMovement?,
    rank: Int,
    modifier: Modifier = Modifier,
) {
    if (movement == null) return

    val description = when (movement) {
        ChartMovement.New -> stringResource(R.string.chart_movement_new, rank)
        ChartMovement.Reentry -> stringResource(R.string.chart_movement_reentry, rank)
        is ChartMovement.Up -> stringResource(R.string.chart_movement_up, movement.places, rank)
        is ChartMovement.Down -> stringResource(R.string.chart_movement_down, movement.places, rank)
        ChartMovement.Unchanged -> stringResource(R.string.chart_movement_unchanged, rank)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        when (movement) {
            ChartMovement.New -> MovementPill(
                label = stringResource(R.string.chart_new),
                color = MaterialTheme.colorScheme.primary,
            )

            ChartMovement.Reentry -> MovementPill(
                label = stringResource(R.string.chart_reentry),
                color = MaterialTheme.colorScheme.outline,
            )

            is ChartMovement.Up -> MovementArrow(
                icon = R.drawable.arrow_upward,
                places = movement.places,
                color = chartClimbColor(),
            )

            is ChartMovement.Down -> MovementArrow(
                icon = R.drawable.arrow_downward,
                places = movement.places,
                color = chartFallColor(),
            )

            // A dash reads better than a blank in a list of otherwise-badged rows.
            ChartMovement.Unchanged -> Text(
                text = "–",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MovementPill(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun MovementArrow(icon: Int, places: Int, color: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp),
    )
    Text(
        text = places.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/**
 * The "movement since" date, formatted for the reader's locale — or null to hide the label, which is
 * also the correct rendering when there are no badges to explain. An unparseable date hides the label
 * rather than showing a raw ISO string: the server owns this format, and a future change to it must
 * degrade quietly instead of leaking machine text into the header.
 */
fun chartAnchorLabel(anchorDate: String?): String? =
    anchorDate?.takeIf { it.isNotBlank() }?.let { iso ->
        runCatching {
            LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrNull()
    }
