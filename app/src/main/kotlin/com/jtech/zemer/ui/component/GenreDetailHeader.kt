package com.jtech.zemer.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.ui.theme.HeaderFontFamily

/**
 * The shared "genre face" header for a genre DETAIL page — the same three-layer treatment the music
 * and podcast genre pages both use, so they can't drift:
 *  1. an album/show-art mosaic of [coverUrls] (the ONE color source, by design), fading into the
 *     surface under a scrim so chrome above stays legible; empty → the surface shows through;
 *  2. the same drifting motif weave the genre card carries ([motifRes]) — card→page continuity;
 *  3. the big genre [title] in the app's display face.
 *
 * [action] is an optional slot below the title for a page-specific control — the music page passes its
 * gold "Play genre radio" pill; the podcast page passes none (podcasts have no genre radio). Content-
 * free of counts by design (a number reads as small; the page should read as complete).
 */
@Composable
fun GenreDetailHeader(
    title: String,
    coverUrls: List<String>,
    @DrawableRes motifRes: Int,
    modifier: Modifier = Modifier,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    // Graceful degradation: a cover that fails/loads paints a neutral tone, never a see-through gap.
    val coverFallback = remember(surface) { ColorPainter(surface) }
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val coverPlaceholder = remember(surfaceContainerHigh) { ColorPainter(surfaceContainerHigh) }
    val motif = painterResource(motifRes)
    // fillMaxWidth on the CONTAINER, not its children: the mosaic sizes to this box, and without it
    // the box shrinks to its widest child.
    Box(modifier = modifier.fillMaxWidth()) {
        if (coverUrls.isNotEmpty()) {
            Row(modifier = Modifier.matchParentSize()) {
                coverUrls.forEach { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = coverPlaceholder,
                        error = coverFallback,
                        fallback = coverFallback,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            // Scrim: covers glow through up top, melt into the surface below so the title sits on
            // solid ground in any theme.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.35f),
                            0.55f to surface.copy(alpha = 0.88f),
                            1f to surface,
                        ),
                    ),
            )
        }
        // The SAME drifting weave the catalog card carries — one continuous fabric from card to page.
        GenreWeaveLayer(
            motif = motif,
            tint = MaterialTheme.colorScheme.primary,
            alpha = 0.05f,
            modifier = Modifier.matchParentSize(),
        )
        Column(modifier = Modifier.padding(12.dp)) {
            // Expanded stage: tall art runway, then the title in the app's display face.
            Spacer(Modifier.height(96.dp))
            AutoResizeText(
                text = title,
                fontFamily = HeaderFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 1.1.em,
                fontSizeRange = FontSizeRange(30.sp, 44.sp),
            )
            action?.let {
                Spacer(Modifier.height(16.dp))
                it()
            }
        }
    }
}
