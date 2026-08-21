@file:Suppress("unused")

package com.jtech.zemer.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.constants.ListItemHorizontalPadding
import com.jtech.zemer.constants.ListThumbnailPadding
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.SwipeToSongKey
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.toast
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.queues.LocalAlbumRadio
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.utils.makeTimeString
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val ActiveBoxAlpha = 0.6f

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    // Gently scroll a title too long for one line instead of ellipsizing it (podcast rows: long
    // show/episode titles). Off by default so music rows are unchanged.
    titleMarquee: Boolean = false,
    // Paint occurrences of this query in the title in the accent color (the browse screens'
    // instant local filter). Null/blank renders the plain title.
    titleHighlight: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.secondaryContainer
            isFocused && focusVisualsEnabled() -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        label = "list_item_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primary
            isFocused && focusVisualsEnabled() -> MaterialTheme.colorScheme.outline
            else -> Color.Transparent
        },
        label = "list_item_focus_border"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressBounce()
            // onFocusChanged only observes focus targets AFTER it in the chain, so it must precede
            // focusable() (the FocusBorder.kt order) - reversed, the row's own focus is never seen.
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .height(ListItemHeight)
            .padding(horizontal = ListItemHorizontalPadding)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
    ) {
        Box(Modifier.padding(ListThumbnailPadding), contentAlignment = Alignment.Center) { thumbnailContent() }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                text = rememberHighlightedText(title, titleHighlight),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                // The row's own focus re-arms the one-shot glide for D-pad/TV users.
                modifier = if (titleMarquee) Modifier.gentleMarquee(focused = isFocused) else Modifier
            )
            if (subtitle != null) Row(verticalAlignment = Alignment.CenterVertically) { subtitle() }
        }
        trailingContent()
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    titleMarquee: Boolean = false,
    titleHighlight: String? = null
) = ListItem(
    title = title,
    modifier = modifier,
    isActive = isActive,
    titleMarquee = titleMarquee,
    titleHighlight = titleHighlight,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(text = subtitle, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    // Gently scroll an overflowing title once instead of ellipsizing, re-armed on D-pad focus. The
    // card applies gentleMarquee AROUND the opaque title slot, so the slot content itself must not
    // carry its own marquee when this is set.
    titleMarquee: Boolean = false,
    // Center the text block under the artwork (the whitelist browse tiles). Column alignment, not
    // just textAlign, so the marquee-wrapped title Box and the badge/subtitle row center too.
    centerContent: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused && focusVisualsEnabled()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "grid_item_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused && focusVisualsEnabled()) MaterialTheme.colorScheme.outline else Color.Transparent,
        label = "grid_item_focus_border"
    )
    val baseModifier = modifier
        .pressBounce()
        .padding(12.dp)
        // onFocusChanged only observes focus targets AFTER it in the chain, so it must precede
        // focusable() (the FocusBorder.kt order) - reversed, the card's own focus is never seen.
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .clip(RoundedCornerShape(12.dp))
        .background(backgroundColor)
        .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(12.dp))

    Column(
        horizontalAlignment = if (centerContent) Alignment.CenterHorizontally else Alignment.Start,
        modifier = if (fillMaxWidth) {
            baseModifier.fillMaxWidth()
        } else {
            baseModifier.width(GridThumbnailHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }
                .aspectRatio(thumbnailRatio)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (titleMarquee) {
            // The title slot is opaque here, so the marquee wraps it (basicMarquee animates any
            // overflowing child); the card's own focus re-arms the glide for D-pad/TV users. The
            // slot content must NOT carry its own marquee - two nested marquees fight.
            Box(Modifier.gentleMarquee(focused = isFocused)) { title() }
        } else {
            title()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            subtitle()
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    // Gently scroll a title too long for one narrow card instead of ellipsizing it (podcast browse
    // cards) - the same one-glide feel as the podcast list rows. Off by default.
    titleMarquee: Boolean = false,
    // Center the title/subtitle under the artwork (the whitelist browse tiles).
    centerContent: Boolean = false,
    // Paint occurrences of this query in the title in the accent color (the browse screens'
    // instant local filter). Null/blank renders the plain title.
    titleHighlight: String? = null,
) = GridItem(
    modifier = modifier,
    titleMarquee = titleMarquee,
    centerContent = centerContent,
    title = {
        Text(
            text = rememberHighlightedText(title, titleHighlight),
            style = MaterialTheme.typography.bodyMedium,  // Made smaller (was bodyLarge)
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centerContent) TextAlign.Center else TextAlign.Start,
            // The marquee (when titleMarquee) is applied by GridItem around the title slot; under it
            // this fillMaxWidth is inert (a marquee measures its child unbounded), and without it
            // fillMaxWidth is what lets the text fill the card.
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (song.song.isVideo) {
            Icon.Video()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            SongDownloadBadge(song.id, song.song.isDownloaded)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            // Episodes carry long titles; decided by type HERE so every caller (library tabs,
            // auto-playlists, history) gets the glide without a per-call-site flag to forget.
            titleMarquee = song.song.isEpisode,
            subtitle = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize),
                    isPreparing = rememberIsPreparing(song.song.id)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.isVideo) {
            Icon.Video()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            SongDownloadBadge(song.id, song.song.isDownloaded)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = song.song.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = {
        val preparing = rememberIsPreparing(song.song.id)
        ItemThumbnail(
            thumbnailUrl = song.song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(GridThumbnailHeight),
            isPreparing = preparing
        )
        if (!isActive && !preparing) {
            OverlayPlayButton(
                visible = true
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

/**
 * The standard library badges for an album row — bookmarked / explicit / aggregate download state
 * (downloaded when every track is, downloading when any is). Single source of truth shared by the
 * library album rows and any other surface that renders an album (e.g. the Latest Releases rows).
 */
@Composable
fun RowScope.AlbumBadges(
    album: Album,
    showLikedIcon: Boolean = true,
) {
    val database = LocalDatabase.current

    val songs by produceState(initialValue = emptyList(), album.id) {
        withContext(Dispatchers.IO) {
            value = database.albumSongs(album.id).first()
        }
    }

    val downloadStatus = rememberAggregateDownloadStatus(songs)
    val downloadProgress = rememberAggregateDownloadProgress(songs)

    if (showLikedIcon && album.album.bookmarkedAt != null) {
        Icon.Favorite()
    }
    if (album.album.explicit) {
        Icon.Explicit()
    }
    DownloadStatusIcon(downloadStatus, downloadProgress)
}

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        AlbumBadges(album = album, showLikedIcon = showLikedIcon)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current

        val songs by produceState(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val downloadStatus = rememberAggregateDownloadStatus(songs)
        val downloadProgress = rememberAggregateDownloadProgress(songs)

        if (album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        DownloadStatusIcon(downloadStatus, downloadProgress)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = album.album.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = album.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val context = LocalContext.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                scope.launch {
                    val albumWithSongs = withContext(Dispatchers.IO) {
                        database.albumWithSongs(album.id).firstOrNull()
                    }
                    albumWithSongs?.let {
                        playerConnection.playQueue(LocalAlbumRadio(it, context = context))
                    }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {}
) = ListItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                playlist.playlist.remoteSongCount,
                playlist.playlist.remoteSongCount
            )
        } else {
            pluralStringResource(
                R.plurals.n_song,
                playlist.songCount,
                playlist.songCount
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    // R.drawable.backup as placeholder
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2)
                )
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        val subtitle = if (autoPlaylist) {
            ""
        } else {
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.playlist.remoteSongCount,
                    playlist.playlist.remoteSongCount
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = width,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    // R.drawable.backup as placeholder
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(painter),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(width / 2)
                    )
                }
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle = joinByBullet(
            mediaMetadata.artists.joinToString { it.name },
            makeTimeString(mediaMetadata.duration * 1000L)
        ),
        badges = {
            if (mediaMetadata.isVideo) Icon.Video()
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    subtitleOverride: String? = null,
    centeredPlayButton: Boolean = false,
    // The id whose play the tap-to-play spinner should track, when it differs from [item].id. A Latest
    // Releases single is an AlbumItem (id = browseId) but plays its sampleVideoId, so the See-all list
    // passes that here; null falls back to item.id (the normal case).
    preparingIdOverride: String? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }
        val albumSongs by produceState(initialValue = emptyList<Song>(), item.id) {
            if (item is AlbumItem) withContext(Dispatchers.IO) {
                value = database.albumSongs(item.id).first()
            }
        }

        if ((item is SongItem && song?.song?.liked == true) ||
            (item is AlbumItem && album?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem && item.isVideo) Icon.Video()
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon.Library()
        }
        when (item) {
            is SongItem -> SongDownloadBadge(item.id, song?.song?.isDownloaded == true)
            is AlbumItem -> DownloadStatusIcon(
                rememberAggregateDownloadStatus(albumSongs),
                rememberAggregateDownloadProgress(albumSongs),
            )
            else -> Unit
        }
    },
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            // Podcast shows/episodes carry long titles; gently scroll them instead of clipping.
            titleMarquee = item is PodcastItem || item is EpisodeItem,
            subtitle = subtitleOverride ?: when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
                is PodcastItem -> joinByBullet(item.author?.name, item.episodeCountText)
                is EpisodeItem -> joinByBullet(item.publishDateText, item.duration?.let { makeTimeString(it.times(1000L)) })
            },
            badges = badges,
            thumbnailContent = {
                val preparing = rememberIsPreparing(preparingIdOverride ?: item.id)
                Box(contentAlignment = Alignment.Center) {
                    ItemThumbnail(
                        thumbnailUrl = item.thumbnail,
                        albumIndex = albumIndex,
                        isSelected = isSelected,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
                        modifier = Modifier.size(ListThumbnailSize),
                        isPreparing = preparing
                    )
                    // A single shows the centred play button on its artwork (album rows stay plain).
                    if (centeredPlayButton && !isActive && !preparing) {
                        OverlayPlayButton(visible = true)
                    }
                }
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = item.copy(thumbnail = item.thumbnail.resize(544,544)).toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun EpisodeListItem(
    episode: EpisodeItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    resumePositionMs: Long? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    // For an in-progress (not finished, not just-started) episode, show how much time is left.
    val durationMs = episode.duration?.times(1000L)
    val timeLeft = resumePositionMs
        ?.takeIf { com.jtech.zemer.playback.EpisodeResume.shouldResume(it, durationMs) }
        ?.let { pos -> durationMs?.let { d -> makeTimeString((d - pos).coerceAtLeast(0)) } }
    ListItem(
        title = episode.title,
        titleMarquee = true,
        subtitle = joinByBullet(
            episode.publishDateText,
            if (timeLeft != null) stringResource(R.string.episode_time_left, timeLeft)
            else episode.duration?.let { makeTimeString(it.times(1000L)) }
        ),
        badges = {
            if (episode.explicit) Icon.Explicit()
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = episode.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize),
                isPreparing = rememberIsPreparing(episode.id)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive
    )
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    // The per-card video badge is redundant (and steals subtitle width, forcing an ugly
    // "Artist •" / "duration" wrap) in a row that is ALREADY all-videos and labelled as such
    // (Home Featured Videos, its see-all, the artist Videos section). Pass false there; leave it
    // on for mixed contexts like search where the badge distinguishes a video from a song.
    showVideoBadge: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }
        val albumSongs by produceState(initialValue = emptyList<Song>(), item.id) {
            if (item is AlbumItem) withContext(Dispatchers.IO) {
                value = database.albumSongs(item.id).first()
            }
        }

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (showVideoBadge && item is SongItem && item.isVideo) Icon.Video()
        if (item is SongItem && song?.song?.inLibrary != null) Icon.Library()
        when (item) {
            is SongItem -> SongDownloadBadge(item.id, song?.song?.isDownloaded == true)
            is AlbumItem -> DownloadStatusIcon(
                rememberAggregateDownloadStatus(albumSongs),
                rememberAggregateDownloadProgress(albumSongs),
            )
            else -> Unit
        }
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9 else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    subtitleOverride: String? = null,
    centeredPlayButton: Boolean = false,
    // Suppress the text title when the thumbnail already carries it (the curated Zemer-playlist
    // covers bake the title into the SVG, so the label below would just repeat it).
    showTitle: Boolean = true,
    // Suppress the sub-label entirely (the compact Home curated card is just the cover).
    showSubtitle: Boolean = true,
) = GridItem(
    // Podcast shows/episodes carry long titles; give their cards the same calm one-shot glide
    // (re-armed on D-pad focus) as their list rows, instead of the looping music-card marquee -
    // otherwise the Podcasts Home shelves/see-alls/genre grids loop forever while the rows below glide.
    titleMarquee = showTitle && (item is PodcastItem || item is EpisodeItem),
    title = {
        if (showTitle) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
                // Podcast/episode cards marquee via GridItem's titleMarquee wrapper (never doubled);
                // every other card keeps its pre-existing looping marquee.
                modifier = if (item is PodcastItem || item is EpisodeItem) Modifier.fillMaxWidth()
                else Modifier.basicMarquee().fillMaxWidth()
            )
        }
    },
    subtitle = {
        val subtitle = if (!showSubtitle) null else subtitleOverride ?: when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            is PodcastItem -> joinByBullet(item.author?.name, item.episodeCountText)
            is EpisodeItem -> joinByBullet(item.publishDateText, item.duration?.let { makeTimeString(it.times(1000L)) })
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                // One line, ellipsized — a 2-line subtitle on a narrow card orphans the " • " bullet
                // ("Artist •" / "3:49"). Matches the string GridItem overload; keeps card heights uniform.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val context = LocalContext.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()
        val preparing = rememberIsPreparing(item.id)

        ItemThumbnail(
            // A video's derived thumbnail is `.../hqdefault.jpg` (4:3, letterboxed) so the square
            // center-crop shows black bars. For the CARD only, swap to mqdefault (clean 16:9, no bars);
            // item.thumbnail (hence the 544px now-playing / lockscreen art) stays hqdefault so it doesn't
            // degrade. Only rewrites the derived ytimg URL — real server art passes through unchanged.
            thumbnailUrl = if (item is SongItem && item.isVideo) {
                item.thumbnail?.replace("/hqdefault.jpg", "/mqdefault.jpg")
            } else {
                item.thumbnail
            },
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
            isPreparing = preparing,
        )

        // A single / episode (or an explicit centeredPlayButton) gets the song-style centred play button
        // instead of the album's corner one, so it reads as "tap to play" like the Keep Listening cards.
        if ((item is SongItem || item is EpisodeItem || centeredPlayButton) && !isActive && !preparing) {
            OverlayPlayButton(
                visible = true
            )
        }

        AlbumPlayButton(
            visible = item is AlbumItem && !centeredPlayButton && !isActive,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    var albumWithSongs = database.albumWithSongs(item.id).first()
                    if (albumWithSongs?.songs.isNullOrEmpty()) {
                        YouTube.album(item.id).onSuccess { albumPage ->
                            database.transaction { insert(albumPage) }
                            albumWithSongs = database.albumWithSongs(item.id).first()
                        }.onFailure { reportException(it) }
                    }
                    albumWithSongs?.let {
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(LocalAlbumRadio(it, context = context))
                        }
                    }
                }
            }
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = true,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = true
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
    // The tapped item is resolving/buffering (see PlayerConnection.preparingMediaId): show a loading
    // spinner over the cover instead of the play / now-playing overlays until audio actually starts.
    isPreparing: Boolean = false
) {
        // A currently-PLAYING card morphs its artwork to a scalloped expressive silhouette (and pops
        // once as it BECOMES active - rising-edge only, so the card it left never bounces too), so the
        // active item reads as playing beyond the equalizer badge. While PREPARING it is not playing yet,
        // so the active treatment (morph, pop, equalizer) waits for real playback.
        val effectiveActive = isActive && !isPreparing
        val effectiveShape = if (effectiveActive) expressivePlayingShape() else shape
        val activePop = rememberActivationPopScale(effectiveActive)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            // Only the ACTIVE card pops (rising-edge), so add the scale layer just for it - a graphicsLayer
            // on every thumbnail across a long scrolling grid is wasted compositing otherwise.
            .then(if (effectiveActive) Modifier.graphicsLayer { scaleX = activePop; scaleY = activePop } else Modifier)
            .clip(effectiveShape)
    ) {
        if (albumIndex == null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(effectiveShape)
            )
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(effectiveShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null
                )
            }
        }

        // The on-cover overlay neutral - the SAME color the now-playing equalizer, the play icon and the
        // over-cover titles use - so the loading spinner is never a different color from the playing
        // animation (a neutral, never the theme accent).
        val overlayColor = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White
        PlayingIndicatorBox(
            isActive = effectiveActive,
            playWhenReady = isPlaying,
            color = overlayColor,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (albumIndex != null)
                        Color.Transparent
                    else
                        Color.Black.copy(alpha = ActiveBoxAlpha),
                    shape = effectiveShape
                )
        )

        if (isPreparing) {
            // Resolving/buffering the tapped item: the shared PreparingOverlay uses the SAME neutral as the
            // equalizer (never the accent) and dims EXACTLY like it - transparent for an album track
            // (albumIndex) so a dark onBackground spinner is never lost on a black scrim.
            PreparingOverlay(
                shape = effectiveShape,
                color = overlayColor,
                scrimColor = if (albumIndex != null) Color.Transparent else Color.Black.copy(alpha = ActiveBoxAlpha),
            )
        }
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), shape)
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
    cacheKey: String? = null
) {
    when (thumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            placeHolder()
        }
        1 -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnails[0])
                .apply { /* Removed cache key extensions due to unresolved in env */ }
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.queue_music),
            error = painterResource(R.drawable.queue_music),
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).fastForEachIndexed { index, alignment ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnails.getOrNull(index))
                        .apply { /* Removed cache key extensions due to unresolved in env */ }
                        .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.queue_music),
                    error = painterResource(R.drawable.queue_music),
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2)
                )
            }
        }
    }
}

