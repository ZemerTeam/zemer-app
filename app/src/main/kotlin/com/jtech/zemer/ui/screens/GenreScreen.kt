package com.jtech.zemer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import androidx.compose.foundation.lazy.LazyListScope
import com.jtech.zemer.search.ZemerResultMapper.headerCovers
import com.jtech.zemer.search.zemerAlbumRoute
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.tracking.TrackImpressionsByKey
import com.jtech.zemer.tracking.TrackingSurface
import com.jtech.zemer.ui.component.AutoResizeText
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.FontSizeRange
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.MenuState
import com.jtech.zemer.ui.component.MoreVertMenuButton
import com.jtech.zemer.ui.component.GenreWeaveLayer
import com.jtech.zemer.ui.component.genreIcon
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.BoxPlaceholder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.component.shimmer.TextPlaceholder
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.menu.YouTubeSongMenu
import com.jtech.zemer.ui.theme.HeaderFontFamily
import com.jtech.zemer.ui.utils.activeRowTapTogglesPlayPause
import com.jtech.zemer.viewmodels.ZemerGenreViewModel
import com.jtech.zemer.viewmodels.ZemerGenreViewModel.UiState
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Rows-from-end runway that triggers the next tracklist page: rows are [YouTubeListItem]s at
 * ListItemHeight (64.dp), so 10 rows ≈ one phone screenful — the fetch lands before the user
 * reaches the edge on all but the slowest connections.
 */
internal const val TRACKLIST_PREFETCH_ROWS = 10

/**
 * True when the viewport's last visible item is within [prefetchRows] of the list end. Compares
 * against the LazyColumn's TOTAL item count (not songs.size): header/shelf/title items share the
 * index space, and near-end distance only ever spans trailing track rows, so their inflation of the
 * count is harmless. Pure so the threshold rule is plain-JVM testable.
 */
internal fun shouldPrefetchNearEnd(
    lastVisibleIndex: Int?,
    totalItemsCount: Int,
    prefetchRows: Int = TRACKLIST_PREFETCH_ROWS,
): Boolean = lastVisibleIndex != null && lastVisibleIndex >= totalItemsCount - 1 - prefetchRows

