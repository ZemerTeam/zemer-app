package com.jtech.zemer.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.ui.utils.whitelistedPodcastRoute
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListThumbnailSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.ui.menu.AlbumMenu
import com.jtech.zemer.ui.menu.ArtistMenu
import com.jtech.zemer.ui.menu.PlaylistMenu
import com.jtech.zemer.ui.menu.PodcastChannelMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.utils.ARTIST_AVATAR_PX
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.utils.navigateToAlbum
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope

/**
 * The ONE request builder for whitelisted-artist avatars — list and grid both use it so they share a
 * cache entry and can never drift (different URLs would double-download every avatar). Requests a
 * [ARTIST_AVATAR_PX] server-side crop of the ~2880px source (~10x smaller) and decodes at that size.
 */
@Composable
private fun artistAvatarRequest(thumbnailUrl: String?): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(thumbnailUrl?.resize(ARTIST_AVATAR_PX, ARTIST_AVATAR_PX))
        .scale(Scale.FILL) // fill the target bounds to avoid narrow slices
        .size(ARTIST_AVATAR_PX)
        .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
        .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
        .build()

@Composable
fun LibraryArtistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier
) = ArtistListItem(
    artist = artist,
    trailingContent = {
        MoreVertMenuButton(
            onClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
    },
    modifier = modifier
        .fillMaxWidth()

        .clickable {
            navController.navigateToArtist(artist.id)
        }
)

@Composable
fun WhitelistedArtistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    onRequestThumb: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = ListItem(
    title = artist.artist.name,
    titleMarquee = true,
    subtitle = "", // No song count for whitelisted artists
    badges = {}, // No badges for whitelisted artists
    thumbnailContent = {
        AsyncImage(
            model = artistAvatarRequest(artist.artist.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            placeholder = painterResource(R.drawable.artist),
            error = painterResource(R.drawable.artist),
            // Fallback for a null URL (coil resolves null data to Error) or one that rotated/404s:
            // re-resolve on-device via the shared bounded resolver.
            onError = { onRequestThumb() },
        )
    },
    trailingContent = {
        MoreVertMenuButton(
            onClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
    },
    modifier = modifier
        .fillMaxWidth()
        .focusBorder()
        .clickable {
            navController.navigateToArtist(artist.id)
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier
) = ArtistGridItem(
    artist = artist,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()

        .combinedClickable(
            onClick = {
                navController.navigateToArtist(artist.id)
            },
            onLongClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhitelistedArtistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    onRequestThumb: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = GridItem(
    title = artist.artist.name,
    titleMarquee = true,
    centerContent = true,
    subtitle = "", // No song count for whitelisted artists
    badges = {}, // No badges for whitelisted artists
    thumbnailContent = {
        AsyncImage(
            model = artistAvatarRequest(artist.artist.thumbnailUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.artist),
            error = painterResource(R.drawable.artist),
            // Fallback for a null URL (coil resolves null data to Error) or one that rotated/404s:
            // re-resolve on-device via the shared bounded resolver.
            onError = { onRequestThumb() },
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .focusBorder()
        .combinedClickable(
            onClick = {
                navController.navigateToArtist(artist.id)
            },
            onLongClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryAlbumListItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) = AlbumListItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    trailingContent = {
        MoreVertMenuButton(
            onClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            navController.navigateToAlbum(album.id)
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumGridItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) = AlbumGridItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    coroutineScope = coroutineScope,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                navController.navigateToAlbum(album.id)
            },
            onLongClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryPlaylistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier
) = PlaylistListItem(
    playlist = playlist,
    trailingContent = {
        MoreVertMenuButton(
            onClick = {
                menuState.show {
                    if (playlist.playlist.isEditable || playlist.songCount != 0) {
                        PlaylistMenu(
                            playlist = playlist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    } else {
                        playlist.playlist.browseId?.let { browseId ->
                            YouTubePlaylistMenu(
                                playlist = PlaylistItem(
                                    id = browseId,
                                    title = playlist.playlist.name,
                                    author = null,
                                    songCountText = null,
                                    thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                    playEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.playEndpointParams
                                    ),
                                    shuffleEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.shuffleEndpointParams
                                    ),
                                    radioEndpoint = WatchEndpoint(
                                        playlistId = "RDAMPL$browseId",
                                        params = playlist.playlist.radioEndpointParams
                                    ),
                                    isEditable = false
                                ),
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            }
        )
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0)
                navController.navigate("online_playlist/${playlist.playlist.browseId}")
            else
                navController.navigate("local_playlist/${playlist.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier
) = PlaylistGridItem(
    playlist = playlist,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0)
                    navController.navigate("online_playlist/${playlist.playlist.browseId}")
                else
                    navController.navigate("local_playlist/${playlist.id}")
            },
            onLongClick = {
                menuState.show {
                    if (playlist.playlist.isEditable || playlist.songCount != 0) {
                        PlaylistMenu(
                            playlist = playlist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    } else {
                        playlist.playlist.browseId?.let { browseId ->
                            YouTubePlaylistMenu(
                                playlist = PlaylistItem(
                                    id = browseId,
                                    title = playlist.playlist.name,
                                    author = null,
                                    songCountText = null,
                                    thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                    playEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.playEndpointParams
                                    ),
                                    shuffleEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.shuffleEndpointParams
                                    ),
                                    radioEndpoint = WatchEndpoint(
                                        playlistId = "RDAMPL$browseId",
                                        params = playlist.playlist.radioEndpointParams
                                    ),
                                    isEditable = false
                                ),
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            }
        )
)

@Composable
fun WhitelistedPodcastListItem(
    navController: NavController,
    menuState: MenuState,
    podcast: PodcastWhitelistEntity,
    onRequestThumb: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = ListItem(
    title = podcast.name,
    titleMarquee = true,
    subtitle = podcastShowCountSubtitle(podcast),
    badges = { PodcastVerifiedBadge(podcast) },
    thumbnailContent = {
        if (podcast.thumbnailUrl.isNullOrBlank()) {
            LaunchedEffect(podcast.channelId) { onRequestThumb() }
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(podcast.thumbnailUrl)
                .scale(Scale.FILL)
                .size(ListThumbnailSize.value.toInt())
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
        )
    },
    trailingContent = {
        MoreVertMenuButton(
            onClick = {
                menuState.show {
                    PodcastChannelMenu(
                        podcast = podcast,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        )
    },
    modifier = modifier
        .fillMaxWidth()
        .focusBorder()
        .clickable {
            navController.openWhitelistedPodcast(podcast)
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhitelistedPodcastGridItem(
    navController: NavController,
    menuState: MenuState,
    podcast: PodcastWhitelistEntity,
    onRequestThumb: () -> Unit = {},
    fillMaxWidth: Boolean = true,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) = GridItem(
    title = podcast.name,
    titleMarquee = true,
    centerContent = true,
    subtitle = podcastShowCountSubtitle(podcast),
    badges = { PodcastVerifiedBadge(podcast) },
    thumbnailContent = {
        if (podcast.thumbnailUrl.isNullOrBlank()) {
            LaunchedEffect(podcast.channelId) { onRequestThumb() }
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(podcast.thumbnailUrl)
                .scale(Scale.FILL)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .focusBorder()
        .combinedClickable(
            onClick = {
                navController.openWhitelistedPodcast(podcast)
            },
            onLongClick = {
                menuState.show {
                    PodcastChannelMenu(
                        podcast = podcast,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        )
)

/**
 * Open a browsed whitelisted podcast CHANNEL: the whitelist is channel-level, so every entry opens the
 * host channel page (ArtistScreen, isPodcastChannel) where Subscribe + the channel's shows live. Shared
 * by the list + grid rows so the destination can't drift.
 */
private fun NavController.openWhitelistedPodcast(podcast: PodcastWhitelistEntity) {
    whitelistedPodcastRoute(null, podcast.channelId)?.let(::navigate)
}

/**
 * The browse tile subtitle for a whitelisted podcast channel: its mirror-synced show count
 * ("3 shows"), hidden when the mirror hasn't counted any. Shared by the list + grid rows.
 */
@Composable
private fun podcastShowCountSubtitle(podcast: PodcastWhitelistEntity): String =
    if (podcast.showCount > 0) {
        pluralStringResource(R.plurals.n_show, podcast.showCount, podcast.showCount)
    } else {
        ""
    }

/** The accent check badge for a mirror-verified podcast channel. Shared by the list + grid rows. */
@Composable
private fun PodcastVerifiedBadge(podcast: PodcastWhitelistEntity) {
    if (podcast.isVerified) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .padding(end = 2.dp),
        )
    }
}
