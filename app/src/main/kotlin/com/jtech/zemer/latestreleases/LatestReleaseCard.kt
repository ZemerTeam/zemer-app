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
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.ui.component.AlbumBadges
import com.jtech.zemer.ui.component.SongDownloadBadge
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.utils.joinByBullet

/**
 * One Latest Releases card for the "See all" LIST ([YouTubeListItem]). The Home shelf renders the same
 * release as a Material 3 Expressive carousel hero ([LatestReleaseCarouselItem]); both share the release
 * binding helpers — album mapping, the "Artist • <relative date>" subtitle, the single's centred play
 * button, the now-playing state, the tap ([openOrPlay]: play a single / open an album), the long-press
 * [YouTubeAlbumMenu] and the [ReleaseBadges] — so the two surfaces can't drift.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LatestReleaseCard(
    release: LatestRelease,
    navController: NavController,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val album = remember(release.browseId) { release.toAlbumItem() }
    val dateLabel = remember(release.browseId) { release.relativeDateLabel() }
    val subtitle = joinByBullet(release.artistName, dateLabel)
    val clickable = Modifier.combinedClickable(
        onClick = { release.openOrPlay(navController, playerConnection) },
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

    YouTubeListItem(
        item = album,
        subtitleOverride = subtitle,
        centeredPlayButton = release.isPlayableSingle(),
        isActive = release.isNowPlaying(mediaMetadata),
        isPlaying = isPlaying,
        // A single is an AlbumItem (id = browseId) but plays its sampleVideoId, so the tap-to-play spinner
        // must track that id, not the browseId (the carousel already does this).
        preparingIdOverride = if (release.isPlayableSingle()) release.sampleVideoId else null,
        badges = { ReleaseBadges(release) },
        modifier = clickable,
    )
}

/**
 * Reflects the release's library state on the row, reactively. A single (one-track release) is
 * downloaded/played as its sample track, so its badge observes THAT song directly — this is why a
 * single's download progress shows immediately (the live MediaStore map is keyed by the videoId, so
 * no DB album row is needed). A multi-track release shows the shared album aggregate badge once it's
 * in the DB. Matches the single-vs-album split the card already uses for tap behaviour.
 */
@Composable
internal fun RowScope.ReleaseBadges(release: LatestRelease) {
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
