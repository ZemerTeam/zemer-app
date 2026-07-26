package com.jtech.zemer.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.search.SearchProvider
import com.jtech.zemer.search.onlineAlbumRoute
import com.jtech.zemer.search.onlinePlaylistRoute
import com.jtech.zemer.ui.component.AlbumListItem
import com.jtech.zemer.ui.component.ArtistListItem
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.menu.AlbumMenu
import com.jtech.zemer.ui.menu.ArtistMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.HomeSeeAllRow
import com.jtech.zemer.viewmodels.HomeSeeAllStore

/**
 * The generic "See all" page for a Home row: the row's full, already-filtered list as a vertical page
 * (a grid for the online Featured rows, a list for the local Quick Picks / Keep Listening / Forgotten
 * Favorites). It renders straight from [HomeSeeAllStore] — the snapshot Home published on its last load
 * — so what you see here is exactly what the row was built from, uncapped, and can never disagree with
 * it. Home publishes each local row's snapshot as soon as the row is shown (and the featured rows once
 * the network load completes), so the data is present before its "See all" arrow is reachable; an empty
 * snapshot (e.g. process-death restore straight onto this screen, before Home has loaded) shows a neutral
 * placeholder rather than a bare blank page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSeeAllScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    row: HomeSeeAllRow,
) {
    val data by HomeSeeAllStore.data.collectAsState()

    val rowIsEmpty = when (row) {
        HomeSeeAllRow.FEATURED_ALBUMS -> data.featuredAlbums.isEmpty()
        HomeSeeAllRow.FEATURED_ARTISTS -> data.featuredArtists.isEmpty()
        HomeSeeAllRow.FEATURED_VIDEOS -> data.featuredVideos.isEmpty()
        HomeSeeAllRow.FEATURED_PLAYLISTS -> data.featuredPlaylists.isEmpty()
        HomeSeeAllRow.QUICK_PICKS -> data.quickPicks.isEmpty()
        HomeSeeAllRow.FORGOTTEN_FAVORITES -> data.forgottenFavorites.isEmpty()
        HomeSeeAllRow.KEEP_LISTENING -> data.keepListening.isEmpty()
    }

    if (rowIsEmpty) {
        EmptyPlaceholder(
            icon = R.drawable.queue_music,
            text = stringResource(R.string.home_see_all_empty),
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        )
    } else {
        when (row) {
            HomeSeeAllRow.FEATURED_ALBUMS ->
                YtItemGrid(data.featuredAlbums, navController, zemerAlbums = data.featuredAlbumsAreZemer)
            HomeSeeAllRow.FEATURED_ARTISTS ->
                YtItemGrid(data.featuredArtists, navController)
            HomeSeeAllRow.FEATURED_VIDEOS ->
                YtItemGrid(data.featuredVideos, navController)
            HomeSeeAllRow.FEATURED_PLAYLISTS ->
                YtItemGrid(data.featuredPlaylists, navController, zemerPlaylists = data.featuredPlaylistsAreZemer)
            HomeSeeAllRow.QUICK_PICKS -> SongList(data.quickPicks, navController)
            HomeSeeAllRow.FORGOTTEN_FAVORITES -> SongList(data.forgottenFavorites, navController)
            HomeSeeAllRow.KEEP_LISTENING -> LocalItemList(data.keepListening, navController)
        }
    }

    TopAppBar(
        title = { Text(stringResource(row.titleRes)) },
        navigationIcon = {
            // The app's IconButton (long-press = back to Home), matching the other See-all screens.
            com.jtech.zemer.ui.component.IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** Online Featured rows (albums / artists / videos) as a grid, click + long-press mirroring Home. */
@Composable
private fun <T : YTItem> YtItemGrid(
    items: List<T>,
    navController: NavController,
    zemerAlbums: Boolean = false,
    zemerPlaylists: Boolean = false,
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        // Two across, not three: album/artist titles here run long (full Hebrew + English names), and a
        // third-of-screen cell chops them mid-word. Two columns give the title + "artist · year" room.
        columns = GridCells.Fixed(2),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(items = items, key = { it.id }) { item ->
            YouTubeGridItem(
                item = item,
                isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                isPlaying = isPlaying,
                coroutineScope = scope,
                thumbnailRatio = 1f,
                fillMaxWidth = true,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        when (item) {
                            // The only SongItems in this grid are the Featured Videos row — open the video
                            // player (matching the Home row), not audio playback.
                            is SongItem -> navController.navigate(
                                videoRoute(item.id, item.title, item.artists.joinToString(" • ") { it.name }),
                            )
                            // Featured albums are Zemer-sourced: open via the server route (bot-gate-proof).
                            is AlbumItem ->
                                if (zemerAlbums) navController.navigate(SearchProvider.ZEMER.onlineAlbumRoute(item))
                                else navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            // Community playlists are Zemer-sourced: open via the server /playlist route and
                            // tag plays `community:<id>` (the discovery-sourced community row).
                            is PlaylistItem ->
                                if (zemerPlaylists) navController.navigate(SearchProvider.ZEMER.onlinePlaylistRoute(item.id, community = true))
                                else navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                // Videos row: isVideo = true so the menu offers "Download video" (video),
                                // not the audio "Download".
                                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss, isVideo = true)
                                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                            }
                        }
                    },
                ),
            )
        }
    }
}

/** Local Song rows (Quick Picks, Forgotten Favorites) as a list, click plays / toggles, menu on hold. */
@Composable
private fun SongList(songs: List<Song>, navController: NavController) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        items(items = songs, key = { it.id }) { originalSong ->
            val song by database.song(originalSong.id).collectAsState(initial = originalSong)
            val shown = song ?: originalSong
            SongListItem(
                song = shown,
                showInLibraryIcon = true,
                isActive = shown.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(onClick = {
                        menuState.show { SongMenu(originalSong = shown, navController = navController, onDismiss = menuState::dismiss) }
                    }) {
                        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                    }
                },
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (shown.id == mediaMetadata?.id) playerConnection.playPause()
                        else playerConnection.playQueue(YouTubeQueue.radio(shown.toMediaMetadata(), database))
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show { SongMenu(originalSong = shown, navController = navController, onDismiss = menuState::dismiss) }
                    },
                ),
            )
        }
    }
}

/** Keep Listening: a mixed local list (songs, albums, artists), each routed to its own destination. */
@Composable
private fun LocalItemList(items: List<LocalItem>, navController: NavController) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        items(items = items, key = { it.id }) { item ->
            when (item) {
                is Song -> SongListItem(
                    song = item,
                    isActive = item.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(onClick = {
                            menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) }
                        }) { Icon(painterResource(R.drawable.more_vert), contentDescription = null) }
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (item.id == mediaMetadata?.id) playerConnection.playPause()
                            else playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata(), database))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) }
                        },
                    ),
                )
                is Album -> AlbumListItem(
                    album = item,
                    isActive = item.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.combinedClickable(
                        onClick = { navController.navigate("album/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) }
                        },
                    ),
                )
                is Artist -> ArtistListItem(
                    artist = item,
                    modifier = Modifier.combinedClickable(
                        onClick = { navController.navigate("artist/${item.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show { ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss) }
                        },
                    ),
                )
                is Playlist -> Unit // Home's Keep Listening never contains playlists.
            }
        }
    }
}
