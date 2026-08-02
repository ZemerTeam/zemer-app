package com.jtech.zemer.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.runtime.remember
import com.jtech.zemer.statuses.StatusContentFilter
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.statuses.visibleRecentIds
import com.jtech.zemer.ui.theme.HeaderFontFamily

/**
 * One JewishStatus creator as a WhatsApp/Stories-style avatar circle: a SEGMENTED story ring (one arc
 * per recent status) that is the app accent while any status is unseen and mutes to a subtle outline
 * once every status has been viewed (WhatsApp read state) — a read circle is still openable. The
 * avatar loads from the R2 CDN; an optional verified badge sits at the bottom-right. Fully app-themed.
 * Presentation-only; the caller wires the tap.
 */
@Composable
fun StatusCreatorCircle(
    creator: StatusCreator,
    seenPostIds: Set<String>,
    contentFilter: StatusContentFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // Ring over the statuses the user can actually VIEW under their content filter, so hidden-kind
    // statuses never show as segments. If the filter hides everything recent, fall back to the full
    // ring so the creator still shows a normal circle (never an empty/phantom ring, never dropped).
    val visibleIds = remember(creator, contentFilter) {
        creator.visibleRecentIds(contentFilter).ifEmpty { creator.recentPostIds }
    }
    val segments = visibleIds.size.coerceAtLeast(1)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(76.dp)
            // D-pad reachability (upstream rows omit it): the shared focus border + .focusable(), so the
            // Home row and See-all grid are navigable by remote. Wraps the click like the peer GenreCard.
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            // Segmented ring: one arc per recent status, with a small gap between arcs.
            Canvas(Modifier.size(64.dp)) {
                val strokeWidth = 3.dp.toPx()
                val gapDeg = if (segments > 1) 8f else 0f
                val sweep = (360f - gapDeg * segments) / segments
                val inset = strokeWidth / 2f
                val arcTopLeft = Offset(inset, inset)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                var start = -90f
                repeat(segments) { i ->
                    // Per-segment read state: an arc mutes to the subtle outline once ITS status is
                    // seen, so the accent arcs that remain show how many are left to view.
                    val postId = visibleIds.getOrNull(i)
                    val seen = postId != null && postId in seenPostIds
                    drawArc(
                        color = if (seen) colorScheme.outlineVariant else colorScheme.primary,
                        startAngle = start + gapDeg / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    start += sweep + gapDeg
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(statusAvatarUrl(creator.avatarPath))
                    .crossfade(true)
                    .build(),
                contentDescription = creator.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            // Single line + ellipsis, matching the app's card titles (e.g. ZemerStationCard) instead of
            // wrapping mid-word into a ragged second line. Heebo header face (the Genres treatment).
            text = creator.displayName,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = HeaderFontFamily,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
    }
}
