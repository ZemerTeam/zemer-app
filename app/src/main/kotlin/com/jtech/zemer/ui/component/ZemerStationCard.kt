package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.search.ZemerStation

/**
 * One "Zemer Radio" station card — a plain [GridItem] (the SAME geometry, typography and D-pad
 * focus treatment as every other home-row card, so the row aligns with its neighbors): the
 * station's branded broadcast SVG cover (served absolute; the shared SvgDecoder renders it), the
 * station title, and the live "Now: title — artist" line (refreshed once per home load — up to one
 * track stale by contract). Callers attach the tune-in `clickable` on [modifier], the shared grid
 * convention.
 */
@Composable
fun ZemerStationCard(
    station: ZemerStation,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
) {
    GridItem(
        title = station.title,
        subtitle = station.nowPlaying
            ?.let { stringResource(R.string.station_now_playing, it.title, it.artist) }
            .orEmpty(),
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
