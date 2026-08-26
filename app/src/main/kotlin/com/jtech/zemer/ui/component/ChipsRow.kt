@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    firstChipFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    // Opt-in (the filter-prefilled search results screen): scroll the initially selected chip into
    // view once, and anchor [firstChipFocusRequester] to the SELECTED chip instead of the first -
    // the search screen's delayed TV focus grab otherwise yanks the row back to chip 0, hiding the
    // selection again. Default OFF: the library rows' rememberPreference-backed selection emits its
    // default before the stored value, which would latch the one-shot on chip 0 and (worse) move
    // their focus anchor unasked.
    revealSelectedChip: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val revealScope = rememberCoroutineScope()
    // One-shot: when the row OPENS with a non-first chip already selected (a filter-prefilled
    // search like the Podcasts browse's episode hand-off), scroll that chip into view - it may sit
    // past the fold and an invisible selection reads as "landed on All". Once only (saveable, so a
    // process restore doesn't re-scroll): later taps are on visible chips and must not yank the row.
    var revealedInitialChip by rememberSaveable { mutableStateOf(false) }
    val focusChipIndex =
        if (revealSelectedChip) chips.indexOfFirst { it.first == currentValue }.coerceAtLeast(0) else 0
    val revealLeadPx = with(LocalDensity.current) { 24.dp.toPx() }
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEachIndexed { index, (value, label) ->
            var isFocused by remember { mutableStateOf(false) }
            val borderColor by animateColorAsState(
                targetValue = if (isFocused && focusVisualsEnabled()) MaterialTheme.colorScheme.outline else Color.Transparent,
                label = "chip_focus_border"
            )
            // Material 3 Expressive: the selected filter morphs its shape (round -> squarer) and
            // presses springily. Same selection/onClick model as the old FilterChip, so every
            // ChipsRow caller (library tabs, Home selector, search filters) upgrades at once.
            TonalToggleButton(
                checked = currentValue == value,
                onCheckedChange = { onValueUpdate(value) },
                colors = ToggleButtonDefaults.tonalToggleButtonColors(containerColor = containerColor),
                modifier = Modifier
                    // EVERY chip routes D-pad up/down to the same target, not just the first — otherwise
                    // a chip the geometric focus search can't resolve upward from (e.g. the rightmost
                    // "Songs" chip with nothing directly above it) stays stuck while the leftmost chips
                    // move focus to the top bar.
                    .focusProperties {
                        if (upFocusRequester != null) up = upFocusRequester
                        if (downFocusRequester != null) down = downFocusRequester
                    }
                    .then(
                        if (index == focusChipIndex && firstChipFocusRequester != null) {
                            Modifier.focusRequester(firstChipFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .onGloballyPositioned { coords ->
                        if (revealSelectedChip && !revealedInitialChip && currentValue == value) {
                            revealedInitialChip = true
                            // Content coordinates equal scroll offsets here (the Row starts unscrolled);
                            // a small dp lead keeps the previous chip's edge peeking for scroll affordance.
                            val target = (coords.positionInParent().x - revealLeadPx).toInt().coerceAtLeast(0)
                            if (target > 0) revealScope.launch { scrollState.animateScrollTo(target) }
                        }
                    }
                    .focusable()
                    .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            ) {
                Text(label)
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * THE content-tab selector row (Home's Music/Radio/Podcasts/Videos, KidZone's Artists/Podcasts):
 * a [ChipsRow] with the standard breathing room (top 12 / bottom 4) on a full-width surface strip,
 * so every screen's content-type chips share one geometry and can't drift.
 */
@Composable
fun <E> ContentTabChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 12.dp, bottom = 4.dp),
    ) {
        ChipsRow(
            chips = chips,
            currentValue = currentValue,
            onValueUpdate = onValueUpdate,
        )
    }
}
