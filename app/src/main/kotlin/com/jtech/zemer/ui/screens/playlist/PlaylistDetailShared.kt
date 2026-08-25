package com.jtech.zemer.ui.screens.playlist

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.shimmer.ButtonPlaceholder
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.utils.makeTimeString

/**
 * The loading skeleton every playlist-detail screen shows: header cover + text placeholders, the
 * Play/Shuffle button pair, then row placeholders. ONE copy so the online and curated screens can't
 * drift apart.
 */
@Composable
fun PlaylistHeaderShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(AlbumThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                        .background(MaterialTheme.colorScheme.onSurface),
                )

                Spacer(Modifier.width(16.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    TextPlaceholder()
                    TextPlaceholder()
                    TextPlaceholder()
                }
            }

            Spacer(Modifier.padding(8.dp))

            Row {
                ButtonPlaceholder(Modifier.weight(1f))

                Spacer(Modifier.width(12.dp))

                ButtonPlaceholder(Modifier.weight(1f))
            }
        }

        repeat(6) {
            ListItemPlaceHolder()
        }
    }
}

/**
 * The playlist-header transport pair — filled Play + outlined Shuffle, equal weights. ONE copy for
 * every playlist-detail screen; either action can be null to hide its button (the online screen
 * shows Shuffle only when the playlist has a shuffle endpoint).
 */
@Composable
fun PlaylistPlayShuffleButtons(
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        if (onPlay != null) {
            Button(
                onClick = onPlay,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.play))
            }
        }

        if (onShuffle != null) {
            OutlinedButton(
                onClick = onShuffle,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.shuffle))
            }
        }
    }
}

/**
 * The playlist-detail header (cover + title + item count + duration + a trailing action Row + the
 * Play/Shuffle pair). ONE copy for the auto/top/downloaded-videos detail screens so the layout can't
 * drift. [aggregateDownload] is the optional download-all control (null for screens with none, e.g.
 * Downloaded Videos); [pluralRes] is the count plural (`n_song` vs `n_video`); [totalDurationMs] is
 * the summed duration already in milliseconds.
 */
@Composable
fun PlaylistDetailHeader(
    coverUrl: String?,
    title: String,
    itemCount: Int,
    @PluralsRes pluralRes: Int,
    totalDurationMs: Long,
    aggregateDownload: (@Composable () -> Unit)?,
    onAddToQueue: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(AlbumThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .fillMaxWidth(),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                AutoResizeText(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSizeRange = FontSizeRange(16.sp, 22.sp),
                )

                Text(
                    text = pluralStringResource(pluralRes, itemCount, itemCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                )

                Text(
                    text = makeTimeString(totalDurationMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                )

                Row {
                    aggregateDownload?.invoke()

                    IconButton(
                        onClick = onAddToQueue,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                        )
                    }
                }
            }
        }

        PlaylistPlayShuffleButtons(
            onPlay = onPlay,
            onShuffle = onShuffle,
        )
    }
}
