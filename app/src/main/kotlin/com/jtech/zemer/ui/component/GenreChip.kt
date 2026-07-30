package com.jtech.zemer.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * A genre chip for the Home strip (owner direction, 2026-07-30 voice notes): the stock
 * [AssistChip], but SQUARISH (10dp corners, "do it more squarely") with its outline clearly
 * visible, and a small per-genre motif icon ([genreIcon]) tinted with the ONE theme accent —
 * monochrome everywhere else ("zero color, only nice accents"). Keyed off the server slug.
 *
 * The 48dp minimum-interactive-size enforcement is disabled HERE ONLY: it wraps every 32dp chip in
 * ~8dp of invisible padding on all sides, which blew the rows apart with phantom gaps. A dense
 * chip strip is the platform convention; the chip itself remains its full stock height.
 */
@Composable
fun GenreChip(
    title: String,
    slug: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The one playful note (owner ask: "do something cool with them"): the motif icon gives a
    // springy little jump while the chip is pressed. Icon only — the chip body stays put.
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "genre_chip_icon_jump",
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        AssistChip(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            // A whisper of the accent on the outline (full-strength gold read as heavy next to the
            // gold icon); the icon stays the accent's one loud note per chip.
            border = AssistChipDefaults.assistChipBorder(
                enabled = true,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            ),
            label = { Text(title) },
            // The mandatory D-pad treatment (docs/ui/standards.md §11) on the chip's own shape —
            // the stock chip is focusable but paints no visible focus indication of its own.
            modifier = modifier.focusBorder(RoundedCornerShape(10.dp)),
            leadingIcon = {
                Icon(
                    painter = painterResource(genreIcon(slug)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(AssistChipDefaults.IconSize)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            },
        )
    }
}
