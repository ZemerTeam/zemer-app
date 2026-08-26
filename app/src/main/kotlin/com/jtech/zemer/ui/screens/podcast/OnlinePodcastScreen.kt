package com.jtech.zemer.ui.screens.podcast

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.ui.screens.shouldPrefetchNearEnd
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Scale
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.AlbumThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.screens.playlist.PlaylistPlayShuffleButtons
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.DraggableScrollbar
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.EpisodeListItem
import com.jtech.zemer.ui.component.ErrorRetryState
import com.jtech.zemer.ui.component.shimmer.ButtonPlaceholder
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.OnlinePodcastViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePodcastScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePodcastViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // KidZone navigation context: drill-outs (View channel) keep the restriction (#drill-in rule).
    val kidZone = navController.currentBackStackEntry?.arguments?.getBoolean("kidZone") == true
    val podcast by viewModel.podcast.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val resumePositions by viewModel.resumePositions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val libraryPodcast by viewModel.libraryPodcast.collectAsState()
    val inLibrary = libraryPodcast?.inLibrary == true

    val lazyListState = rememberLazyListState()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    // Near-edge prefetch: append the next episode page while ~a screenful of rows remains, so the
    // server's paging cursor is actually consumed and episodes past the first page become reachable.
    // Keyed on the paging cursor (nextOffset) so the flow RESTARTS after each landed page — a page that
    // adds only DUPLICATE ids still advances the cursor, so paging can't stall there (keying on
    // episodes.size would). Stops once the cursor is null; loadMoreEpisodes() is idempotent (in-flight
    // guard + null-cursor no-op), so the one extra call after the final page is harmless.
    val nextOffset by viewModel.nextOffset.collectAsState()
    LaunchedEffect(lazyListState, nextOffset) {
        if (nextOffset == null) return@LaunchedEffect
        snapshotFlow {
            shouldPrefetchNearEnd(
                lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                totalItemsCount = lazyListState.layoutInfo.totalItemsCount,
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMoreEpisodes() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                .asPaddingValues(),
        ) {
            if (isLoading) {
                item(key = "loading_shimmer") {
                    ShimmerHost {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .background(MaterialTheme.colorScheme.onSurface),
                                )

                                Spacer(Modifier.width(16.dp))

                                Column(
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // title, author, episode-count — the three text lines the header renders.
                                    TextPlaceholder()
                                    TextPlaceholder()
                                    TextPlaceholder()

                                    // The Add-to-library pill the header always shows below those lines.
                                    Spacer(Modifier.height(8.dp))
                                    ButtonPlaceholder(Modifier.width(140.dp))
                                }
                            }

                            // Matches the real header's Spacer(12) before the Play/Shuffle row.
                            Spacer(Modifier.height(12.dp))

                            // PlaylistPlayShuffleButtons — two equal-width buttons.
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
            } else if (podcast != null) {
                item(key = "podcast_header") {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .animateItem(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(AlbumThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(podcast!!.thumbnail)
                                        .scale(Scale.FILL)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = null,
                                    placeholder = painterResource(R.drawable.podcast),
                                    error = painterResource(R.drawable.podcast),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AutoResizeText(
                                    text = podcast!!.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                )

                                podcast!!.author?.let { author ->
                                    Text(
                                        text = author.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }

                                podcast!!.episodeCountText?.let { episodeCountText ->
                                    Text(
                                        text = episodeCountText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }

                                // Categories chips
                                if (podcast!!.categories.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(
                                            items = podcast!!.categories,
                                            key = { it }
                                        ) { category ->
                                            AssistChip(
                                                onClick = { },
                                                label = {
                                                    Text(
                                                        text = category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                ),
                                                border = null,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { viewModel.toggleSubscription() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (inLibrary)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else
                                            Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (inLibrary) R.drawable.favorite else R.drawable.favorite_border
                                        ),
                                        contentDescription = null,
                                        tint = if (inLibrary)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            LocalContentColor.current,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = stringResource(
                                            if (inLibrary) R.string.remove_from_library else R.string.add_to_library
                                        )
                                    )
                                }

                                // View channel: the host's Zemer channel page (/podcast-channel — its
                                // Podcasts shelf + a channel Subscribe), when the podcast carries a channel id.
                                val channelId = podcast?.channelId ?: podcast?.author?.id
                                if (!channelId.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { navController.navigateToArtist(channelId, isPodcastChannel = true, kidZone = kidZone) },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.person),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(text = stringResource(R.string.view_channel))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (episodes.isNotEmpty()) {
                            PlaylistPlayShuffleButtons(
                                onPlay = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = podcast!!.title,
                                            items = episodes.map { it.toMediaItem() },
                                            playSource = PlaySource.podcast(podcast!!.id),
                                        )
                                    )
                                },
                                onShuffle = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = podcast!!.title,
                                            items = episodes.map { it.toMediaItem() }.shuffled(),
                                            playSource = PlaySource.podcast(podcast!!.id),
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                if (episodes.isEmpty()) {
                    item(key = "empty_episodes") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.no_episodes),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = episodes,
                    key = { _, episode -> episode.id }
                ) { index, episode ->
                    EpisodeListItem(
                        episode = episode,
                        isActive = mediaMetadata?.id == episode.id,
                        isPlaying = isPlaying,
                        resumePositionMs = resumePositions[episode.id],
                        trailingContent = {
                            MoreVertMenuButton(
                                onClick = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = episode.asSongItem(),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .focusBorder()
                            .combinedClickable(
                                onClick = {
                                    if (episode.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = podcast!!.title,
                                                items = episodes.map { it.toMediaItem() },
                                                startIndex = index,
                                                playSource = PlaySource.podcast(podcast!!.id),
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = episode.asSongItem(),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            )
                            .animateItem(),
                    )
                }
            } else if (error != null) {
                item(key = "error_state") {
                    ErrorRetryState(
                        onRetry = { viewModel.retry() },
                        detail = error,
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                        .asPaddingValues()
                )
                .align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 1
        )

        TopAppBar(
            title = {
                if (showTopBarTitle) {
                    AppBarTitle(podcast?.title.orEmpty())
                }
            },
            navigationIcon = {
                BackNavigationIcon(navController)
            },
            scrollBehavior = scrollBehavior,
            colors = zemerTopAppBarColors(),
        )
    }
}
