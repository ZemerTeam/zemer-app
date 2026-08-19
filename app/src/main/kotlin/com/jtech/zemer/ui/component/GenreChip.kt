@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A genre chip for the Home strip. Material 3 Expressive: a filled tonal [TonalToggleButton] — the SAME
 * component the Home content-tab selector and the library filter chips render as — so a chip presses with
 * the springy shape-morph (round → squarer) instead of sitting as a flat hollow outline. It is a
 * navigation chip, not a real toggle, so it is permanently `checked = false` and its `onCheckedChange`
 * just fires [onClick] (the [LibraryFilterChip] uses the mirror trick of a permanently-checked one).
 *
 * The chip keeps its two hand-tuned notes: a small per-genre motif icon ([genreIcon]) tinted with the ONE
 * theme accent (monochrome everywhere else), and that icon giving a springy little jump while pressed —
 * the chip body stays put beyond the button's own shape morph. Keyed off the server slug. The mandatory
 * D-pad focus treatment (docs/ui/standards.md §11) rides the chip's rounded shape.
 *
 * Deliberately SMALLER than the Home content-tab selector above it (which is the primary control): the
 * 48dp minimum-interactive floor is lifted, content padding is tight, and the icon/label are compact, so
 * a genre chip reads as a secondary strip instead of competing with the tab chips for attention.
 */
@Composable
fun GenreChip(
    title: String,
    slug: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Overrides the slug→motif lookup (music by default) so podcast genres get their own icons.
    @androidx.annotation.DrawableRes iconOverride: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "genre_chip_icon_jump",
    )
    // Lift the 48dp min-interactive floor so the chip can be a compact secondary control (the button
    // still keeps its own visual height from the content padding below).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        TonalToggleButton(
            checked = false,
            onCheckedChange = { onClick() },
            interactionSource = interactionSource,
            // Dim tonal fill (surfaceContainer, a step below the tab selector's) so the chip recedes
            // against the near-black background instead of reading as heavy chrome.
            colors = ToggleButtonDefaults.tonalToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            // Tight padding so the chip sits well below the full-size tab selector in the hierarchy.
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            // The stock toggle paints no visible focus indication of its own; add the D-pad ring on the
            // chip's rounded shape (the button's resting shape).
            modifier = modifier.focusBorder(RoundedCornerShape(20.dp)),
        ) {
            Icon(
                painter = painterResource(iconOverride ?: genreIcon(slug)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.width(5.dp))
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}
