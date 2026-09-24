package com.jtech.zemer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockPodcastsKey
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.constants.PodcastViewTypeKey
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.ui.component.BrowseScreenScaffold
import com.jtech.zemer.ui.component.ContentTabChipsRow
import com.jtech.zemer.ui.component.YouTubeGridItem
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.utils.podcastRoute
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.KidZoneViewModel
import com.metrolist.innertube.models.PodcastItem
import kotlinx.coroutines.flow.StateFlow

@Composable
fun KidZoneScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: KidZoneViewModel = hiltViewModel(),
) {
    // Home-selector pattern: Block Podcasts removes the PODCASTS tab (the category gate); with one
    // tab left the chip row hides and the screen is the plain artist browse it always was.
    val (blockPodcasts, _) = rememberPreference(BlockPodcastsKey, defaultValue = false)
    val tabs = visibleKidZoneTabs(blockPodcasts)
    var selectedTab by rememberSaveable { mutableStateOf(KidZoneTab.ARTISTS) }
    val tab = effectiveKidZoneTab(selectedTab, tabs)

    // The chip row rides each tab's scaffold as a header section, so it scrolls with content (the
    // Home chips rule) and both tabs keep their native browse scaffold untouched.
    val chips: (@Composable () -> Unit)? = if (tabs.size > 1) {
        {
            ContentTabChipsRow(
                chips = tabs.map { t ->
                    t to stringResource(
                        when (t) {
                            KidZoneTab.ARTISTS -> R.string.artists
                            KidZoneTab.PODCASTS -> R.string.podcasts
                        },
                    )
                },
                currentValue = tab,
                onValueUpdate = { selectedTab = it },
            )
        }
    } else {
        null
    }

    when (tab) {
        KidZoneTab.ARTISTS -> ArtistBrowseScreenContent(
            navController = navController,
            scrollBehavior = scrollBehavior,
            artists = viewModel.allArtists.collectAsState().value,
            searchQuery = viewModel.searchQuery.collectAsState().value,
            onSearchQueryChange = { viewModel.searchQuery.value = it },
            onRefresh = { viewModel.sync() },
            isSyncing = viewModel.isSyncing,
            titleRes = R.string.kid_zone,
            emptyIconRes = R.drawable.kid_zone,
            emptyTextRes = R.string.kid_zone_empty,
            onRequestThumb = { viewModel.requestThumb(it) },
            topSections = chips,
        )
        KidZoneTab.PODCASTS -> KidZonePodcastsContent(
            navController = navController,
            scrollBehavior = scrollBehavior,
            podcasts = viewModel.kidPodcasts.collectAsState().value,
            searchQuery = viewModel.podcastSearchQuery.collectAsState().value,
            onSearchQueryChange = { viewModel.podcastSearchQuery.value = it },
            onRefresh = { viewModel.fetchKidPodcasts() },
            isRefreshing = viewModel.isRefreshingPodcasts,
            topSections = chips,
        )
    }
}

/**
 * The KidZone Podcasts tab: the server's kid-flagged shows (`/podcasts?kidZone=1`) on the shared
 * browse scaffold. A card opens the SHOW directly with the kidZone navigation context — never the
 * host channel from here (drill-in discipline; the show screen's "View channel" stays kid-scoped
 * through the same flag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KidZonePodcastsContent(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    podcasts: List<PodcastItem>?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: StateFlow<Boolean>,
    topSections: (@Composable () -> Unit)?,
) {
    var viewType by rememberEnumPreference(PodcastViewTypeKey, LibraryViewType.GRID)
    val filtered = podcasts?.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
    }

    fun openShow(item: PodcastItem) {
        podcastRoute(item.id, kidZone = true)?.let(navController::navigate)
    }

    BrowseScreenScaffold(
        navController = navController,
        scrollBehavior = scrollBehavior,
        items = filtered.orEmpty(),
        isLoading = filtered == null,
        shimmerThumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
        itemKey = { it.id },
        itemName = { it.title },
        viewType = viewType,
        onToggleViewType = { viewType = viewType.toggle() },
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onRefresh = onRefresh,
        titleRes = R.string.kid_zone,
        emptyIconRes = R.drawable.podcast,
        emptyTextRes = R.string.library_podcast_empty,
        isSyncing = isRefreshing,
        countPluralRes = R.plurals.n_show,
        searchPlaceholderRes = R.string.search_podcasts,
        topSections = topSections,
        listItemContent = { _, item, modifier ->
            YouTubeListItem(
                item = item,
                isActive = false,
                isPlaying = false,
                modifier = modifier
                    .focusBorder()
                    .clickable { openShow(item) },
            )
        },
        gridItemContent = { _, item, modifier ->
            YouTubeGridItem(
                item = item,
                isActive = false,
                isPlaying = false,
                thumbnailRatio = 1f,
                modifier = modifier
                    .focusBorder()
                    .clickable { openShow(item) },
            )
        },
    )
}
