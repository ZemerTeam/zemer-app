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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
 *   tree.
 *
 * The description states the MOVEMENT only, never a chart position. The obvious position to quote —
 * the row's index — is an index into a client-filtered list (explicit-content filtering, plus the
 * All/Albums/Songs chip), while the delta is measured against the server's unfiltered chart. Pairing
 * them announced a position that contradicts its own delta, and two users with different filter
 * settings heard different chart positions for the same song. The list shows no rank numbers either,
 * so there is nothing on screen for the announcement to agree with.
 */
@Composable
fun ChartMovementBadge(
    movement: ChartMovement?,
    modifier: Modifier = Modifier,
) {
    if (movement == null) return

    val description = when (movement) {
        ChartMovement.New -> stringResource(R.string.chart_movement_new)
        ChartMovement.Reentry -> stringResource(R.string.chart_movement_reentry)
        ChartMovement.Unchanged -> stringResource(R.string.chart_movement_unchanged)
        // Plurals, not a hardcoded "places": a one-place move is the most common non-zero delta on a
        // weekly chart, and this string is the ONLY thing a screen reader gets for the row.
        is ChartMovement.Up ->
            pluralStringResource(R.plurals.chart_movement_up, movement.places, movement.places)
        is ChartMovement.Down ->
            pluralStringResource(R.plurals.chart_movement_down, movement.places, movement.places)
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
