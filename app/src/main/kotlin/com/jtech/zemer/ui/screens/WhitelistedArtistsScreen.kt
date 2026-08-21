package com.jtech.zemer.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.ArtistViewTypeKey
import com.jtech.zemer.constants.LibraryViewType
import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.ui.component.BrowseScreenScaffold
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.WhitelistedArtistGridItem
import com.jtech.zemer.ui.component.WhitelistedArtistListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.WhitelistedArtistsViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun WhitelistedArtistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: WhitelistedArtistsViewModel = hiltViewModel(),
) = ArtistBrowseScreenContent(
    navController = navController,
    scrollBehavior = scrollBehavior,
    artists = viewModel.allArtists.collectAsState().value,
    searchQuery = viewModel.searchQuery.collectAsState().value,
    onSearchQueryChange = { viewModel.searchQuery.value = it },
    onRefresh = { viewModel.sync() },
    isSyncing = viewModel.isSyncing,
    titleRes = R.string.artists,
    emptyIconRes = R.drawable.artist,
    emptyTextRes = R.string.library_artist_empty,
    onRequestThumb = { viewModel.requestThumb(it) },
)

/**
 * The ONE artist-browse body shared by the Artists tab and Kid Zone — the two screens differ only in
 * their ViewModel (which whitelist slice backs them) and three resource ids, so everything else
 * (dedup, view-type preference, item wiring) lives here once and cannot drift between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistBrowseScreenContent(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    artists: List<Artist>?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isSyncing: StateFlow<Boolean>,
    titleRes: Int,
    emptyIconRes: Int,
    emptyTextRes: Int,
    onRequestThumb: (String) -> Unit,
) {
    val menuState = LocalMenuState.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val coroutineScope = rememberCoroutineScope()

    // Single source for what the scaffold renders: both view types and the fast scroller must
    // agree on items and positions, so the de-duplication happens once, here. Null = the DB flow
    // hasn't emitted yet (the scaffold shimmers instead of flashing the empty state).
    val displayedArtists = remember(artists) { artists?.distinctBy { it.artist.name } }

    BrowseScreenScaffold(
        navController = navController,
        scrollBehavior = scrollBehavior,
        items = displayedArtists.orEmpty(),
        isLoading = displayedArtists == null,
        itemKey = { it.id },
        itemName = { it.artist.name },
        viewType = viewType,
        onToggleViewType = { viewType = viewType.toggle() },
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onRefresh = onRefresh,
        titleRes = titleRes,
        emptyIconRes = emptyIconRes,
        emptyTextRes = emptyTextRes,
        isSyncing = isSyncing,
        listItemContent = { _, artist, modifier ->
            WhitelistedArtistListItem(
                navController = navController,
                menuState = menuState,
                coroutineScope = coroutineScope,
                modifier = modifier,
                artist = artist,
                onRequestThumb = { onRequestThumb(artist.id) },
                highlightQuery = searchQuery,
            )
        },
        gridItemContent = { _, artist, modifier ->
            WhitelistedArtistGridItem(
                navController = navController,
                menuState = menuState,
                coroutineScope = coroutineScope,
                modifier = modifier,
                artist = artist,
                onRequestThumb = { onRequestThumb(artist.id) },
                highlightQuery = searchQuery,
            )
        },
    )
}
