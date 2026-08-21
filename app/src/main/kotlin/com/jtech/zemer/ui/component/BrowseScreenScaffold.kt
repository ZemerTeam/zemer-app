package com.jtech.zemer.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.CONTENT_TYPE_ARTIST
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.ui.screens.LoadingScreen
import com.jtech.zemer.utils.WhitelistSyncProgress
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// The always-present lazy items ("search", "header") that precede the content in BOTH the list and
// the grid — the fast scroller scrolls to itemIndex + this, so a header item added to one container
// must be added to the other and counted here. [browseHeaderItemCount] adds the optional
// header-sections item (the Podcasts browse's Subscribed Channels / New Episodes block).
internal const val BROWSE_BASE_HEADER_ITEM_COUNT = 2

internal fun browseHeaderItemCount(hasHeaderSections: Boolean): Int =
    BROWSE_BASE_HEADER_ITEM_COUNT + if (hasHeaderSections) 1 else 0

/**
 * Approximate scroll position of the active container as a 0..1 fraction, driving the fast
 * scroller's thumb. Item-index based, which is exact enough for a uniform browse list/grid.
 * Pure so the clamping rules (header offset, short lists, over-scroll) are JVM-tested.
 */
internal fun browseFastScrollProgress(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    headerItemCount: Int,
    itemCount: Int,
): Float {
    val contentFirst = (firstVisibleIndex - headerItemCount).coerceAtLeast(0)
    val maxFirst = (itemCount - (visibleItemCount - headerItemCount)).coerceAtLeast(1)
    return (contentFirst.toFloat() / maxFirst).coerceIn(0f, 1f)
}

/** When the back-to-top button shows: past the first few items (more in the 3-column grid). */
internal fun browseShowBackToTop(viewType: LibraryViewType, firstVisibleIndex: Int): Boolean =
    when (viewType) {
        LibraryViewType.LIST -> firstVisibleIndex > 2
        LibraryViewType.GRID -> firstVisibleIndex > 5
    }

// Geometry of the bottom-end button stack, shared with the fast scroller's clearance so the two
// can never drift apart.
private val BackToTopButtonSize = 36.dp
private val BackToTopBottomPadding = 16.dp
private val FastScrollBottomGap = 16.dp

