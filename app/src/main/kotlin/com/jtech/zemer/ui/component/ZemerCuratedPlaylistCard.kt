package com.jtech.zemer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.jtech.zemer.R
import com.jtech.zemer.search.ZemerCuratedPlaylist
import com.jtech.zemer.utils.joinByBullet
import com.metrolist.innertube.models.PlaylistItem

/**
 * One card of the "Zemer Playlists" surfaces: cover, title, and a "42 songs • 164 minutes" sub-label
 * (runtime hidden when unknown). Rendered with the shared [YouTubeGridItem] so it matches every other
 * Home card; the click target navigates to the curated detail screen — never a YouTube-playlist path.
 *
 * [showRuntime] = false drops the runtime, leaving the song count as the whole sub-label.
 */
@Composable
fun ZemerCuratedPlaylistGridItem(
    playlist: ZemerCuratedPlaylist,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
    showRuntime: Boolean = true,
    // The compact Home row is just the cover (no sub-label at all); the wider See-all grid keeps
    // the count/runtime.
    showSubtitle: Boolean = true,
) {
    YouTubeGridItem(
        item = PlaylistItem(
            id = playlist.id,
            title = playlist.title,
            author = null,
            songCountText = null,
            thumbnail = playlist.thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        ),
        subtitleOverride = joinByBullet(
            pluralStringResource(R.plurals.n_song, playlist.trackCount, playlist.trackCount),
            if (showRuntime) runtimeLabel(playlist.totalDurationSec) else null,
        ),
        // The generated cover SVG already renders the playlist name, so the text title below would
        // be a duplicate — show only the count/runtime sub-label (and nothing at all on Home).
        showTitle = false,
        showSubtitle = showSubtitle,
        thumbnailRatio = 1f,
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}
