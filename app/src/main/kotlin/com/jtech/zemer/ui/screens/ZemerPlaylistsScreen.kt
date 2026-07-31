package com.jtech.zemer.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.GridThumbnailHeight
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.ZemerCuratedPlaylistGridItem
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.viewmodels.ZemerCuratedPlaylistsViewModel

/**
 * The "See all" screen for the Home "Zemer Playlists" section: every curated playlist as a vertical
 * grid, in the server's editorial order. Uses its own [ZemerCuratedPlaylistsViewModel] instance (a
 * fresh fetch on open — the endpoint's freshness contract is a plain re-fetch); when the feed is
 * empty/unavailable the grid is simply empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZemerPlaylistsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerCuratedPlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = playlists,
            key = { it.id },
        ) { playlist ->
            ZemerCuratedPlaylistGridItem(
                playlist = playlist,
                fillMaxWidth = true,
                modifier = Modifier.clickable {
                    // The slug is server-controlled: encode so an unexpected '/'/'?' can never
                    // break route matching (a crash on tap).
                    navController.navigate("zemer_playlist/${Uri.encode(playlist.id)}")
                },
            )
        }
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.zemer_playlists)) },
        navigationIcon = { BackNavigationIcon(navController) },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}
