package com.jtech.zemer.latestreleases

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.R
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.AlbumBadges
import com.jtech.zemer.ui.component.SongDownloadBadge
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope

/**
 * One Latest Releases card, shared by the Home shelf ([asGrid] = true, a [YouTubeGridItem]) and the
 * "See all" list ([asGrid] = false, a [YouTubeListItem]). Keeping the binding here — album mapping,
 * the "Artist • <relative date>" subtitle, the single's centred play button, the now-playing state,
 * the tap ([openOrPlay]: play a single / open an album) and the long-press [YouTubeAlbumMenu] — means
 * it exists once and can't drift between the two surfaces.
 *
 * [coroutineScope] is only used by the grid variant (the album play button); the list variant ignores
 * it, so callers that render a list may pass null.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LatestReleaseCard(
    release: LatestRelease,
    navController: NavController,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    asGrid: Boolean,
    coroutineScope: CoroutineScope? = null,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val album = remember(release.browseId) { release.toAlbumItem() }
    val dateLabel = remember(release.browseId) { release.relativeDateLabel() }
    val subtitle = joinByBullet(release.artistName, dateLabel)
    val clickable = Modifier.combinedClickable(
        onClick = { release.openOrPlay(navController, playerConnection, database) },
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                YouTubeAlbumMenu(
                    albumItem = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    )

    if (asGrid) {
        YouTubeGridItem(
            item = album,
            subtitleOverride = subtitle,
            centeredPlayButton = release.isPlayableSingle(),
            isActive = release.isNowPlaying(mediaMetadata),
            isPlaying = isPlaying,
            coroutineScope = coroutineScope,
            thumbnailRatio = 1f,
            badges = { ReleaseBadges(release) },
            modifier = clickable,
        )
    } else {
        YouTubeListItem(
            item = album,
            subtitleOverride = subtitle,
            centeredPlayButton = release.isPlayableSingle(),
            isActive = release.isNowPlaying(mediaMetadata),
            isPlaying = isPlaying,
            badges = { ReleaseBadges(release) },
            modifier = clickable,
        )
    }
}

/**
 * Reflects the release's library state on the row, reactively. A single (one-track release) is
 * downloaded/played as its sample track, so its badge observes THAT song directly — this is why a
 * single's download progress shows immediately (the live MediaStore map is keyed by the videoId, so
 * no DB album row is needed). A multi-track release shows the shared album aggregate badge once it's
 * in the DB. Matches the single-vs-album split the card already uses for tap behaviour.
 */
@Composable
private fun RowScope.ReleaseBadges(release: LatestRelease) {
    val database = LocalDatabase.current
    val videoId = release.sampleVideoId
    if (release.isPlayableSingle() && !videoId.isNullOrEmpty()) {
        val song by remember(videoId) { database.song(videoId) }.collectAsState(initial = null)
        if (song?.song?.liked == true) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp).padding(end = 2.dp),
            )
        }
        if (song?.song?.explicit == true) {
            Icon(
                painter = painterResource(R.drawable.explicit),
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(end = 2.dp),
            )
        }
        SongDownloadBadge(videoId, song?.song?.isDownloaded == true)
    } else {
        val album by remember(release.browseId) { database.album(release.browseId) }.collectAsState(initial = null)
        album?.let { AlbumBadges(album = it) }
    }
}
