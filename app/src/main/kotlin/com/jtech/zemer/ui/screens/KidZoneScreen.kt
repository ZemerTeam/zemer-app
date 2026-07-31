package com.jtech.zemer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.ArtistViewTypeKey
import com.jtech.zemer.constants.CONTENT_TYPE_ARTIST
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.ui.component.ArtistCountHeader
import com.jtech.zemer.ui.component.ArtistSearchField
import com.jtech.zemer.ui.component.EmptyPlaceholder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.screens.LoadingScreen
import com.jtech.zemer.ui.component.WhitelistedArtistGridItem
import com.jtech.zemer.ui.component.WhitelistedArtistListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.KidZoneViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KidZoneScreen(
    navController: NavController,
    viewModel: KidZoneViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    LocalHapticFeedback.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstArtistFocus = remember { FocusRequester() }

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

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val searchContent = @Composable {
        ArtistSearchField(
            query = searchQuery,
            onQueryChange = { viewModel.searchQuery.value = it },
            searchFocus = searchFocus,
            downTarget = if (artists.isNotEmpty()) firstArtistFocus else firstFocus,
        )
    }

    val headerContent = @Composable {
        ArtistCountHeader(
            titleRes = R.string.kid_zone,
            artistCount = artists.size,
            viewType = viewType,
            onToggleViewType = { viewType = viewType.toggle() },
            firstFocus = firstFocus,
            searchFocus = searchFocus,
            downTarget = if (artists.isNotEmpty()) firstArtistFocus else FocusRequester.Default,
        )
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
                            icon = R.drawable.kid_zone,
                            text = if (searchQuery.isEmpty()) {
                                stringResource(R.string.kid_zone_empty)
                            } else {
                                stringResource(R.string.no_results_found)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                itemsIndexed(
                    items = artists.distinctBy { it.artist.name },
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
                            icon = R.drawable.kid_zone,
                            text = if (searchQuery.isEmpty()) {
                                stringResource(R.string.kid_zone_empty)
                            } else {
                                stringResource(R.string.no_results_found)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                itemsIndexed(
                    items = artists.distinctBy { it.artist.name },
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

        if (showSyncOverlay && !syncProgress.isComplete) {
            LoadingScreen(
                onFinished = { showSyncOverlay = false },
                shouldStartSync = false
            )
        }
    }
}
