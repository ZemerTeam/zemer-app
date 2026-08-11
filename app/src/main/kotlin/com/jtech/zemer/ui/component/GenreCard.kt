package com.jtech.zemer.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jtech.zemer.ui.theme.HeaderFontFamily

private val WEAVE_CELL = 40.dp
private val WEAVE_MOTIF = 16.dp
private const val WEAVE_DRIFT_MS = 24_000

/**
 * The slow-drifting motif weave (owner ask: the little background icons "move slowly and
 * everywhere") — a brick-staggered grid of small, upright repeats of the genre's motif, whisper
 * faint in the ONE theme accent. Shared by the catalog [GenreCard] and the genre detail header so
 * the card→page background is one continuous fabric.
 *
 * The motion re-issues the tiled blit EVERY frame in [drawWithCache]'s `onDrawBehind` (which reads
 * the animated `phase`), but the expensive part — rasterizing the vector motif — happens ONCE into a
 * tiny cached [ImageBitmap] (the cache block re-runs only on a size/motif change). Each frame just
 * blits that ~16dp tile across a brick-staggered grid at a `phase`-driven x offset in [0, cell], so
 * the drift loops seamlessly (grid period == one cell). This deliberately REPLACES an earlier
 * "rasterize the whole grid once into a `graphicsLayer` and slide the cached layer" trick: that
 * cached layer could be silently evicted when several coexist on the catalog, leaving a card's weave
 * blank with no input change to trigger a redraw. Blitting a cached tile is cheap enough that ~20 of
 * these stay smooth without depending on a persistent GPU layer. Always render as a
 * matchParentSize/fillMaxSize child BEHIND the content.
 */
@Composable
fun GenreWeaveLayer(
    motif: Painter,
    tint: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 0.06f,
) {
    val phase by rememberInfiniteTransition(label = "genre_weave")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = WEAVE_DRIFT_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "genre_weave_phase",
        )
    // Invariant per (tint) — hoisted out of the draw path so it is not reallocated per frame.
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Spacer(
        modifier
            .clipToBounds()
            .drawWithCache {
                val cell = WEAVE_CELL.toPx()
                val small = WEAVE_MOTIF.toPx()
                // Rasterize the tinted, faint motif ONCE into a small tile; every frame just blits it.
                val px = small.toInt().coerceAtLeast(1)
                val tile = ImageBitmap(px, px)
                CanvasDrawScope().draw(this, layoutDirection, Canvas(tile), Size(px.toFloat(), px.toFloat())) {
                    with(motif) { draw(Size(small, small), alpha = alpha, colorFilter = filter) }
                }
                onDrawBehind {
                    val drift = phase * cell
                    var row = 0
                    var y = cell / 4
                    while (y < size.height) {
                        val stagger = if (row % 2 == 0) 0f else cell / 2
                        // Start two cells before the left edge so the drift never reveals a gap.
                        var x = -2 * cell + stagger + drift
                        while (x < size.width + cell) {
                            drawImage(tile, topLeft = Offset(x, y))
                            x += cell
                        }
                        y += cell
                        row++
                    }
                }
            },
    )
}

/**
 * A big genre card for the catalog page: a monochrome slab textured with its drifting motif
 * [GenreWeaveLayer], plus the motif once more as the hero — FULLY VISIBLE, upright, end-aligned
 * with even padding (owner review: bleeding/rotated heroes read as cropped clutter). Single-line
 * auto-shrinking title bottom-start; no counts. D-pad: standard focus border, card-clipped.
 */
@Composable
fun GenreCard(
    title: String,
    slug: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Overrides the slug→motif lookup (music by default) so podcast genres get their own icons.
    @androidx.annotation.DrawableRes iconOverride: Int? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val motif = painterResource(iconOverride ?: genreIcon(slug))
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .focusBorder(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick)
            .height(96.dp),
    ) {
        GenreWeaveLayer(motif = motif, tint = accent, modifier = Modifier.fillMaxSize())
        Icon(
            painter = motif,
            contentDescription = null,
            tint = accent.copy(alpha = 0.30f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(48.dp),
        )
        AutoResizeText(
            text = title,
            fontSizeRange = FontSizeRange(11.sp, 16.sp),
            fontFamily = HeaderFontFamily,
            fontWeight = FontWeight.Bold,
            // ONE line, always (owner rule): a long title SHRINKS to fit its box instead of
            // wrapping ("Instrument/al") or running into the hero icon - the end clearance is the
            // icon (48) + its padding (16) + a gap.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 76.dp),
        )
    }
}
