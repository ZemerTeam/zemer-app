package com.jtech.zemer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalDownloadUtil
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.HideExplicitKey
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.Album
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.playback.ExoDownloadService
import com.jtech.zemer.playback.queues.LocalAlbumRadio
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.shimmer.ButtonPlaceholder
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.ui.menu.AlbumMenu
import com.jtech.zemer.ui.menu.SelectionSongMenu
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val wrappedSongs = remember(albumWithSongs, hideExplicit) {
        val filteredSongs = if (hideExplicit) {
            albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
        } else {
            albumWithSongs?.songs ?: emptyList()
        }
        filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
    }
    var selection by remember {
        mutableStateOf(false)
    }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    // Focus state for TopAppBar buttons
    val isBackButtonFocused = remember { mutableStateOf(false) }
    val isSelectAllButtonFocused = remember { mutableStateOf(false) }
    val isMoreButtonFocused = remember { mutableStateOf(false) }

    // Focus state for header buttons
    val isHeartButtonFocused = remember { mutableStateOf(false) }
    val isDownloadButtonFocused = remember { mutableStateOf(false) }
    val isHeaderMenuButtonFocused = remember { mutableStateOf(false) }
    val isArtistLinkFocused = remember { mutableStateOf(false) }

    // Focus state for track items
    val trackFocusStates = remember { mutableMapOf<String, Boolean>() }

    // Focus requesters to skip player
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstHeaderItemFocusRequester = remember { FocusRequester() }

    val backButtonBorderColor = animateColorAsState(
        targetValue = if (isBackButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "back_button_focus_border"
    )
    val selectAllButtonBorderColor = animateColorAsState(
        targetValue = if (isSelectAllButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "select_all_button_focus_border"
    )
    val moreButtonBorderColor = animateColorAsState(
        targetValue = if (isMoreButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "more_button_focus_border"
    )
    val heartButtonBorderColor = animateColorAsState(
        targetValue = if (isHeartButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "heart_button_focus_border"
    )
    val downloadButtonBorderColor = animateColorAsState(
        targetValue = if (isDownloadButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "download_button_focus_border"
    )
    val headerMenuButtonBorderColor = animateColorAsState(
        targetValue = if (isHeaderMenuButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "header_menu_button_focus_border"
    )
    val artistLinkBorderColor = animateColorAsState(
        targetValue = if (isArtistLinkFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "artist_link_focus_border"
    )

    LaunchedEffect(Unit) {
        firstHeaderItemFocusRequester.requestFocus()
    }

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED ||
                                downloads[it]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        val albumWithSongs = albumWithSongs
        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty()) {
            item(key = "album_header") {
                Column(
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = albumWithSongs.album.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .size(AlbumThumbnailSize)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                        )

                        Spacer(Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AutoResizeText(
                                text = albumWithSongs.album.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSizeRange = FontSizeRange(16.sp, 22.sp),
                            )

                            val artistLinkFocused = remember { mutableStateOf(false) }
                            val artistLinkBorderColor = animateColorAsState(
                                targetValue = if (artistLinkFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                label = "artist_link_focus_border"
                            )
                            Box(
                                modifier = Modifier
                                    .focusRequester(firstHeaderItemFocusRequester)
                                    .border(3.dp, artistLinkBorderColor.value, RoundedCornerShape(8.dp))
                                    .focusable()
                                    .onFocusChanged { artistLinkFocused.value = it.isFocused }
                                    .padding(4.dp)
                            ) {
                                Text(buildAnnotatedString {
                                    withStyle(
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onBackground
                                        ).toSpanStyle()
                                    ) {
                                        albumWithSongs.artists.fastForEachIndexed { index, artist ->
                                            val link = LinkAnnotation.Clickable(artist.id) {
                                                navController.navigate("artist/${artist.id}")
                                            }
                                            withLink(link) {
                                                append(artist.name)
                                            }
                                            if (index != albumWithSongs.artists.lastIndex) {
                                                append(", ")
                                            }
                                        }
                                    }
                                })
                            }

                            if (albumWithSongs.album.year != null) {
                                Text(
                                    text = albumWithSongs.album.year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal,
                                )
                            }

                            Row {
                                val heartButtonFocused = remember { mutableStateOf(false) }
                                val heartButtonBorderColor = animateColorAsState(
                                    targetValue = if (heartButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = "heart_button_focus_border"
                                )
                                Box(
                                    modifier = Modifier
                                        .border(3.dp, heartButtonBorderColor.value, RoundedCornerShape(8.dp))
                                        .focusable()
                                        .onFocusChanged { heartButtonFocused.value = it.isFocused }
                                ) {
                                    IconButton(
                                        onClick = {
                                            database.query {
                                                update(albumWithSongs.album.toggleLike())
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter =
                                            painterResource(
                                                if (albumWithSongs.album.bookmarkedAt !=
                                                    null
                                                ) {
                                                    R.drawable.favorite
                                                } else {
                                                    R.drawable.favorite_border
                                                },
                                            ),
                                            contentDescription = null,
                                            tint =
                                            if (albumWithSongs.album.bookmarkedAt !=
                                                null
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                LocalContentColor.current
                                            },
                                        )
                                    }
                                }

                                val downloadButtonFocused = remember { mutableStateOf(false) }
                                val downloadButtonBorderColor = animateColorAsState(
                                    targetValue = if (downloadButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = "download_button_focus_border"
                                )

                                when (downloadState) {
                                    Download.STATE_COMPLETED -> {
                                        Box(
                                            modifier = Modifier
                                                .border(3.dp, downloadButtonBorderColor.value, RoundedCornerShape(8.dp))
                                                .focusable()
                                                .onFocusChanged { downloadButtonFocused.value = it.isFocused }
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    albumWithSongs.songs.forEach { song ->
                                                        DownloadService.sendRemoveDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            song.id,
                                                            false,
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }

                                    Download.STATE_DOWNLOADING -> {
                                        Box(
                                            modifier = Modifier
                                                .border(3.dp, downloadButtonBorderColor.value, RoundedCornerShape(8.dp))
                                                .focusable()
                                                .onFocusChanged { downloadButtonFocused.value = it.isFocused }
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    albumWithSongs.songs.forEach { song ->
                                                        DownloadService.sendRemoveDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            song.id,
                                                            false,
                                                        )
                                                    }
                                                },
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .border(3.dp, downloadButtonBorderColor.value, RoundedCornerShape(8.dp))
                                                .focusable()
                                                .onFocusChanged { downloadButtonFocused.value = it.isFocused }
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    albumWithSongs.songs.forEach { song ->
                                                        downloadUtil.downloadToMediaStore(song)
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                }

                                val headerMenuButtonFocused = remember { mutableStateOf(false) }
                                val headerMenuButtonBorderColor = animateColorAsState(
                                    targetValue = if (headerMenuButtonFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = "header_menu_button_focus_border"
                                )
                                Box(
                                    modifier = Modifier
                                        .border(3.dp, headerMenuButtonBorderColor.value, RoundedCornerShape(8.dp))
                                        .focusable()
                                        .onFocusChanged { headerMenuButtonFocused.value = it.isFocused }
                                ) {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = Album(
                                                        albumWithSongs.album,
                                                        albumWithSongs.artists
                                                    ),
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(
                                    LocalAlbumRadio(albumWithSongs, database = database),
                                )
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(
                                text = stringResource(R.string.play),
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(
                                    LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled()), database = database),
                                )
                            },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.shuffle))
                        }
                    }
                }
            }

            if (!wrappedSongs.isNullOrEmpty()) {
                itemsIndexed(
                    items = wrappedSongs,
                    key = { _, song -> song.item.id },
                ) { index, songWrapper ->
                    val trackId = songWrapper.item.id
                    val isTrackFocused = trackFocusStates[trackId] ?: false
                    val trackBorderColor = animateColorAsState(
                        targetValue = if (isTrackFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "track_${trackId}_focus_border"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .border(3.dp, trackBorderColor.value, RoundedCornerShape(8.dp))
                            .focusable()
                            .onFocusChanged { trackFocusStates[trackId] = it.isFocused }
                    ) {
                        SongListItem(
                            song = songWrapper.item,
                            albumIndex = index + 1,
                            isActive = songWrapper.item.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,

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
                            isSelected = songWrapper.isSelected && selection,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (songWrapper.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.service.getAutomix(playlistId)
                                                playerConnection.playQueue(
                                                    LocalAlbumRadio(albumWithSongs, startIndex = index, database = database),
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
                                        wrappedSongs.forEach {
                                            it.isSelected = false
                                        } // Clear previous selections
                                        songWrapper.isSelected = true // Select the current item
                                    },
                                ),
                        )
                    }
                }
            }
        } else {
            item(key = "loading_shimmer") {
                ShimmerHost(
                    modifier = Modifier.animateItem()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                modifier =
                                Modifier
                                    .size(AlbumThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                    .background(MaterialTheme.colorScheme.onSurface),
                            )

                            Spacer(Modifier.width(16.dp))

                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                TextPlaceholder()
                                TextPlaceholder()
                                TextPlaceholder()
                            }
                        }

                        Spacer(Modifier.padding(8.dp))

                        Row {
                            ButtonPlaceholder(Modifier.weight(1f))

                            Spacer(Modifier.width(12.dp))

                            ButtonPlaceholder(Modifier.weight(1f))
                        }
                    }

                    repeat(6) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }
    }

    TopAppBar(
        title = {
            if (selection) {
                val count = wrappedSongs?.count { it.isSelected } ?: 0
                Text(
                    text = pluralStringResource(R.plurals.n_song, count, count),
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                Text(
                    text = albumWithSongs?.album?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .focusRequester(backButtonFocusRequester)
                    .border(3.dp, backButtonBorderColor.value, RoundedCornerShape(8.dp))
                    .focusable()
                    .onFocusChanged { isBackButtonFocused.value = it.isFocused }
                    .focusProperties { down = firstHeaderItemFocusRequester }
            ) {
                IconButton(
                    onClick = {
                        if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!selection) {
                            navController.backToMain()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (selection) R.drawable.close else R.drawable.arrow_back
                        ),
                        contentDescription = null
                    )
                }
            }
        },
        actions = {
            if (selection) {
                val count = wrappedSongs?.count { it.isSelected } ?: 0
                Box(
                    modifier = Modifier
                        .border(3.dp, selectAllButtonBorderColor.value, RoundedCornerShape(8.dp))
                        .focusable()
                        .onFocusChanged { isSelectAllButtonFocused.value = it.isFocused }
                ) {
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs?.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs?.forEach { it.isSelected = true }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (count == wrappedSongs?.size) R.drawable.deselect else R.drawable.select_all
                            ),
                            contentDescription = null
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .border(3.dp, moreButtonBorderColor.value, RoundedCornerShape(8.dp))
                        .focusable()
                        .onFocusChanged { isMoreButtonFocused.value = it.isFocused }
                ) {
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedSongs?.filter { it.isSelected }!!
                                        .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false }
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    )
}
