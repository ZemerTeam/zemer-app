package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.playback.DownloadStateResolver
import com.jtech.zemer.playback.DownloadStatus

/**
 * The UI-layer face of [DownloadStateResolver]: a tiny set of composables that EVERY download badge,
 * row and menu reads, so download/progress state can never diverge between surfaces again. Each one
 * combines the persisted `isDownloaded` flag with the live in-session MediaStore map; none of them
 * touch the dead legacy ExoPlayer download flow.
 */

/** One song's unified status, recomputed as its live MediaStore state changes. */
@Composable
fun rememberSongDownloadStatus(songId: String, isDownloaded: Boolean): DownloadStatus {
    val live by LocalDownloadUtil.current.getMediaStoreDownload(songId).collectAsState(initial = null)
    return DownloadStateResolver.forSong(isDownloaded, live)
}

@Composable
fun rememberSongDownloadStatus(song: Song): DownloadStatus =
    rememberSongDownloadStatus(song.id, song.song.isDownloaded)

/** One song's live progress fraction (0..1): 1f once downloaded, else the active download's progress. */
@Composable
fun rememberSongDownloadProgress(songId: String, isDownloaded: Boolean): Float {
    val live by LocalDownloadUtil.current.getMediaStoreDownload(songId).collectAsState(initial = null)
    return DownloadStateResolver.songProgress(isDownloaded, live)
}

/** Aggregate status for a collection (album / playlist / multi-select), recomputed live. */
@Composable
fun rememberAggregateDownloadStatus(songs: List<Song>): DownloadStatus {
    val live by LocalDownloadUtil.current.getAllMediaStoreDownloads().collectAsState()
    return remember(songs, live) { DownloadStateResolver.aggregateSongs(songs, live) }
}

/** Aggregate progress (0..1) for a collection, for a determinate header progress bar. */
@Composable
fun rememberAggregateDownloadProgress(songs: List<Song>): Float {
    val live by LocalDownloadUtil.current.getAllMediaStoreDownloads().collectAsState()
    return remember(songs, live) { DownloadStateResolver.aggregateProgress(songs, live) }
}

/**
 * The one download badge: a filled "offline" check when downloaded, a progress ring (determinate when
 * [progress] is known, indeterminate otherwise) while downloading, nothing when not downloaded.
 */
@Composable
fun RowScope.DownloadStatusIcon(status: DownloadStatus, progress: Float = 0f) {
    when (status) {
        DownloadStatus.DOWNLOADED -> Icon(
            painter = painterResource(R.drawable.offline),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp),
        )
        DownloadStatus.DOWNLOADING -> if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp),
            )
        } else {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp),
            )
        }
        DownloadStatus.NOT_DOWNLOADED -> Unit
    }
}

/**
 * Convenience badge for a song row: observes the song's live state and renders the unified
 * [DownloadStatusIcon] with live progress. The default badge for every song list/grid row.
 */
@Composable
fun RowScope.SongDownloadBadge(songId: String, isDownloaded: Boolean) {
    val status = rememberSongDownloadStatus(songId, isDownloaded)
    val progress = rememberSongDownloadProgress(songId, isDownloaded)
    DownloadStatusIcon(status, progress)
}

/**
 * THE aggregate download action button for an album / playlist header (Download → live progress ring →
 * Remove), shared by every screen header so look, behaviour and D-pad focus border can't drift. Reads
 * the unified [rememberAggregateDownloadStatus]; tapping while downloading runs [onCancelAll] (defaults
 * to [onRemoveAll]). Carries the standard focus-border treatment so it stays D-pad navigable.
 */
@Composable
fun AggregateDownloadButton(
    songs: List<Song>,
    onDownloadAll: () -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier,
    onCancelAll: () -> Unit = onRemoveAll,
) {
    val status = rememberAggregateDownloadStatus(songs)
    val progress = rememberAggregateDownloadProgress(songs)
    val focused = remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "download_button_focus_border",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable()
            .onFocusChanged { focused.value = it.isFocused },
    ) {
        when (status) {
            DownloadStatus.DOWNLOADED -> IconButton(onClick = onRemoveAll) {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = stringResource(R.string.remove_download),
                )
            }
            DownloadStatus.DOWNLOADING -> IconButton(onClick = onCancelAll) {
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            DownloadStatus.NOT_DOWNLOADED -> IconButton(onClick = onDownloadAll) {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = stringResource(R.string.action_download),
                )
            }
        }
    }
}
