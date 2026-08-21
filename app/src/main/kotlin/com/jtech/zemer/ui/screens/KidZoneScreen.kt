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
import com.jtech.zemer.ui.component.BrowseScreenScaffold
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.WhitelistedArtistGridItem
import com.jtech.zemer.ui.component.WhitelistedArtistListItem
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.viewmodels.KidZoneViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidZoneScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: KidZoneViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)

    val artists by viewModel.allArtists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Single source for what the scaffold renders (see WhitelistedArtistsScreen). Null = the DB
    // flow hasn't emitted yet (the scaffold shimmers instead of flashing the empty state).
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
        onSearchQueryChange = { viewModel.searchQuery.value = it },
        onRefresh = { viewModel.sync() },
        titleRes = R.string.kid_zone,
        emptyIconRes = R.drawable.kid_zone,
        emptyTextRes = R.string.kid_zone_empty,
        syncProgress = viewModel.syncProgress,
        isSyncing = viewModel.isSyncing,
        listItemContent = { _, artist, modifier ->
            WhitelistedArtistListItem(
                navController = navController,
                menuState = menuState,
                coroutineScope = coroutineScope,
                modifier = modifier,
                artist = artist,
                onRequestThumb = { viewModel.requestThumb(artist.id) },
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
                onRequestThumb = { viewModel.requestThumb(artist.id) },
                highlightQuery = searchQuery,
            )
        },
    )
}
