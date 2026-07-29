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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.search.ZemerStation

/**
 * One "Zemer Radio" station card - a [GridItem] (the SAME geometry, typography and D-pad focus
 * treatment as every other home-row card, so the row aligns with its neighbors): the station's
 * branded broadcast SVG cover (served absolute; the shared SvgDecoder renders it), the station
 * title, and the live now-playing line. That line MARQUEES instead of truncating - a ~140dp card
 * fits ~15 characters, so an ellipsized "Now: ..." prefix line carried no information (the cover
 * already carries the LIVE branding, so the prefix is dropped too). Refreshed once per home load -
 * up to one track stale by contract. Callers attach the tune-in `clickable` on [modifier], the
 * shared grid convention.
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
                text = station.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        subtitle = {
            station.nowPlaying?.let { now ->
                Text(
                    text = stringResource(R.string.station_now_playing, now.title, now.artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }
        },
        thumbnailContent = {
            AsyncImage(
                model = station.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )
        },
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}