/**
 * Whether the player is currently PREPARING [id] to play - the user tapped it and audio has not started
 * yet (stream resolve + buffer), so a card shows a loading spinner instead of a hanging play affordance.
 * Reads PlayerConnection.preparingMediaId; false when [id] is null or there is no player connection.
 */
@Composable
fun rememberIsPreparing(id: String?): Boolean {
    val connection = LocalPlayerConnection.current ?: return false
    val preparing by connection.preparingMediaId.collectAsState()
    return id != null && preparing == id
}

@Composable
fun BoxScope.OverlayPlayButton(
    visible: Boolean,
    // While the tapped item resolves/buffers, the disc shows the M3 Expressive loading indicator instead
    // of the play icon (surfaces that use ItemThumbnail get the spinner from it and leave this false).
    loading: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
        ) {
            if (loading) {
                // The neutral white of the play icon it replaces - never the theme accent.
                MediaLoadingSpinner()
            } else {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.OverlayEditButton(
    visible: Boolean,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(alignment)
            .then(if (alignment == Alignment.BottomEnd) Modifier.padding(8.dp) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .padding(0.dp)
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
    // Resets the internal loading state when the item at this slot changes. The Latest Releases carousel
    // is index-keyed (no per-item key), so without this a spinning disc's loading=true would bleed onto a
    // different release that later occupies the same slot; pass the release/album id there.
    itemKey: Any? = null,
) {
    // An album resolves + fetches its tracks before any audio, so the static play disc used to just hang.
    // Show the shared M3 Expressive over-media spinner from tap until the album becomes active (the caller
    // flips `visible` off), then it fades into the now-playing indicator. A timeout reverts to the play
    // icon so a failed / aborted play never spins forever.
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (!visible) loading = false }
    LaunchedEffect(itemKey) { loading = false }
    LaunchedEffect(loading) {
        if (loading) {
            // A successful play flips `visible` off within a few seconds (the queue's first track becomes
            // current); this backstop only matters when the play never activates - e.g. the album fetch
            // 404s and navigates to the page instead - so keep it short rather than a long hang.
            delay(12_000L)
            loading = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                // Always clickable (never enabled=false) so the tap is CONSUMED and cannot fall through to
                // the card behind it (which would navigate to the album page mid-fetch); a re-tap while
                // loading is simply ignored.
                .clickable {
                    if (!loading) {
                        loading = true
                        onClick()
                    }
                }
        ) {
            if (loading) {
                // The neutral white of the play icon it replaces - never the theme accent.
                MediaLoadingSpinner()
            } else {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    // Match OverlayPlayButton's icon so a song's and an album's play button read identically
                    // (same 36dp disc, same tint, same icon) wherever they appear together.
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    val threshold = 300f

    val dragState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceIn(-threshold, threshold)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = dragState,
                onDragStopped = {
                    when {
                        offset.floatValue >= threshold -> {
                            player?.playNext(listOf(mediaItem))
                            ctx.toast(R.string.play_next)
                            reset(offset, scope)
                        }

                        offset.floatValue <= -threshold -> {
                            player?.addToQueue(listOf(mediaItem))
                            ctx.toast(R.string.add_to_queue)
                            reset(offset, scope)
                        }

                        else -> reset(offset, scope)
                    }
                }
            )
    ) {
        if (offset.floatValue != 0f) {
            val (iconRes, bg, tint, align) = if (offset.floatValue > 0)
                Quadruple(
                    R.drawable.playlist_play,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                    Alignment.CenterStart
                ) else
                Quadruple(
                    R.drawable.queue_music,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    Alignment.CenterEnd
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .background(bg),
                contentAlignment = align
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(30.dp)
                        .alpha(0.9f),
                    tint = tint
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}

// Helper to animate reset of swipe offset
private fun reset(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        ) { value, _ -> offset.value = value }
    }
}

// Data holder for swipe visuals
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private object Icon {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    // Download badge lives in DownloadStatusUi.kt (DownloadStatusIcon / SongDownloadBadge) — the one
    // place download/progress state is rendered, so it can't drift between surfaces.

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    /** Marks a row that is a video being surfaced as a "video song" (played as audio). */
    @Composable
    fun Video() {
        Icon(
            painter = painterResource(R.drawable.ondemand_video),
            contentDescription = stringResource(R.string.video),
            modifier = Modifier
                .size(15.dp)
                .padding(end = 2.dp)
        )
    }
}
