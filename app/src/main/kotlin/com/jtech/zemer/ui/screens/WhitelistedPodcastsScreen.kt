package com.jtech.zemer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.constants.PodcastViewTypeKey
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.search.SEARCH_FILTER_EPISODES
import com.jtech.zemer.search.zemerSearchRoute
import com.jtech.zemer.ui.component.BrowseScreenScaffold
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.SearchHandoffPill
import com.jtech.zemer.ui.component.WhitelistedPodcastGridItem
import com.jtech.zemer.ui.component.WhitelistedPodcastListItem
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.utils.navigateToPodcast
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.WhitelistedPodcastsViewModel
import com.metrolist.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistedPodcastsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: WhitelistedPodcastsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    var viewType by rememberEnumPreference(PodcastViewTypeKey, LibraryViewType.GRID)

    val podcasts by viewModel.allPodcasts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val subscribedPodcasts by viewModel.subscribedPodcasts.collectAsState()
    val newEpisodes by viewModel.newEpisodes.collectAsState()
    val isLoadingNewEpisodes by viewModel.isLoadingNewEpisodes.collectAsState()

    val hasHeaderSections = subscribedPodcasts.isNotEmpty() || newEpisodes.isNotEmpty() || isLoadingNewEpisodes

    BrowseScreenScaffold(
        navController = navController,
        scrollBehavior = scrollBehavior,
        items = podcasts,
        itemKey = { it.channelId },
        itemName = { it.name },
        viewType = viewType,
        onToggleViewType = { viewType = viewType.toggle() },
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.searchQuery.value = it },
        titleRes = R.string.podcasts,
        emptyIconRes = R.drawable.podcast,
        emptyTextRes = R.string.library_podcast_empty,
        syncProgress = viewModel.syncProgress,
        isSyncing = viewModel.isSyncing,
        countPluralRes = R.plurals.n_channel,
        searchPlaceholderRes = R.string.search_podcasts,
        headerSections = if (hasHeaderSections) {
            {
                PodcastLibraryHeaderSections(
                    subscribedPodcasts = subscribedPodcasts,
                    newEpisodes = newEpisodes,
                    isLoadingNewEpisodes = isLoadingNewEpisodes,
                    onSync = { viewModel.syncSubscribedPodcasts() },
                    onChannelClick = { navController.navigateToPodcast(it) },
                    onRefresh = { viewModel.fetchNewEpisodes() },
                    onEpisodeClick = { episode ->
                        playerConnection.playQueue(
                            ListQueue(title = episode.title, items = listOf(episode.toMediaItem())),
                        )
                    },
                )
            }
        } else {
            null
        },
        // This screen's search filters CHANNELS (a local title match over the allow-set); episode
        // search is the global search screen's job. A typed query gets one hand-off row into it,
        // landing on the Episodes chip prefilled - covering the "typed an episode name here" case
        // (esp. zero channel matches) without turning this instant local filter into a networked
        // results screen.
        trailingItem = if (searchQuery.isNotBlank()) {
            {
                SearchHandoffPill(
                    text = stringResource(R.string.search_episodes_for, searchQuery.trim()),
                    onClick = {
                        navController.navigate(
                            zemerSearchRoute(searchQuery.trim(), SEARCH_FILTER_EPISODES)
                        )
                    },
                )
            }
        } else {
            null
        },
        listItemContent = { _, podcast, modifier ->
            WhitelistedPodcastListItem(
                navController = navController,
                menuState = menuState,
                modifier = modifier,
                podcast = podcast,
            )
        },
        gridItemContent = { _, podcast, modifier ->
            WhitelistedPodcastGridItem(
                navController = navController,
                menuState = menuState,
                modifier = modifier,
                podcast = podcast,
            )
        },
    )
}

/**
 * Shared gold section header (title + a trailing sync/refresh icon), used by both podcast rows so the
 * two can't drift.
 */
@Composable
private fun PodcastSectionHeader(title: String, onSync: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSync) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = stringResource(R.string.action_sync),
            )
        }
    }
}

/** A subscribed-podcast avatar card (the "Subscribed Channels" row). D-pad focusable. */
@Composable
private fun SubscribedPodcastCard(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A "New Episodes" card (larger thumbnail + title + author). D-pad focusable. */
@Composable
private fun NewEpisodeCard(
    episode: SongItem,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = episode.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
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

/**
 * The two library header sections (Subscribed Channels — the account's synced + whitelist-filtered
 * subscriptions; New Episodes — the Zemer server `/podcasts/new-episodes` feed), rendered once and shared
 * by the LIST and GRID branches so they can't drift. Each inner section hides itself when empty.
 */
@Composable
private fun PodcastLibraryHeaderSections(
    subscribedPodcasts: List<PodcastEntity>,
    newEpisodes: List<SongItem>,
    isLoadingNewEpisodes: Boolean,
    onSync: () -> Unit,
    onChannelClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onEpisodeClick: (SongItem) -> Unit,
) {
    if (subscribedPodcasts.isNotEmpty()) {
        SubscribedChannelsSection(
            podcasts = subscribedPodcasts,
            onSync = onSync,
            onChannelClick = onChannelClick,
        )
    }
    if (newEpisodes.isNotEmpty() || isLoadingNewEpisodes) {
        NewEpisodesSection(
            episodes = newEpisodes,
            onRefresh = onRefresh,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

/**
 * The "Subscribed Channels" row: gold header + sync + a horizontal strip of avatar cards.
 */
@Composable
private fun SubscribedChannelsSection(
    podcasts: List<PodcastEntity>,
    onSync: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PodcastSectionHeader(title = stringResource(R.string.subscribed_channels), onSync = onSync)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = podcasts, key = { it.id }) { podcast ->
                SubscribedPodcastCard(
                    title = podcast.title,
                    thumbnailUrl = podcast.thumbnailUrl,
                    onClick = { onChannelClick(podcast.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The "New Episodes" row: gold header + refresh + a horizontal strip of episode cards.
 */
@Composable
private fun NewEpisodesSection(
    episodes: List<SongItem>,
    onRefresh: () -> Unit,
    onEpisodeClick: (SongItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PodcastSectionHeader(title = stringResource(R.string.new_episodes), onSync = onRefresh)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = episodes, key = { it.id }) { episode ->
                NewEpisodeCard(episode = episode, onClick = { onEpisodeClick(episode) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
