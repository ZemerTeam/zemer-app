package com.jtech.zemer.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.CONTENT_TYPE_SONG
import com.jtech.zemer.constants.PodcastFilter
import com.jtech.zemer.constants.PodcastFilterKey
import com.jtech.zemer.constants.PodcastSortDescendingKey
import com.jtech.zemer.constants.PodcastSortTypeKey
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.PodcastEntity
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.shareText
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.extensions.togglePlayPause
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.LibraryFilterChip
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.HideOnScrollFAB
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.component.gentleMarquee
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.SongListItem
import com.jtech.zemer.ui.component.SortHeader
import com.jtech.zemer.ui.menu.SongMenu
import com.jtech.zemer.ui.utils.navigateToArtist
import com.jtech.zemer.ui.utils.navigateToPodcast
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.LibraryPodcastsViewModel
import com.jtech.zemer.viewmodels.PodcastChannel
import kotlinx.coroutines.launch

/**
 * Library -> Podcasts. Ported to full Metrolist parity: three sub-filter tabs (Episodes / Channels /
 * Downloaded). New Episodes is served whitelist-pure by the Zemer server (/podcasts/new-episodes); the
 * account-backed pieces (Episodes-for-Later, host channels) sync from the YouTube account for a
 * PERSONAL login only - the pooled anonymous account's library must never leak across users. The local
 * pieces (subscribed shows, downloaded episodes, saved episodes) work for every session.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryPodcastsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val downloadedEpisodesStr = stringResource(R.string.downloaded_episodes)


    var podcastFilter by rememberEnumPreference(PodcastFilterKey, PodcastFilter.EPISODES)
    val (sortType, onSortTypeChange) = rememberEnumPreference(PodcastSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PodcastSortDescendingKey, true)

    val subscribedPodcasts by viewModel.subscribedPodcasts.collectAsState()
    val savedEpisodes by viewModel.savedEpisodes.collectAsState()
    val newEpisodes by viewModel.newEpisodes.collectAsState()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsState()
    val podcastChannels by viewModel.podcastChannels.collectAsState()

    // Refresh the account's channels each time the screen resumes (personal only; a no-op for anon).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshChannels()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val lazyListState = rememberLazyListState()

    val chipsHeader = @Composable {
        Row {
            Spacer(Modifier.width(12.dp))
            LibraryFilterChip(
                label = stringResource(R.string.filter_podcasts),
                onDeselect = onDeselect,
            )
            ChipsRow(
                chips = listOf(
                    PodcastFilter.EPISODES to stringResource(R.string.filter_episodes),
                    PodcastFilter.CHANNELS to stringResource(R.string.filter_channels),
                    PodcastFilter.DOWNLOADED to stringResource(R.string.filter_downloaded),
                ),
                currentValue = podcastFilter,
                onValueUpdate = { podcastFilter = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (podcastFilter) {
            // ── EPISODES tab ──────────────────────────────────────────────
            PodcastFilter.EPISODES -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "filter", contentType = CONTENT_TYPE_HEADER) { chipsHeader() }

                    val everythingEmpty = subscribedPodcasts.isEmpty() && savedEpisodes.isEmpty() &&
                        newEpisodes.isEmpty()
                    if (everythingEmpty) {
                        item(key = "empty") {
                            EmptyPlaceholder(
                                icon = R.drawable.podcast,
                                text = stringResource(R.string.library_podcast_empty),
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    // New Episodes card: the whitelist-pure server /podcasts/new-episodes feed (the same
                    // feed the count reflects, anon-capable). Tapping PLAYS that feed — never opens the raw
                    // InnerTube RDPN playlist, which is a different, unfiltered content set (a kosher leak)
                    // that also never matched the shown count.
                    if (newEpisodes.isNotEmpty()) {
                        item(key = "new_episodes", contentType = CONTENT_TYPE_HEADER) {
                            val newEpisodesTitle = stringResource(R.string.new_episodes)
                            AutoPlaylistCard(
                                title = newEpisodesTitle,
                                thumbnailUrl = null,
                                gradientCover = true,
                                subtitleCount = pluralStringResource(R.plurals.n_episode, newEpisodes.size, newEpisodes.size),
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = newEpisodesTitle,
                                            items = newEpisodes.map { it.toMediaItem() },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    // Episodes for Later: ALWAYS the LOCAL saved episodes (whitelist-filtered on sync,
                    // covering both local saves and YTM-synced ones) — never the raw online YTM "SE"
                    // playlist, which is unfiltered (a kosher leak) AND misses local-only saves (so it
                    // opened empty whenever a save had not reached YouTube Music). Same for anon + personal.
                    if (savedEpisodes.isNotEmpty()) {
                        item(key = "se_header", contentType = CONTENT_TYPE_HEADER) {
                            SectionHeader(stringResource(R.string.episodes_for_later))
                        }
                        itemsIndexed(
                            items = savedEpisodes,
                            key = { _, item -> "episode_${item.id}" },
                            contentType = { _, _ -> CONTENT_TYPE_SONG },
                        ) { index, song ->
                            EpisodeSongListItem(
                                song = song,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying && mediaMetadata?.id == song.id,
                                isSaved = song.song.inLibrary != null,
                                onClick = {
                                    if (mediaMetadata?.id == song.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = song.song.title,
                                                items = savedEpisodes.map { it.toMediaItem() },
                                                startIndex = index,
                                            ),
                                        )
                                    }
                                },
                                onMenuClick = {
                                    menuState.show {
                                        SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                                    }
                                },
                                onBookmarkClick = {
                                    viewModel.toggleEpisodeSaved(song)
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Subscribed shows (local, whitelist-filtered) - always.
                    itemsIndexed(
                        items = subscribedPodcasts,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { _, podcast ->
                        PodcastEpisodePlaylistItem(
                            podcast = podcast,
                            onClick = { navController.navigateToPodcast(podcast.id) },
                            onMenuClick = {
                                menuState.show {
                                    PodcastEpisodePlaylistMenu(
                                        onRemove = { viewModel.unsubscribeShow(podcast) },
                                        onShare = { context.shareText(podcastShareUrl(podcast.id)) },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        )
                    }
                }
            }

            // ── CHANNELS tab ──────────────────────────────────────────────
            PodcastFilter.CHANNELS -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "filter", contentType = CONTENT_TYPE_HEADER) { chipsHeader() }

                    item(key = "channels_count", contentType = CONTENT_TYPE_HEADER) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.n_channel, podcastChannels.size, podcastChannels.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }

                    itemsIndexed(
                        items = podcastChannels,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { _, channel ->
                        PodcastArtistChannelItem(
                            thumbnailUrl = channel.thumbnailUrl,
                            channelName = channel.name,
                            onClick = { navController.navigateToArtist(channel.id, isPodcastChannel = true) },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    if (podcastChannels.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_subscribed_channels),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── DOWNLOADED tab ────────────────────────────────────────────
            PodcastFilter.DOWNLOADED -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "filter", contentType = CONTENT_TYPE_HEADER) { chipsHeader() }

                    item(key = "sort_header", contentType = CONTENT_TYPE_HEADER) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { st ->
                                    when (st) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = pluralStringResource(R.plurals.n_episode, downloadedEpisodes.size, downloadedEpisodes.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }

                    itemsIndexed(
                        items = downloadedEpisodes,
                        key = { index, item -> "${item.song.id}_$index" },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, episode ->
                        SongListItem(
                            song = episode,
                            showInLibraryIcon = false,
                            showLikedIcon = false,
                            showDownloadIcon = true,
                            isActive = episode.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                MoreVertMenuButton(onClick = {
                                    menuState.show {
                                        SongMenu(originalSong = episode, navController = navController, onDismiss = menuState::dismiss)
                                    }
                                })
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusBorder()
                                .combinedClickable(
                                    onClick = {
                                        if (episode.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = downloadedEpisodesStr,
                                                    items = downloadedEpisodes.map { it.toMediaItem() },
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            SongMenu(originalSong = episode, navController = navController, onDismiss = menuState::dismiss)
                                        }
                                    },
                                )
                                .animateItem(),
                        )
                    }

                    if (downloadedEpisodes.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_downloaded_episodes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HideOnScrollFAB(
                    visible = downloadedEpisodes.isNotEmpty(),
                    lazyListState = lazyListState,
                    icon = R.drawable.shuffle,
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = downloadedEpisodesStr,
                                items = downloadedEpisodes.shuffled().map { it.toMediaItem() },
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** The YouTube Music playlist share URL for a saved podcast show. */
private fun podcastShareUrl(podcastId: String): String =
    "https://music.youtube.com/playlist?list=${podcastId.removePrefix("MPSP")}"

