package com.jtech.zemer.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.search.ZemerStation

/**
 * One "Zemer Radio" station card - a [GridItem] (the SAME geometry, typography and D-pad focus
 * treatment as every other home-row card, so the row aligns with its neighbors). The branded
 * broadcast SVG cover already carries the station name, so the text under it does NOT repeat it:
 * it is the live now-playing SONG (bold, marqueeing - a ~140dp card truncates uselessly) over its
 * artist, refreshed by the on-screen ticker. The station title only appears as a fallback when the
 * server sent no nowPlaying. Callers attach the tune-in `clickable` on [modifier], the shared grid
 * convention.
 */
@Composable
fun ZemerStationCard(
    station: ZemerStation,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
) {
    GridItem(
        title = {
            Text(
                text = station.nowPlaying?.title ?: station.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
        },
        subtitle = {
            station.nowPlaying?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        thumbnailContent = {
            AsyncImage(
                model = station.thumbnail,
                contentDescription = station.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )
        },
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}
