package com.jtech.zemer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner treatment for row [index] of a [count]-row settings card group (the M3 Expressive
 * grouped-card stack, matching upstream Metrolist's settings look): a lone card is fully rounded,
 * the group's outer edges get the large radius, and the seams between neighbours get the small one,
 * so each row reads as its own card while the group reads as one cluster. Pure, so the geometry is
 * unit-tested ([settingsCardCorners] returns top/bottom radii in dp).
 */
fun settingsCardCorners(index: Int, count: Int): Pair<Int, Int> {
    val top = if (index == 0) SETTINGS_CARD_OUTER_RADIUS else SETTINGS_CARD_INNER_RADIUS
    val bottom = if (index == count - 1) SETTINGS_CARD_OUTER_RADIUS else SETTINGS_CARD_INNER_RADIUS
    return top to bottom
}

/** Breathing room between a settings screen's top bar and its first group title (owner ask). */
val SettingsScreenTopSpacing = 12.dp

const val SETTINGS_CARD_OUTER_RADIUS = 24
const val SETTINGS_CARD_INNER_RADIUS = 6

/** The gap between neighbouring cards in a stack - shared with every card-stack renderer. */
val SettingsCardGap = 4.dp

fun settingsCardShape(index: Int, count: Int): RoundedCornerShape {
    val (top, bottom) = settingsCardCorners(index, count)
    return RoundedCornerShape(
        topStart = top.dp, topEnd = top.dp,
        bottomStart = bottom.dp, bottomEnd = bottom.dp,
    )
}

/** The one card fill for the stacks - a change here restyles every card-stack surface together. */
@Composable
fun settingsCardFill() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

/**
 * A settings group in the per-item CARD style: an optional group title over a 4dp-gapped stack in
 * which EVERY row slot sits inside its own position-shaped card ([settingsCardCorners]). The rows
 * are the existing preference composables ([PreferenceEntry] / [SwitchPreference] /
 * [ListPreference] / [SliderPreference] / anything row-shaped) passed as slots, so every dialog,
 * slider, focus and conditional-visibility behavior is untouched - a screen migrates by grouping
 * its row run into `rows = listOf({ ... }, { ... })` (conditionals via `buildList`). An empty
 * [rows] renders NOTHING, title included, so a fully-conditional group collapses cleanly.
 */
@Composable
fun SettingsCardGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    // The stack's screen-edge inset. Screens whose scroll column already applies its own
    // horizontal padding pass 0.dp so the cards don't double-indent against their siblings.
    horizontalPadding: Dp = 16.dp,
    // Optional info content between the title and the card stack (e.g. Storage's size-used
    // caption and usage bars) - section-scoped context that is not a tappable row. Rendered
    // inside the group's inset so it stays visually attached to ITS section.
    headerContent: (@Composable () -> Unit)? = null,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        title?.let {
            PreferenceGroupTitle(
                title = it,
                padding = PaddingValues(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        headerContent?.invoke()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            rows.forEachIndexed { index, row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(settingsCardShape(index, rows.size))
                        .background(settingsCardFill()),
                ) {
                    row()
                }
            }
        }
    }
}
