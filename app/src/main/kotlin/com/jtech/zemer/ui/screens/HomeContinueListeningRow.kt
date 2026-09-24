package com.jtech.zemer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.playback.EpisodeResume
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.utils.makeTimeString

/**
 * The Home "Continue Listening" strip: in-progress podcast episodes (most-recently-played first) as a
 * horizontal LazyRow. Its title is the standard NavigationTitle emitted by HomeScreen; this owns only
 * the row. Backed by the isolated fail-soft [com.jtech.zemer.viewmodels.ContinueListeningViewModel].
 */
@Composable
fun HomeContinueListeningRow(
    episodes: List<Song>,
    onPlay: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
        modifier = modifier,
    ) {
        items(items = episodes, key = { it.id }, contentType = { "continue_episode" }) { song ->
            ContinueListeningCard(
                song = song,
                onClick = { onPlay(song) },
                modifier = Modifier.width(GridThumbnailHeight + 24.dp),
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L)
    val timeLeft = EpisodeResume.timeLeftMs(song.song.lastPositionMs, durationMs)
        ?.let { makeTimeString(it) }
    Column(
        modifier = modifier
            .focusBorder(RoundedCornerShape(ThumbnailCornerRadius))
            .clickable(onClick = onClick)
            // 12dp to match the shared GridItem card inset, so this row's cards are flush with the
            // other home rows (Top/Trending/etc.) instead of sitting ~6dp further left.
            .padding(12.dp),
    ) {
        AsyncImage(
            model = song.song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
            modifier = Modifier
                .size(GridThumbnailHeight)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.song.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (timeLeft != null) {
            Text(
                text = stringResource(R.string.episode_time_left, timeLeft),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
