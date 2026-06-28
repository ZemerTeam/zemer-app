@file:Suppress("unused")

package com.jtech.zemer.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AppBarHeight
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.db.entities.ArtistEntity
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.AlbumGridItem
import com.jtech.zemer.ui.component.HideOnScrollFAB
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.ButtonPlaceholder
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.ui.menu.AlbumMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeArtistMenu
import com.jtech.zemer.ui.menu.YouTubePlaylistMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.screens.videoRoute
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.fadingEdge
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.playback.DownloadStateResolver
import kotlinx.coroutines.launch
import com.jtech.zemer.ui.component.AggregateDownloadButton
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.ui.component.SelectionTopActions
import com.jtech.zemer.ui.menu.SelectionMediaMetadataMenu
import com.jtech.zemer.ui.menu.SelectionSongMenu

import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.ArtistViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.valentinilk.shimmer.shimmer

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val unknownArtistTitle = stringResource(R.string.unknown_artist)
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val artistPage = viewModel.artistPage
    val isLoadingArtist = viewModel.isLoading
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val downloadUtil = LocalDownloadUtil.current
    var remoteSelection by remember { mutableStateOf(false) }
    val selectedRemoteSongIds = remember { mutableSetOf<String>() }
    val remoteSongItems = remember { mutableMapOf<String, SongItem>() }
    if (remoteSelection) {
        BackHandler { remoteSelection = false }
    }

    val filteredLibrarySongs = remember(librarySongs, hideExplicit) {
        if (hideExplicit) librarySongs.filter { !it.song.explicit } else librarySongs
    }
    val wrappedSongs = remember(filteredLibrarySongs) {
        filteredLibrarySongs.map { ItemWrapper(it) }.toMutableList()
    }
    var selection by remember { mutableStateOf(false) }

    val backFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val visibleCounts = remember { mutableStateMapOf<String, Int>() }

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    // Calculate the offset value outside of the offset lambda
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset = with(density) {
        -(systemBarsTopPadding + AppBarHeight).roundToPx()
    }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    LaunchedEffect(libraryArtist) {
        // always show local page for local artists. Show local page remote artist when offline
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    LaunchedEffect(artistPage, libraryArtist, showLocal) {
        if (artistPage != null || libraryArtist != null || showLocal) {
            firstFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if ((artistPage == null || isLoadingArtist) && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost (
                        modifier = Modifier
                            .offset {
                                IntOffset(x = 0, y = headerOffset)
                            }
                    ) {
                        // Artist Image Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f),
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.onSurface)
                                    .fadingEdge(
                                        top = systemBarsTopPadding + AppBarHeight,
                                        bottom = 200.dp,
                                    ),
                            )
                        }
                        // Artist Name and Controls Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ButtonPlaceholder(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(40.dp)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(40.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .shimmer()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface,
                                                RoundedCornerShape(24.dp)
                                            )
                                    )
                                }
                            }

                            repeat(6) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
                    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

                    Box {
                        // Artist Image with offset
                        if (thumbnail != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .offset {
                                        IntOffset(x = 0, y = headerOffset)
                                    }
                            ) {
                                AsyncImage(
                                    model = thumbnail.resize(1200, 1200),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .fadingEdge(
                                            bottom = 200.dp,
                                        ),
                                )
                            }
                        }

                        // Artist Name and Controls Section - positioned at bottom of image
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (thumbnail != null) {
                                        // Position content at the bottom part of the image
                                        // Using screen width to calculate aspect ratio height minus overlap
                                        LocalResources.current.displayMetrics.widthPixels.let { screenWidth ->
                                            with(density) {
                                                ((screenWidth / 1.2f) - 144).toDp()
                                            }
                                        }
                                    } else {
                                        16.dp
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                // Artist Name
                                Text(
                                    text = artistName ?: stringResource(R.string.unknown),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Subscribe Button
                                    OutlinedButton(
                                        onClick = {
                                            database.transaction {
                                                val artist = libraryArtist?.artist
                                                if (artist != null) {
                                                    update(artist.toggleLike())
                                                } else {
                                                    artistPage?.artist?.let {
                                                        insert(
                                                            ArtistEntity(
                                                                id = it.id,
                                                                name = it.title,
                                                                channelId = it.channelId,
                                                                thumbnailUrl = it.thumbnail,
                                                            ).toggleLike()
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (libraryArtist?.artist?.bookmarkedAt != null)
                                                MaterialTheme.colorScheme.surface
                                            else
                                                Color.Transparent
                                        ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .focusRequester(firstFocus)
                                    ) {
                                        val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
                                        Text(
                                            text = stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe),
                                            color = if (!isSubscribed) MaterialTheme.colorScheme.error else LocalContentColor.current
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Radio Button
                                        if (!showLocal) {
                                            artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                                OutlinedButton(
                                                    onClick = {
                                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint, preloadItem = null, database))
                                                    },
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.height(40.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.radio),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.radio)
                                                    )
                                                }
                                            }
                                        }

                                        // Shuffle Button
                                        if (!showLocal) {
                                            artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                                IconButton(
                                                    onClick = {
                                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint, preloadItem = null, database))
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(24.dp)
                                                        )
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.shuffle),
                                                        contentDescription = stringResource(R.string.shuffle),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Download All Button (local songs)
                                        if (filteredLibrarySongs.isNotEmpty()) {
                                            AggregateDownloadButton(
                                                songs = filteredLibrarySongs,
                                                onDownloadAll = {
                                                    filteredLibrarySongs.forEach { downloadUtil.downloadToMediaStore(it) }
                                                },
                                                onRemoveAll = {
                                                    coroutineScope.launch {
                                                        filteredLibrarySongs.forEach { downloadUtil.removeDownload(it.id) }
                                                    }
                                                },
                                            )
                                        }

                                        if (showLocal && librarySongs.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    val shuffledSongs = librarySongs.shuffled()
                                                    if (shuffledSongs.isNotEmpty()) {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = libraryArtist?.artist?.name ?: unknownArtistTitle,
                                                                items = shuffledSongs.map { it.toMediaItem() }
                                                            )
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(24.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.shuffle),
                                                    contentDescription = stringResource(R.string.shuffle),
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name ?: ""

                            if (selection) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SelectionTopActions(
                                        wrapped = wrappedSongs,
                                        countLabel = { pluralStringResource(R.plurals.n_song, it, it) },
                                        onExit = { selection = false },
                                        onMore = {
                                            menuState.show {
                                                SelectionSongMenu(
                                                    songSelection = wrappedSongs.filter { it.isSelected }.map { it.item },
                                                    onDismiss = menuState::dismiss,
                                                    clearAction = { selection = false },
                                                )
                                            }
                                        },
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NavigationTitle(
                                        title = stringResource(R.string.songs),
                                        modifier = Modifier.animateItem(),
                                        onClick = {
                                            navController.navigate("search/${java.net.URLEncoder.encode(artistName, "UTF-8")}?filter=songs")
                                        }
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (filteredLibrarySongs.isNotEmpty()) {
                                        AggregateDownloadButton(
                                            songs = filteredLibrarySongs,
                                            onDownloadAll = {
                                                filteredLibrarySongs.forEach { downloadUtil.downloadToMediaStore(it) }
                                            },
                                            onRemoveAll = {
                                                coroutineScope.launch {
                                                    filteredLibrarySongs.forEach { downloadUtil.removeDownload(it.id) }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = wrappedSongs,
                            key = { index, item -> "local_song_${item.item.id}_$index" }
                        ) { index, songWrapper ->
                            SongListItem(
                                song = songWrapper.item,
                                showInLibraryIcon = true,
                                isActive = songWrapper.item.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSelected = songWrapper.isSelected && selection,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songWrapper.item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (!selection) {
                                                if (songWrapper.item.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = libraryArtist?.artist?.name ?: unknownArtistTitle,
                                                            items = filteredLibrarySongs.map { it.toMediaItem() },
                                                            startIndex = index
                                                        )
                                                    )
                                                }
                                            } else {
                                                songWrapper.isSelected = !songWrapper.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            songWrapper.isSelected = true
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name ?: ""
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("search/${java.net.URLEncoder.encode(artistName, "UTF-8")}?filter=albums")
                                }
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums = if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { "local_album_${it.id}_${filteredLibraryAlbums.indexOf(it)}" }
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    navController.navigate("album/${album.id}")
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    artistPage?.sections?.fastForEach { section ->
                        val distinctItems = section.items.distinctBy { it.id }
                        val isVideoSection = section.title.contains("video", ignoreCase = true) ||
                            section.title.contains("short", ignoreCase = true)

                        // Skip video sections entirely if videos are blocked
                        if (isVideoSection && blockVideos) {
                            return@fastForEach
                        }

                        val visibleCount = visibleCounts.getOrPut(section.title) {
                            if (isVideoSection) minOf(8, distinctItems.size) else distinctItems.size
                        }
                        val displayItems = distinctItems.take(visibleCount)

                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                val artistName = artistPage.artist.title
                                NavigationTitle(
                                    title = section.title,
                                    modifier = Modifier.animateItem(),
                                    // "Albums" is deliberately NOT routed to search. YouTube removed the
                                    // album facet from search for independent (non-Official-Artist-Channel)
                                    // artists: the search chip cloud no longer offers an "Albums" filter and
                                    // search?filter=albums returns "No results" for them, even though the
                                    // artist's page still lists every album. So route "Albums" through the
                                    // section's own moreEndpoint (the artist album grid via YouTube.artistItems),
                                    // like every other section, sourcing albums from where they now live.
                                    // ("Songs" search still works, so it stays.) See tests/search/README.md.
                                    onClick = when (section.title) {
                                        "Songs" -> {
                                            {
                                                navController.navigate(
                                                    "search/${java.net.URLEncoder.encode(artistName, "UTF-8")}?filter=songs"
                                                )
                                            }
                                        }
                                        else -> section.moreEndpoint?.let {
                                            {
                                                navController.navigate(
                                                    "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            items(
                                items = displayItems,
                                key = { "youtube_song_${it.id}" },
                            ) { song ->
                                if (song.id !in remoteSongItems) {
                                    remoteSongItems[song.id] = song as SongItem
                                }
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    isSelected = song.id in selectedRemoteSongIds && remoteSelection,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                if (remoteSelection) {
                                                    if (song.id in selectedRemoteSongIds) {
                                                        selectedRemoteSongIds.remove(song.id)
                                                    } else {
                                                        selectedRemoteSongIds.add(song.id)
                                                    }
                                                } else {
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (remoteSelection) {
                                                    if (song.id in selectedRemoteSongIds) {
                                                        selectedRemoteSongIds.remove(song.id)
                                                    } else {
                                                        selectedRemoteSongIds.add(song.id)
                                                    }
                                                } else if (isVideoSection && !blockVideos) {
                                                    val artistDisplay = song.artists.joinToString(" • ") { it.name }
                                                    navController.navigate(videoRoute(song.id, song.title, artistDisplay))
                                                } else if (!isVideoSection) {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                WatchEndpoint(videoId = song.id),
                                                                song.toMediaMetadata(),
                                                                database
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (!remoteSelection) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    remoteSelection = true
                                                    selectedRemoteSongIds.clear()
                                                    selectedRemoteSongIds.add(song.id)
                                                } else {
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                        .animateItem(),
                                )
                            }
                        } else {
                            item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                                ) {
                                    itemsIndexed(
                                        items = displayItems,
                                        key = { index, item -> "youtube_album_${item.id}_$index" },
                                    ) { index, item ->
                                        if (isVideoSection && index >= displayItems.size - 3 && visibleCount < distinctItems.size) {
                                            visibleCounts[section.title] = minOf(visibleCount + 6, distinctItems.size)
                                        }
                                        YouTubeGridItem(
                                            item = item,
                                            isActive = when (item) {
                                                is SongItem -> mediaMetadata?.id == item.id
                                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                is ArtistItem -> false
                                                is PlaylistItem -> false
                                            },
                                            isPlaying = isPlaying,
                                            isSelected = remoteSelection && item.id in selectedRemoteSongIds,
                                            coroutineScope = coroutineScope,
                                            // All grid thumbnails square (1f). Video items (Videos /
                                            // Live performances) carry the title baked into the 16:9
                                            // image, illegible behind the card; a center crop hides it
                                            // and the clean title shows below. Albums/artists/playlists
                                            // are square anyway. See issue #84.
                                            thumbnailRatio = 1f,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        if (remoteSelection) {
                                                            if (item.id in selectedRemoteSongIds) {
                                                                selectedRemoteSongIds.remove(item.id)
                                                            } else {
                                                                selectedRemoteSongIds.add(item.id)
                                                            }
                                                        } else if (isVideoSection && item is SongItem && !blockVideos) {
                                                            val artistDisplay = item.artists.joinToString(" • ") { it.name }
                                                            navController.navigate(videoRoute(item.id, item.title, artistDisplay))
                                                        } else if (!isVideoSection) {
                                                            when (item) {
                                                                is SongItem -> {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue(
                                                                            WatchEndpoint(videoId = item.id),
                                                                            item.toMediaMetadata(),
                                                                            database
                                                                        ),
                                                                    )
                                                                }
                                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!remoteSelection) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            remoteSelection = true
                                                            selectedRemoteSongIds.clear()
                                                            selectedRemoteSongIds.add(item.id)
                                                        } else {
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem ->
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                            isVideo = isVideoSection,
                                                                        )

                                                                    is AlbumItem ->
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss,
                                                                        )

                                                                    is ArtistItem ->
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )

                                                                    is PlaylistItem ->
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                }
                                                            }
                                                        }
                                                    },
                                                )
                                                .animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HideOnScrollFAB(
            visible = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }

    TopAppBar(
        title = {
            if (remoteSelection) {
                Text(
                    text = pluralStringResource(R.plurals.n_song, selectedRemoteSongIds.size, selectedRemoteSongIds.size),
                    style = MaterialTheme.typography.titleLarge
                )
            } else if (!transparentAppBar) {
                Text(
                    text = artistPage?.artist?.title.orEmpty().ifEmpty { stringResource(R.string.artists) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (remoteSelection) {
                        remoteSelection = false
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!remoteSelection) {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painterResource(if (remoteSelection) R.drawable.close else R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .focusProperties { down = firstFocus }
                )
            }
        },
        actions = {
            if (remoteSelection) {
                IconButton(
                    onClick = {
                        if (selectedRemoteSongIds.size == remoteSongItems.size) {
                            selectedRemoteSongIds.clear()
                        } else {
                            remoteSongItems.keys.forEach { selectedRemoteSongIds.add(it) }
                        }
                    },
                ) {
                    Icon(
                        painterResource(
                            if (selectedRemoteSongIds.size == remoteSongItems.size) R.drawable.deselect else R.drawable.select_all
                        ),
                        contentDescription = null
                    )
                }
                IconButton(
                    onClick = {
                        val selectedMedia = selectedRemoteSongIds.mapNotNull { remoteSongItems[it]?.toMediaMetadata() }
                        menuState.show {
                            SelectionMediaMetadataMenu(
                                songSelection = selectedMedia,
                                currentItems = emptyList(),
                                onDismiss = menuState::dismiss,
                                clearAction = { remoteSelection = false }
                            )
                        }
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        viewModel.artistPage?.artist?.shareLink?.let { link ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(context.getString(R.string.clip_label_artist_link), link)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.link),
                        contentDescription = null,
                    )
                }
            }
        },
        colors = if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    )
}
