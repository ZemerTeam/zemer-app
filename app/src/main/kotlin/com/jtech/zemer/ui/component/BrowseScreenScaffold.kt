package com.jtech.zemer.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.CONTENT_TYPE_ARTIST
import com.jtech.zemer.constants.CONTENT_TYPE_HEADER
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.ui.component.shimmer.GridItemPlaceHolder
import com.jtech.zemer.ui.component.shimmer.LoadingListPlaceholder
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
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

/**
 * The LIST view's sticky letter sections: one bucket per RUN of a letter ([alphabetBucketOf]) in
 * display order, as (letter, first item index). Runs, not distinct letters — bucketing strips
 * leading punctuation (quotes, parentheses) while the sort does not, so the same letter can
 * legitimately appear in two non-contiguous runs and each run gets its own header.
 */
internal fun browseLetterBuckets(names: List<String>): List<Pair<Char, Int>> {
    val buckets = mutableListOf<Pair<Char, Int>>()
    names.forEachIndexed { index, name ->
        val bucket = alphabetBucketOf(name)
        if (buckets.isEmpty() || buckets.last().first != bucket) buckets.add(bucket to index)
    }
    return buckets
}

/**
 * Lazy-list index of content item [itemIndex] when the sticky letter headers are present: the fixed
 * header items, plus every letter header at or before the item. [bucketStarts] empty = no letter
 * headers (the grid, short lists).
 */
internal fun browseLazyItemIndex(itemIndex: Int, bucketStarts: List<Int>, headerItemCount: Int): Int =
    headerItemCount + itemIndex + bucketStarts.count { it <= itemIndex }

// Geometry of the bottom-end button stack, shared with the fast scroller's clearance so the two
// can never drift apart.
private val BackToTopButtonSize = 36.dp
private val BackToTopBottomPadding = 16.dp
private val FastScrollBottomGap = 16.dp

