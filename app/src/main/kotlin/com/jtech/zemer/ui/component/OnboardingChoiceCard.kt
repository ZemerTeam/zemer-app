package com.jtech.zemer.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CardColors

/**
 * The standard M3 card fill shared by every onboarding selectable/status card, so cards, buttons and
 * the search field all read as one family: a SOLID container tone (never an alpha-blended
 * `surfaceVariant` hack) — [secondaryContainer] when active/selected, [surfaceContainerHigh] otherwise
 * (the same neutral fill `OnboardingActionButton` and the shared search field use). Lives here in
 * `ui/component` alongside the rest of the shared onboarding pieces.
 */
@Composable
internal fun onboardingCardColors(active: Boolean): CardColors = CardDefaults.cardColors(
    // surfaceContainer (not High) on purpose: a card's inner tonal action button is
    // surfaceContainerHighest, so the pill always sits two tones above its card and stays visible.
    containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * The shared radio-select choice card the onboarding steps use (bottom-nav setup, search backup) —
 * one implementation instead of a per-screen copy, carrying the mandatory D-pad treatment
 * (`.focusBorder()` before the clickable, per docs/ui/standards.md §11) that a bespoke card is
 * exactly how a screen forgets.
 */
@Composable
fun OnboardingChoiceCard(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                ) else Modifier
            ),
        colors = onboardingCardColors(active = selected),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusBorder()
                .clickable(onClick = onSelect)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}
