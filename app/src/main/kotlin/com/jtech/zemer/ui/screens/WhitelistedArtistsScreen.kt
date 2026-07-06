package com.jtech.zemer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.ArtistViewTypeKey
import com.jtech.zemer.constants.CONTENT_TYPE_ARTIST
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.constants.RecognizeMusicFabKey
import com.jtech.zemer.constants.YtmSyncKey
import com.jtech.zemer.ui.component.ALPHABET_OTHER_BUCKET
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LetterFastScrollbar
import com.jtech.zemer.ui.component.MIN_ITEMS_FOR_FAST_SCROLL
import com.jtech.zemer.ui.component.alphabetBucketOf
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.screens.LoadingScreen
import com.jtech.zemer.ui.component.WhitelistedArtistGridItem
import com.jtech.zemer.ui.component.WhitelistedArtistListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.WhitelistedArtistsViewModel

// The always-present lazy items ("search", "header") that precede the artists in BOTH the list
// and the grid — the fast scroller scrolls to artistIndex + this, so a header item added to one
// container must be added to the other and counted here.
private const val ARTIST_HEADER_ITEM_COUNT = 2

// Geometry of the bottom-end button stack, shared between the back-to-top button below and the
// fast scroller's clearance so the two can never drift apart.
private val BackToTopButtonSize = 36.dp
private val BackToTopBottomPadding = 16.dp
// Clears the Recognize-music FAB (56dp) + gap when it occupies this corner.
private val BackToTopBottomPaddingAboveFab = 80.dp
private val FastScrollBottomGap = 16.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhitelistedArtistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: WhitelistedArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    LocalHapticFeedback.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val (_) = rememberPreference(YtmSyncKey, true)
    // The global "Recognize music" FAB occupies the bottom-end corner on this main-tab screen, so
    // lift the back-to-top button above it (a full FAB + gap) when that FAB is enabled.
    val (recognizeMusicFab) = rememberPreference(RecognizeMusicFabKey, defaultValue = true)
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstArtistFocus = remember { FocusRequester() }

    // Sync moved to App.kt - now happens once at app startup instead of every tab visit

    val artists by viewModel.allArtists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showSyncOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(syncProgress.total, syncProgress.isComplete, syncProgress.current, isSyncing) {
        showSyncOverlay = isSyncing || (syncProgress.total > 0 && !syncProgress.isComplete)
        if (!isSyncing && (syncProgress.isComplete || syncProgress.total == 0)) {
            showSyncOverlay = false
        }
    }

    // Single source for what the lazy containers actually render: both view types and the fast
    // scroller must agree on items and positions, so the de-duplication happens once, here.
    val displayedArtists = remember(artists) { artists.distinctBy { it.artist.name } }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    // Approximate scroll position of the active container as a 0..1 fraction, driving the fast
    // scroller's thumb. Item-index based, which is exact enough for a uniform artists list/grid.
    val fastScrollProgress by remember(viewType, displayedArtists.size) {
        derivedStateOf {
            val (firstIndex, visibleCount) = when (viewType) {
                LibraryViewType.LIST ->
                    lazyListState.firstVisibleItemIndex to lazyListState.layoutInfo.visibleItemsInfo.size
                LibraryViewType.GRID ->
                    lazyGridState.firstVisibleItemIndex to lazyGridState.layoutInfo.visibleItemsInfo.size
            }
            val contentFirst = (firstIndex - ARTIST_HEADER_ITEM_COUNT).coerceAtLeast(0)
            val maxFirst = (displayedArtists.size - (visibleCount - ARTIST_HEADER_ITEM_COUNT))
                .coerceAtLeast(1)
            (contentFirst.toFloat() / maxFirst).coerceIn(0f, 1f)
        }
    }

    // The one "scroll whichever container the view type shows" dispatch — the scroll-to-top
    // signal, the back-to-top button and the letter index must all move the same container.
    val scrollActiveListTo: suspend (index: Int, animate: Boolean) -> Unit = { index, animate ->
        when (viewType) {
            LibraryViewType.LIST -> with(lazyListState) {
                if (animate) animateScrollToItem(index) else scrollToItem(index)
            }
            LibraryViewType.GRID -> with(lazyGridState) {
                if (animate) animateScrollToItem(index) else scrollToItem(index)
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    // Show back to top button when scrolled past first few items
    val showBackToTop by remember {
        derivedStateOf {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.firstVisibleItemIndex > 2
                LibraryViewType.GRID -> lazyGridState.firstVisibleItemIndex > 5
            }
        }
    }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            scrollActiveListTo(0, true)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val searchContent = @Composable {
        val downTarget = if (artists.isNotEmpty()) firstArtistFocus else firstFocus
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(searchFocus)
                .focusProperties {
                    down = downTarget
                }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                        downTarget.requestFocus()
                        false
                    } else {
                        false
                    }
                },
            placeholder = { Text(stringResource(R.string.search_artists)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.artists),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_artist,
                    artists.size,
                    artists.size
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier
                    .padding(start = 6.dp)
                    .focusRequester(firstFocus)
                    .focusProperties {
                        up = searchFocus
                        down = if (artists.isNotEmpty()) firstArtistFocus else FocusRequester.Default
                    },
            ) {
                Icon(
                    painter =
                    painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        },
                    ),
                    contentDescription = null,
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "search",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (artists.isEmpty()) {
                        item(key = "empty_placeholder") {
                            EmptyPlaceholder(
                                icon = R.drawable.artist,
                                text = if (searchQuery.isEmpty()) {
                                    stringResource(R.string.library_artist_empty)
                                } else {
                                    stringResource(R.string.no_results_found)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    itemsIndexed(
                        items = displayedArtists,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                    ) { index, artist ->
                        WhitelistedArtistListItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstArtistFocus) else Modifier)
                                .animateItem(),
                            artist = artist,
                            onRequestThumb = { viewModel.requestThumb(artist.id) }
                        )
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "search",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        searchContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (artists.isEmpty()) {
                        item(
                            key = "empty_placeholder",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            EmptyPlaceholder(
                                icon = R.drawable.artist,
                                text = if (searchQuery.isEmpty()) {
                                    stringResource(R.string.library_artist_empty)
                                } else {
                                    stringResource(R.string.no_results_found)
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    itemsIndexed(
                        items = displayedArtists,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                    ) { index, artist ->
                        WhitelistedArtistGridItem(
                            navController = navController,
                            menuState = menuState,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstArtistFocus) else Modifier)
                                .animateItem(),
                            artist = artist,
                            onRequestThumb = { viewModel.requestThumb(artist.id) }
                        )
                    }
                }
        }

        // Fast scroller: only when the list is long enough for jumping to beat swiping.
        if (displayedArtists.size >= MIN_ITEMS_FOR_FAST_SCROLL) {
            LetterFastScrollbar(
                itemCount = displayedArtists.size,
                scrollProgress = fastScrollProgress,
                listScrollInProgress = when (viewType) {
                    LibraryViewType.LIST -> lazyListState.isScrollInProgress
                    LibraryViewType.GRID -> lazyGridState.isScrollInProgress
                },
                letterFor = { index ->
                    displayedArtists.getOrNull(index)?.artist?.name?.let(::alphabetBucketOf)
                        ?: ALPHABET_OTHER_BUCKET
                },
                onScrollToItem = { index ->
                    coroutineScope.launch {
                        scrollActiveListTo(index + ARTIST_HEADER_ITEM_COUNT, false)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Vertical)
                    )
                    // Stay clear of the bottom-end button stack (back-to-top button + its gap).
                    .padding(
                        top = 8.dp,
                        bottom = (if (recognizeMusicFab) BackToTopBottomPaddingAboveFab else BackToTopBottomPadding) +
                            BackToTopButtonSize + FastScrollBottomGap,
                    ),
            )
        }

        // Back to top button - inconspicuous but clear
        AnimatedVisibility(
            visible = showBackToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(
                    end = 16.dp,
                    top = 16.dp,
                    bottom = if (recognizeMusicFab) BackToTopBottomPaddingAboveFab else BackToTopBottomPadding,
                )
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        // Reset TopAppBar height offset to prevent visual glitch
                        scrollBehavior.state.heightOffset = 0f
                        scrollActiveListTo(0, false)
                    }
                },
                modifier = Modifier.size(BackToTopButtonSize),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward),
                    contentDescription = stringResource(R.string.back_to_top),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showSyncOverlay && !syncProgress.isComplete) {
            LoadingScreen(
                onFinished = { showSyncOverlay = false },
                shouldStartSync = false
            )
        }
    }
}
