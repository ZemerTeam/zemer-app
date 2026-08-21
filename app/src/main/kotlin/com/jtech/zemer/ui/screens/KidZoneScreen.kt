package com.jtech.zemer.ui.screens

import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.viewmodels.KidZoneViewModel

@Composable
fun KidZoneScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: KidZoneViewModel = hiltViewModel(),
) = ArtistBrowseScreenContent(
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
)
