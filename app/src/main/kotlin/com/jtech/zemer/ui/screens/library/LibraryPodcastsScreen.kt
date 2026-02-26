package com.jtech.zemer.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.viewmodels.LibraryPodcastsViewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryPodcastsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val subscribedPodcasts by viewModel.subscribedPodcasts.collectAsState()
    val savedEpisodes by viewModel.savedEpisodes.collectAsState()
    val newEpisodes by viewModel.newEpisodes.collectAsState()
    val isLoadingNewEpisodes by viewModel.isLoadingNewEpisodes.collectAsState()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filterContent = @Composable {
        Row {
            Spacer(Modifier.width(12.dp))
            FilterChip(
                label = { Text(stringResource(R.string.filter_podcasts)) },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = onDeselect,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                },
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "filter") {
                filterContent()
            }

            // Empty state - only show if everything is empty
            if (subscribedPodcasts.isEmpty() && savedEpisodes.isEmpty() && newEpisodes.isEmpty()) {
                item(key = "empty_placeholder") {
                    EmptyPlaceholder(
                        icon = R.drawable.podcast,
                        text = stringResource(R.string.library_podcast_empty),
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }

            // Subscribed Channels Section
            if (subscribedPodcasts.isNotEmpty()) {
                item(key = "subscribed_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.subscribed_channels),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.syncPodcastSubscriptions() }) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.action_sync),
                            )
                        }
                    }
                }

                item(key = "subscribed_carousel") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = subscribedPodcasts,
                            key = { it.id }
                        ) { podcast ->
                            PodcastCarouselItem(
                                podcast = podcast,
                                onClick = {
                                    navController.navigate("online_podcast/${podcast.id}")
                                }
                            )
                        }
                    }
                }

                item(key = "spacer_after_subscribed") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // New Episodes Section (from official API)
            if (newEpisodes.isNotEmpty() || isLoadingNewEpisodes) {
                item(key = "new_episodes_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.new_episodes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.fetchNewEpisodes() }) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.action_sync),
                            )
                        }
                    }
                }

                item(key = "new_episodes_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = newEpisodes,
                            key = { it.id }
                        ) { episode ->
                            NewEpisodeItem(
                                episode = episode,
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = episode.title,
                                            items = listOf(episode.toMediaItem()),
                                        ),
                                    )
                                }
                            )
                        }
                    }
                }

                item(key = "spacer_after_new_episodes") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Episodes for Later Section
            if (savedEpisodes.isNotEmpty()) {
                item(key = "episodes_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.episodes_for_later),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_episode,
                                savedEpisodes.size,
                                savedEpisodes.size
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        IconButton(onClick = { viewModel.syncEpisodesForLater() }) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.action_sync),
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = savedEpisodes,
                    key = { _, item -> "episode_${item.id}" }
                ) { index, song ->
                    val isActive = mediaMetadata?.id == song.id
                    val isSaved = song.song.inLibrary != null

                    EpisodeSongListItem(
                        song = song,
                        isActive = isActive,
                        isPlaying = isPlaying && isActive,
                        isSaved = isSaved,
                        onClick = {
                            if (isActive) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = song.song.title,
                                        items = savedEpisodes.map { it.toMediaItem() },
                                        startIndex = index
                                    )
                                )
                            }
                        },
                        onLongClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onBookmarkClick = {
                            // Server-first: call API, then update local on success
                            coroutineScope.launch(Dispatchers.IO) {
                                if (YouTube.isAnonLogin) {
                                    // Anonymous: just update local
                                    val updatedSong = song.song.copy(
                                        inLibrary = if (isSaved) null else LocalDateTime.now()
                                    )
                                    database.query { update(updatedSong) }
                                } else if (isSaved) {
                                    // Remove from saved episodes
                                    val setVideoId = database.getSetVideoId(song.id)?.setVideoId
                                    if (setVideoId != null) {
                                        YouTube.removeEpisodeFromSavedEpisodes(song.id, setVideoId)
                                            .onSuccess {
                                                database.query { update(song.song.copy(inLibrary = null)) }
                                            }
                                            .onFailure { e ->
                                                timber.log.Timber.e(e, "[EPISODE_REMOVE] Failed to remove episode: ${song.id}")
                                            }
                                    }
                                } else {
                                    // Add to saved episodes
                                    YouTube.addEpisodeToSavedEpisodes(song.id)
                                        .onSuccess {
                                            database.query { update(song.song.copy(inLibrary = LocalDateTime.now())) }
                                        }
                                        .onFailure { e ->
                                            timber.log.Timber.e(e, "[EPISODE_SAVE] Failed to save episode: ${song.id}")
                                        }
                                }
                            }
                        },
                        onMenuClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastCarouselItem(
    podcast: PodcastEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = podcast.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NewEpisodeItem(
    episode: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = episode.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = episode.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = episode.artists.joinToString { it.name },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeSongListItem(
    song: com.jtech.zemer.db.entities.Song,
    isActive: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = song.song.thumbnailUrl,
            contentDescription = null,
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    text = song.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isPlaying) {
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Bookmark button for episodes
        IconButton(onClick = onBookmarkClick) {
            Icon(
                painter = painterResource(
                    if (isSaved) R.drawable.bookmark_filled else R.drawable.bookmark
                ),
                contentDescription = null,
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null,
            )
        }
    }
}