/**
 * Detail screen for one genre — opens like an artist page (handoff §3): a count-free header whose
 * Play button starts GENRE RADIO (`/radio?kind=genre` — never the browse tracklist,
 * §4), horizontal Albums / Singles shelves (no artists shelf: an artist card opens a FULL
 * catalog, mostly unrelated to the genre), then the paged songs/videos tracklist (a
 * near-edge prefetch fetches the next `offset` page ~[TRACKLIST_PREFETCH_ROWS] rows before the
 * end, with an end shimmer as the in-flight fallback). Song taps are the
 * standard seed-first song radio; albums/singles open through the server route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerGenreViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val state by viewModel.state.collectAsState()

    // 404 = unknown slug or everything filtered out for this viewer; back out gracefully (the
    // catalog/home row re-fetch on open, so the stale chip disappears on return).
    LaunchedEffect(state) {
        if (state is UiState.NotFound) navController.navigateUp()
    }

    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    val loaded = state as? UiState.Loaded

    val showSongMenu: (SongItem, Boolean) -> Unit = { song, isVideo ->
        menuState.show {
            YouTubeSongMenu(
                song = song,
                navController = navController,
                onDismiss = menuState::dismiss,
                isVideo = isVideo,
            )
        }
    }

    // Impressions: the exposure dampener must see what this page showed. Keyed (the header/shelf
    // items share the index space); only tracklist rows carry a "song_"/"video_" key, so shelf art
    // and headers never mis-report. The two prefixes keep the key namespaces disjoint: the same id
    // may legitimately appear in songs on one page and videos on a later one (the live corpus can
    // reclassify between fetches), and a shared prefix would crash the keyed LazyColumn.
    loaded?.let { page ->
        val impressionIds = remember(page.songs, page.videos) {
            (page.songs + page.videos).map { it.id }.toSet()
        }
        TrackImpressionsByKey(
            surface = TrackingSurface.genre(viewModel.genreId),
            state = lazyListState,
            idOfKey = { key ->
                (key as? String)
                    ?.let {
                        when {
                            it.startsWith("song_") -> it.removePrefix("song_")
                            it.startsWith("video_") -> it.removePrefix("video_")
                            else -> null
                        }
                    }
                    ?.takeIf(impressionIds::contains)
            },
        )
    }

    // Near-edge prefetch: start the next tracklist page while ~a screenful of rows remain, so the
    // end shimmer is the slow-network exception, not every page boundary. Keyed on nextOffset so
    // the flow RESTARTS per landed page — it immediately chains the next page if the viewport is
    // still near the (now longer) end — while a FAILED page (nextOffset unchanged, no restart)
    // stays blocked by distinctUntilChanged until the user scrolls back across the threshold:
    // natural retry backoff, no timers. snapshotFlow collects off-composition, so nothing
    // recomposes per frame; loadMore() itself is idempotent (loadingMore guard + offset echo).
    LaunchedEffect(lazyListState, loaded?.nextOffset) {
        if (loaded?.nextOffset == null) return@LaunchedEffect
        snapshotFlow {
            shouldPrefetchNearEnd(
                lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                totalItemsCount = lazyListState.layoutInfo.totalItemsCount,
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        when (val uiState = state) {
            UiState.Loading, UiState.NotFound -> item(key = "loading_shimmer") {
                GenreHeaderShimmer()
            }

            is UiState.Loaded -> {
                val header = uiState.page.header

                item(key = "genre_header") {
                    // The genre's face, in three layers, all count-free (a concrete number reads
                    // as small — the catalog should read as complete, not counted):
                    // 1. an album-art mosaic of the genre's own top covers — the app's ONLY color
                    //    source by design ("the album arts are already getting the color"), fading
                    //    into the surface under a scrim so the chrome above stays legible;
                    // 2. the same motif weave its catalog card carries (card→page continuity);
                    // 3. one full-width gold pill — the screen's single loud accent — that starts
                    //    genre radio (never the browse tracklist, per the handoff).
                    val covers = remember(uiState.page) { uiState.page.headerCovers() }
                    val motif = painterResource(genreIcon(header.id))
                    val surface = MaterialTheme.colorScheme.surface
                    // Graceful degradation: a cover that fails or is still loading paints this
                    // neutral card tone, not a transparent gap that would read as a broken stripe.
                    val coverFallback = remember(surface) {
                        ColorPainter(surface)
                    }
                    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                    val coverPlaceholder = remember(surfaceContainerHigh) {
                        ColorPainter(surfaceContainerHigh)
                    }
                    // fillMaxWidth on the CONTAINER, not its children: the mosaic sizes to this
                    // box, and without it the box shrinks to the widest child (the compact pill
                    // regression that halved the artwork).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    ) {
                        if (covers.isNotEmpty()) {
                            Row(modifier = Modifier.matchParentSize()) {
                                covers.forEach { url ->
                                    AsyncImage(
                                        // Usually already in coil's cache: the VM preloads these
                                        // URLs the moment the page JSON lands. Crossfade covers
                                        // the cold-cache remainder instead of a hard pop-in.
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(url)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        // A failed/loading column shows a neutral fill, never a
                                        // see-through gap in the all-or-nothing strip.
                                        placeholder = coverPlaceholder,
                                        error = coverFallback,
                                        fallback = coverFallback,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                            // Scrim: covers glow through up top, melt into the surface below so
                            // title and pill sit on solid ground in any theme.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to surface.copy(alpha = 0.35f),
                                            0.55f to surface.copy(alpha = 0.88f),
                                            1f to surface,
                                        ),
                                    ),
                            )
                        }
                        // The SAME drifting weave the catalog card carries — one continuous
                        // fabric from card to page (it animates here too, not statically).
                        GenreWeaveLayer(
                            motif = motif,
                            tint = MaterialTheme.colorScheme.primary,
                            alpha = 0.05f,
                            modifier = Modifier.matchParentSize(),
                        )
                        Column(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            // Expanded stage (owner ask: "expand a lot more that top place, a lot
                            // bigger"): tall art runway, then the title in the app's display face.
                            Spacer(Modifier.height(96.dp))
                            AutoResizeText(
                                text = header.title,
                                fontFamily = HeaderFontFamily,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                // Relative line height: the ambient style's fixed sp line height
                                // made a wrapped 44sp title overlap its own second line.
                                lineHeight = 1.1.em,
                                fontSizeRange = FontSizeRange(30.sp, 44.sp),
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    playerConnection.playQueue(
                                        ZemerRadioQueue.genre(viewModel.genreId, playerConnection.service),
                                    )
                                },
                                shape = CircleShape,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                // Compact by review: the pill is the screen's accent note, not a
                                // banner — content-sized, not edge to edge.
                                modifier = Modifier.height(44.dp),
                            ) {
                                // A plain play arrow (owner review: the genre motif inside the
                                // pill — a diamond on the Chasunah page — read as noise; the
                                // motif's home is the weave and the chips).
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(
                                    text = stringResource(R.string.play),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = HeaderFontFamily,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                genreAlbumShelf(
                    key = "albums",
                    title = { stringResource(R.string.albums) },
                    albums = uiState.page.albums,
                    activeAlbumId = mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    navController = navController,
                    menuState = menuState,
                )
                genreAlbumShelf(
                    key = "singles",
                    title = { stringResource(R.string.singles) },
                    albums = uiState.page.singles,
                    activeAlbumId = mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    navController = navController,
                    menuState = menuState,
                )

                if (uiState.songs.isNotEmpty()) {
                    item(key = "songs_title") {
                        NavigationTitle(title = stringResource(R.string.songs), modifier = Modifier.animateItem())
                    }
                    itemsIndexed(
                        items = uiState.songs,
                        key = { _, song -> "song_${song.id}" },
                    ) { _, song ->
                        GenreTrackRow(
                            song = song,
                            isVideo = false,
                            isActive = mediaMetadata?.id == song.id,
                            isPlaying = isPlaying,
                            genreId = viewModel.genreId,
                            navController = navController,
                            showSongMenu = showSongMenu,
                        )
                    }
                }

                if (uiState.videos.isNotEmpty()) {
                    item(key = "videos_title") {
                        NavigationTitle(title = stringResource(R.string.videos), modifier = Modifier.animateItem())
                    }
                    items(
                        items = uiState.videos,
                        key = { "video_${it.id}" },
                    ) { video ->
                        GenreTrackRow(
                            song = video,
                            isVideo = true,
                            isActive = mediaMetadata?.id == video.id,
                            isPlaying = isPlaying,
                            genreId = viewModel.genreId,
                            navController = navController,
                            showSongMenu = showSongMenu,
                        )
                    }
                }

                // End shimmer, fallback-only: the near-edge prefetch above owns triggering, so this
                // item just shows placeholders when a fetch is genuinely in flight at the true edge
                // (fast fling past the runway, or a slow network). After a failed page it renders
                // nothing — scrolling back across the prefetch threshold is the retry.
                if (uiState.nextOffset != null) {
                    item(key = "load_more") {
                        if (uiState.loadingMore) {
                            ShimmerHost { repeat(3) { ListItemPlaceHolder() } }
                        }
                    }
                }
            }

            UiState.Error -> item(key = "error_state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::load) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }

    TopAppBar(
        title = {
            if (showTopBarTitle) {
                Text((state as? UiState.Loaded)?.page?.header?.title.orEmpty())
            }
        },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
    )
}

/** One tracklist row: seed-first song radio on tap (video rows open the video player instead). */
@Composable
private fun GenreTrackRow(
    song: SongItem,
    isVideo: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    genreId: String,
    navController: NavController,
    showSongMenu: (SongItem, Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    YouTubeListItem(
        item = song,
        isActive = isActive,
        isPlaying = isPlaying,
        trailingContent = {
            MoreVertMenuButton(onClick = { showSongMenu(song, isVideo) })
        },
        modifier = Modifier
            .combinedClickable(
                onClick = {
                    if (isVideo) {
                        val artistDisplay = song.artists.joinToString(" • ") { it.name }
                        navController.navigate(videoRoute(song.id, song.title, artistDisplay))
                    } else if (activeRowTapTogglesPlayPause(isActive, playerConnection.isStationBroadcast.value)) {
                        playerConnection.playPause()
                    } else {
                        playerConnection.playQueue(
                            ZemerRadioQueue.song(
                                song.toMediaMetadata(),
                                playerConnection.service,
                                PlaySource.genre(genreId),
                            ),
                        )
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSongMenu(song, isVideo)
                },
            ),
    )
}

/** A horizontal releases shelf; taps open the album screen through the server route. */
private fun LazyListScope.genreAlbumShelf(
    key: String,
    title: @Composable () -> String,
    albums: List<AlbumItem>,
    activeAlbumId: String?,
    isPlaying: Boolean,
    navController: NavController,
    menuState: MenuState,
) {
    if (albums.isEmpty()) return
    item(key = "${key}_title") {
        NavigationTitle(title = title(), modifier = Modifier.animateItem())
    }
    item(key = "${key}_row") {
        val haptic = LocalHapticFeedback.current
        LazyRow(
            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
            modifier = Modifier.animateItem(),
        ) {
            items(items = albums, key = { it.browseId }) { album ->
                YouTubeGridItem(
                    item = album,
                    isActive = activeAlbumId == album.browseId,
                    isPlaying = isPlaying,
                    thumbnailRatio = 1f,
                    modifier = Modifier.combinedClickable(
                        onClick = { navController.navigate(zemerAlbumRoute(album)) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                )
            }
        }
    }
}

/**
 * The genre page's OWN loading skeleton, shaped like what actually loads (a borrowed
 * playlist-shaped shimmer read as a bait-and-switch): the tall header stage with the title bar and
 * the full-width pill where they will land, then a shelf (title + square cards), then track rows.
 */
@Composable
private fun GenreHeaderShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier) {
        Column(Modifier.padding(12.dp)) {
            Spacer(Modifier.height(96.dp))
            // The title line, at the header's real display height.
            BoxPlaceholder(Modifier.height(40.dp).fillMaxWidth(fraction = 0.55f))
            Spacer(Modifier.height(16.dp))
            // The gold pill's slot — compact, matching the real button's footprint.
            BoxPlaceholder(Modifier.height(44.dp).width(132.dp), shape = CircleShape)
        }
        // One shelf: section title, then a row of square cards (the artist/album carousels).
        Column(Modifier.padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(12.dp))
            TextPlaceholder()
            Spacer(Modifier.height(8.dp))
            Row {
                repeat(3) {
                    BoxPlaceholder(
                        Modifier.size(140.dp),
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // The tracklist rows.
        repeat(4) {
            ListItemPlaceHolder()
        }
    }
}
