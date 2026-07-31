package com.jtech.zemer.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.jtech.zemer.R
import com.jtech.zemer.search.ZemerCuratedPlaylist
import com.jtech.zemer.utils.joinByBullet
import com.metrolist.innertube.models.PlaylistItem
import kotlin.math.roundToInt

/**
 * The runtime part of a curated playlist's sub-label, sized to fit a grid card's two subtitle lines:
 * null (server doesn't know) hides the label; under two hours it reads in minutes (a known sub-minute
 * runtime rounds up to 1 rather than "0 minutes"); from two hours up it reads in whole rounded hours —
 * "40 hours", not the "2426 minutes" that truncated the card text.
 */
data class ZemerRuntimeLabel(val count: Int, val isHours: Boolean)

fun zemerCuratedPlaylistRuntime(totalDurationSec: Int?): ZemerRuntimeLabel? {
    val minutes = totalDurationSec?.let { (it / 60).coerceAtLeast(1) } ?: return null
    return if (minutes < 120) {
        ZemerRuntimeLabel(minutes, isHours = false)
    } else {
        ZemerRuntimeLabel((totalDurationSec / 3600f).roundToInt(), isHours = true)
    }
}

/** The localized runtime label ("164 minutes" / "40 hours"), or null to hide it. */
@Composable
fun zemerCuratedPlaylistRuntimeLabel(totalDurationSec: Int?): String? =
    zemerCuratedPlaylistRuntime(totalDurationSec)?.let { runtime ->
        if (runtime.isHours) {
            pluralStringResource(R.plurals.n_hour, runtime.count, runtime.count)
        } else {
            pluralStringResource(R.plurals.minute, runtime.count, runtime.count)
        }
    }

/**
 * One card of the "Zemer Playlists" surfaces: cover, title, and a "42 songs • 164 minutes" sub-label
 * (runtime hidden when unknown). Rendered with the shared [YouTubeGridItem] so it matches every other
 * Home card; the click target navigates to the curated detail screen — never a YouTube-playlist path.
 *
 * [showRuntime] is off on the compact Home-row card (fixed 128dp wide), where even the hours form of
 * the runtime overflows the two subtitle lines and truncates — there the sub-label is the song count
 * alone. The wider "See all" grid keeps the full label.
 */
@Composable
fun ZemerCuratedPlaylistGridItem(
    playlist: ZemerCuratedPlaylist,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
    showRuntime: Boolean = true,
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
            if (showRuntime) zemerCuratedPlaylistRuntimeLabel(playlist.totalDurationSec) else null,
        ),
        // The generated cover SVG already renders the playlist name, so the text title below would
        // be a duplicate — show only the count/runtime sub-label.
        showTitle = false,
        thumbnailRatio = 1f,
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}