/**
 * The ONE whitelist-browse scaffold, shared by the Artists / Kid Zone / Podcasts browse screens:
 * search pill + count header (+ optional header sections) + the LIST/GRID item run, empty
 * placeholder, back-to-top button, letter fast scroller (auto-hidden under
 * [MIN_ITEMS_FOR_FAST_SCROLL]), the scroll-to-top nav signal, D-pad focus wiring, and the
 * whitelist-sync overlay — one implementation so the three screens (and the LIST vs GRID branches
 * inside each) cannot drift apart.
 *
 * The caller supplies only its data + the row/tile composables; [listItemContent]/[gridItemContent]
 * receive the modifier carrying the first-item focus anchor and item animation and must apply it.
 * [headerSections] (when non-null) renders between the search pill and the count header;
 * [trailingItem] (when non-null) renders after the item run (the Podcasts search hand-off pill).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> BrowseScreenScaffold(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    items: List<T>,
    itemKey: (T) -> Any,
    itemName: (T) -> String,
    viewType: LibraryViewType,
    onToggleViewType: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    titleRes: Int,
    emptyIconRes: Int,
    emptyTextRes: Int,
    syncProgress: StateFlow<WhitelistSyncProgress>,
    isSyncing: StateFlow<Boolean>,
    listItemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
    gridItemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
    countPluralRes: Int = R.plurals.n_artist,
    searchPlaceholderRes: Int = R.string.search_artists,
    headerSections: (@Composable () -> Unit)? = null,
    trailingItem: (@Composable () -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val syncProgressValue by syncProgress.collectAsState()
    val isSyncingValue by isSyncing.collectAsState()
    // Overlay while a sync is running or a started progress hasn't completed; LoadingScreen's
    // onFinished only hides its own composition, so the show state derives live from the flows.
    val showSyncOverlay =
        (isSyncingValue || (syncProgressValue.total > 0 && !syncProgressValue.isComplete)) &&
            !syncProgressValue.isComplete

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val headerItemCount = browseHeaderItemCount(headerSections != null)

    val fastScrollProgress by remember(viewType, items.size, headerItemCount) {
        derivedStateOf {
            val (firstIndex, visibleCount) = when (viewType) {
                LibraryViewType.LIST ->
                    lazyListState.firstVisibleItemIndex to lazyListState.layoutInfo.visibleItemsInfo.size
                LibraryViewType.GRID ->
                    lazyGridState.firstVisibleItemIndex to lazyGridState.layoutInfo.visibleItemsInfo.size
            }
            browseFastScrollProgress(firstIndex, visibleCount, headerItemCount, items.size)
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

    val showBackToTop by remember(viewType) {
        derivedStateOf {
            val firstIndex = when (viewType) {
                LibraryViewType.LIST -> lazyListState.firstVisibleItemIndex
                LibraryViewType.GRID -> lazyGridState.firstVisibleItemIndex
            }
            browseShowBackToTop(viewType, firstIndex)
        }
    }

    RequestInitialDpadFocus(firstFocus)

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            scrollActiveListTo(0, true)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val searchContent = @Composable {
        ArtistSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            searchFocus = searchFocus,
            downTarget = if (items.isNotEmpty()) firstItemFocus else firstFocus,
            placeholderRes = searchPlaceholderRes,
        )
    }

    val headerContent = @Composable {
        ArtistCountHeader(
            titleRes = titleRes,
            count = items.size,
            countPluralRes = countPluralRes,
            viewType = viewType,
            onToggleViewType = onToggleViewType,
            firstFocus = firstFocus,
            searchFocus = searchFocus,
            downTarget = if (items.isNotEmpty()) firstItemFocus else FocusRequester.Default,
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
                    item(key = "search", contentType = CONTENT_TYPE_HEADER) {
                        searchContent()
                    }

                    if (headerSections != null) {
                        item(key = "header_sections", contentType = CONTENT_TYPE_HEADER) {
                            headerSections()
                        }
                    }

                    item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                        headerContent()
                    }

                    if (items.isEmpty()) {
                        item(key = "empty_placeholder") {
                            EmptyPlaceholder(
                                icon = emptyIconRes,
                                text = stringResource(
                                    if (searchQuery.isEmpty()) emptyTextRes else R.string.no_results_found
                                ),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    itemsIndexed(
                        items = items,
                        key = { _, item -> itemKey(item) },
                        contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                    ) { index, item ->
                        listItemContent(
                            index,
                            item,
                            Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                .animateItem(),
                        )
                    }

                    if (trailingItem != null) {
                        item(key = "trailing") {
                            Box(Modifier.animateItem()) { trailingItem() }
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                        searchContent()
                    }

                    if (headerSections != null) {
                        item(key = "header_sections", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                            headerSections()
                        }
                    }

                    item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                        headerContent()
                    }

                    if (items.isEmpty()) {
                        item(key = "empty_placeholder", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyPlaceholder(
                                icon = emptyIconRes,
                                text = stringResource(
                                    if (searchQuery.isEmpty()) emptyTextRes else R.string.no_results_found
                                ),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    itemsIndexed(
                        items = items,
                        key = { _, item -> itemKey(item) },
                        contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                    ) { index, item ->
                        gridItemContent(
                            index,
                            item,
                            Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                .animateItem(),
                        )
                    }

                    if (trailingItem != null) {
                        item(key = "trailing", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.animateItem()) { trailingItem() }
                        }
                    }
                }
        }

        // Fast scroller: only when the list is long enough for jumping to beat swiping.
        if (items.size >= MIN_ITEMS_FOR_FAST_SCROLL) {
            LetterFastScrollbar(
                itemCount = items.size,
                scrollProgress = fastScrollProgress,
                listScrollInProgress = when (viewType) {
                    LibraryViewType.LIST -> lazyListState.isScrollInProgress
                    LibraryViewType.GRID -> lazyGridState.isScrollInProgress
                },
                letterFor = { index ->
                    items.getOrNull(index)?.let { alphabetBucketOf(itemName(it)) }
                        ?: ALPHABET_OTHER_BUCKET
                },
                onScrollToItem = { index ->
                    coroutineScope.launch {
                        scrollActiveListTo(index + headerItemCount, false)
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
                        bottom = BackToTopBottomPadding + BackToTopButtonSize + FastScrollBottomGap,
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
                .padding(end = 16.dp, top = 16.dp, bottom = BackToTopBottomPadding),
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
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_upward),
                    contentDescription = stringResource(R.string.back_to_top),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showSyncOverlay) {
            LoadingScreen(
                onFinished = {},
                shouldStartSync = false,
                progressFlow = syncProgress,
            )
        }
    }
}