@Composable
private fun SectionHeader(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

/** Auto-playlist card (mirrors YT Music) - used for the New Episodes and Episodes-for-Later playlists. */
/** The 56dp rounded podcast thumbnail with the neutral queue_music fallback — shared by the library rows. */
@Composable
private fun PodcastRowThumbnail(thumbnailUrl: String?) {
    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(ThumbnailCornerRadius)),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun AutoPlaylistCard(
    title: String,
    thumbnailUrl: String?,
    subtitleCount: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // A generated Zemer-brand gradient cover with a podcast glyph (the YouTube-Music "New Episodes"
    // look), instead of the first episode's thumbnail — theme-aware (seeded from the chosen accent).
    gradientCover: Boolean = false,
) {
    // Observed BEFORE focusBorder (whose focusable is the row's focus target) so the title glide
    // re-arms when a D-pad user focuses the row - a bare gentleMarquee would fire once and never again.
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusBorder().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (gradientCover) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.podcast),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        } else {
            PodcastRowThumbnail(thumbnailUrl)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.gentleMarquee(focused = focused))
            Text(
                text = buildString {
                    append(stringResource(R.string.auto_playlist))
                    if (!subtitleCount.isNullOrBlank()) {
                        append(" • ")
                        append(subtitleCount)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A saved podcast show row shown in the Episodes tab. */
@Composable
private fun PodcastEpisodePlaylistItem(
    podcast: PodcastEntity,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same focus wiring as AutoPlaylistCard - re-arm the title glide when the row gains D-pad focus.
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.focusBorder().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PodcastRowThumbnail(podcast.thumbnailUrl)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = podcast.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.gentleMarquee(focused = focused))
            if (!podcast.author.isNullOrBlank()) {
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MoreVertMenuButton(onClick = onMenuClick)
    }
}

/** Menu for a saved podcast show: remove-from-library + share. */
@Composable
private fun PodcastEpisodePlaylistMenu(
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Spacer(Modifier.size(12.dp))
    Material3MenuGroup(
        items = listOf(
            Material3MenuItemData(
                title = { Text(text = stringResource(R.string.remove_from_library)) },
                icon = { Icon(painter = painterResource(R.drawable.delete), contentDescription = null) },
                onClick = {
                    onRemove()
                    onDismiss()
                },
            ),
            Material3MenuItemData(
                title = { Text(text = stringResource(R.string.share)) },
                icon = { Icon(painter = painterResource(R.drawable.share), contentDescription = null) },
                onClick = {
                    onShare()
                    onDismiss()
                },
            ),
        ),
    )
    Spacer(Modifier.size(12.dp))
}

/**
 * A podcast host channel row shown in the Channels tab. Owns its focusBorder + click chain like its
 * sibling rows (AutoPlaylistCard / PodcastEpisodePlaylistItem) so it can observe its own focus and
 * re-arm the title glide on D-pad focus - a caller-side focusBorder would be invisible to it.
 */
@Composable
private fun PodcastArtistChannelItem(
    thumbnailUrl: String?,
    channelName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = channelName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).gentleMarquee(focused = focused),
        )
    }
}

/** A saved (episodes-for-later) episode row with a bookmark toggle - used for anonymous sessions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeSongListItem(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SongListItem(
        song = song,
        showLikedIcon = false,
        showInLibraryIcon = false,
        showDownloadIcon = true,
        isActive = isActive,
        isPlaying = isPlaying,
        trailingContent = {
            androidx.compose.material3.IconButton(onClick = onBookmarkClick) {
                Icon(
                    painter = painterResource(if (isSaved) R.drawable.bookmark_filled else R.drawable.bookmark),
                    contentDescription = null,
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoreVertMenuButton(onClick = onMenuClick)
        },
        modifier = modifier.fillMaxWidth().focusBorder().combinedClickable(onClick = onClick, onLongClick = onMenuClick),
    )
}