/**
 * The ONE whitelist-browse scaffold, shared by the Artists / Kid Zone / Podcasts browse screens:
 * search pill + count header (+ optional header-sections slot) + the LIST/GRID item run, sticky
 * letter section headers (LIST), loading shimmer, empty placeholder, expressive pull-to-refresh,
 * back-to-top button, letter fast scroller (auto-hidden under
 * [MIN_ITEMS_FOR_FAST_SCROLL]), the scroll-to-top nav signal, D-pad focus wiring, and the
 * whitelist-sync overlay — one implementation so the three screens (and the LIST vs GRID branches
 * inside each) cannot drift apart.
 *
 * The caller supplies only its data + the row/tile composables; [listItemContent]/[gridItemContent]
 * receive the modifier carrying the first-item focus anchor and item animation and must apply it.
 * [headerSections] (when non-null) renders between the search pill and the count header;
 * [trailingItem] (when non-null) renders after the item run (the Podcasts search hand-off pill).
 * [isLoading] (first open before the backing flow emits) — and a sync filling a still-empty table —
 * renders the shimmer skeleton, shaped by [shimmerThumbnailShape] to match the real tiles, instead
 * of the empty placeholder; a running sync itself shows as the pull-to-refresh spinner.
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
    onRefresh: () -> Unit,
    titleRes: Int,
    emptyIconRes: Int,
    emptyTextRes: Int,
    isSyncing: StateFlow<Boolean>,
    listItemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
    gridItemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
    isLoading: Boolean = false,
    shimmerThumbnailShape: Shape = CircleShape,
    countPluralRes: Int = R.plurals.n_artist,
    searchPlaceholderRes: Int = R.string.search_artists,
    headerSections: (@Composable () -> Unit)? = null,
    trailingItem: (@Composable () -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val isSyncingValue by isSyncing.collectAsState()
    // A running sync surfaces as the expressive pull-to-refresh spinner (isRefreshing shows it for
    // toolbar/startup syncs too) — never the old full-screen "Setting up your library" overlay.
    // While the sync is filling an EMPTY table (first install), the shimmer stands in for content.
    val showShimmer = isLoading || (items.isEmpty() && isSyncingValue && searchQuery.isEmpty())

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val pullRefreshState = rememberPullToRefreshState()
    val headerItemCount = browseHeaderItemCount(headerSections != null)

    // Sticky letter sections in the LIST view, gated with the fast scroller (a short list doesn't
    // need either navigation aid).
    val letterBuckets = remember(items, viewType) {
        if (viewType == LibraryViewType.LIST && items.size >= MIN_ITEMS_FOR_FAST_SCROLL) {
            browseLetterBuckets(items.map(itemName))
        } else {
            emptyList()
        }
    }
    val bucketStarts = remember(letterBuckets) { letterBuckets.map { it.second } }

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

    val emptyPlaceholder = @Composable {
        EmptyPlaceholder(
            icon = emptyIconRes,
            text = stringResource(
                if (searchQuery.isEmpty()) emptyTextRes else R.string.no_results_found
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isSyncingValue,
                onRefresh = onRefresh,
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
                val contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

                when (viewType) {
                    LibraryViewType.LIST ->
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = contentPadding,
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

                            if (showShimmer) {
                                item(key = "loading") {
                                    LoadingListPlaceholder(count = 8)
                                }
                            } else if (items.isEmpty()) {
                                item(key = "empty_placeholder") {
                                    Box(Modifier.animateItem()) { emptyPlaceholder() }
                                }
                            }

                            if (letterBuckets.isEmpty()) {
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
                            } else {
                                letterBuckets.forEachIndexed { bucketIndex, (letter, start) ->
                                    val end = letterBuckets.getOrNull(bucketIndex + 1)?.second ?: items.size
                                    // Key by run start, not letter alone: bucketing strips leading
                                    // punctuation while the sort does not, so a letter can recur.
                                    stickyHeader(key = "letter_${letter}_$start", contentType = CONTENT_TYPE_HEADER) {
                                        BrowseLetterHeader(letter)
                                    }
                                    items(
                                        count = end - start,
                                        key = { offset -> itemKey(items[start + offset]) },
                                        contentType = { CONTENT_TYPE_ARTIST },
                                    ) { offset ->
                                        val index = start + offset
                                        listItemContent(
                                            index,
                                            items[index],
                                            Modifier
                                                .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                                .animateItem(),
                                        )
                                    }
                                }
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
                            contentPadding = contentPadding,
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

                            if (showShimmer) {
                                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                                    BrowseGridShimmer(shimmerThumbnailShape)
                                }
                            } else if (items.isEmpty()) {
                                item(key = "empty_placeholder", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.animateItem()) { emptyPlaceholder() }
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
                                scrollActiveListTo(browseLazyItemIndex(index, bucketStarts, headerItemCount), false)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .windowInsetsPadding(
                                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Vertical)
                            )
                            // Stay clear of the bottom-end button stack (back-to-top button + gap).
                            .padding(
                                top = 8.dp,
                                bottom = BackToTopBottomPadding + BackToTopButtonSize + FastScrollBottomGap,
                            ),
                    )
                }

                // Back to top button - inconspicuous but clear
                BrowseBackToTopButton(
                    visible = showBackToTop,
                    onClick = {
                        coroutineScope.launch {
                            // Reset TopAppBar height offset to prevent visual glitch
                            scrollBehavior.state.heightOffset = 0f
                            scrollActiveListTo(0, false)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                        )
                        .padding(end = 16.dp, top = 16.dp, bottom = BackToTopBottomPadding),
                )
        }

        // The expressive pull-to-refresh indicator (the Home look), over the pinned pill. It is
        // ALSO the sync-in-progress signal for toolbar/startup syncs (isRefreshing shows it without
        // a pull) — the one loading treatment for this screen.
        LoadingIndicator(
            isRefreshing = isSyncingValue,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

/** The fading back-to-top button (extracted so no lazy/Column scope shadows AnimatedVisibility). */
@Composable
private fun BrowseBackToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
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
}

/** One sticky letter section header in the LIST view (see [browseLetterBuckets]). */
@Composable
private fun BrowseLetterHeader(letter: Char) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            // An opaque fill so scrolled content never shows through the stuck header.
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

/** The GRID loading skeleton: two shimmering rows of three tiles, shaped like the real tiles. */
@Composable
private fun BrowseGridShimmer(thumbnailShape: Shape) {
    ShimmerHost {
        repeat(2) {
            Row(Modifier.fillMaxWidth()) {
                repeat(3) {
                    GridItemPlaceHolder(
                        modifier = Modifier.weight(1f),
                        thumbnailShape = thumbnailShape,
                        fillMaxWidth = true,
                    )
                }
            }
        }
    }
}
