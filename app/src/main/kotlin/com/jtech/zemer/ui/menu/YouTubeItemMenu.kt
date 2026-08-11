package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope

/**
 * The one YTItem -> YouTube*Menu dispatcher, shared by every discovery surface (search results, the
 * search dropdown, Home, home see-all, artist pages) that long-presses / 3-dots a mixed list of
 * [YTItem]s. Returns the menu content to hand to `menuState.show(...)`, collapsing the four-branch
 * `when (item)` block that was copy-pasted at each site.
 *
 * [isVideo] is supplied by the caller from its OWN filter/section context (the "Videos" chip, a
 * videos row, etc.) and is deliberately NOT re-derived here — every surface keeps exactly the
 * video-ness it passed before, so this is a pure de-duplication with no behavior change.
 */
fun ytItemMenu(
    item: YTItem,
    navController: NavController,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    isVideo: Boolean = false,
): @Composable ColumnScope.() -> Unit = {
    when (item) {
        // A podcast SHOW gets its own menu — the playlist menu would save an MPSP id as a music
        // PlaylistEntity (a broken, permanently empty library row). Episodes reuse the song menu.
        is com.metrolist.innertube.models.PodcastItem -> YouTubePodcastMenu(
            podcast = item,
            navController = navController,
            onDismiss = onDismiss,
        )
        is com.metrolist.innertube.models.EpisodeItem -> YouTubeSongMenu(
            song = item.asSongItem(),
            navController = navController,
            onDismiss = onDismiss,
        )
        is SongItem -> YouTubeSongMenu(
            song = item,
            navController = navController,
            onDismiss = onDismiss,
            isVideo = isVideo,
        )
        is AlbumItem -> YouTubeAlbumMenu(
            albumItem = item,
            navController = navController,
            onDismiss = onDismiss,
        )
        is ArtistItem -> YouTubeArtistMenu(
            artist = item,
            onDismiss = onDismiss,
        )
        is PlaylistItem -> YouTubePlaylistMenu(
            playlist = item,
            coroutineScope = coroutineScope,
            onDismiss = onDismiss,
        )
    }
}
