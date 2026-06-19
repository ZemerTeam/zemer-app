package com.jtech.zemer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.latestreleases.isNowPlaying
import com.jtech.zemer.latestreleases.isPlayableSingle
import com.jtech.zemer.latestreleases.openOrPlay
import com.jtech.zemer.latestreleases.relativeDateLabel
import com.jtech.zemer.latestreleases.toAlbumItem
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.menu.YouTubeAlbumMenu
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.utils.joinByBullet
import com.jtech.zemer.viewmodels.LatestReleasesViewModel

/**
 * The "See all" screen for the Home Latest Releases section: the full kosher latest-releases feed as
 * a vertical list, newest-first. Uses its own [LatestReleasesViewModel] instance but is backed by the
 * same process-wide [com.jtech.zemer.latestreleases.LatestReleasesStore] cache as the Home section, so
 * the feed is reused (a conditional refresh, not a fresh fetch). When the feed is empty/unavailable the
 * list is simply empty — nothing is forced.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LatestReleasesScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LatestReleasesViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val releases by viewModel.releases.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(
            items = releases,
            key = { it.browseId },
        ) { release ->
            val album = remember(release.browseId) { release.toAlbumItem() }
            val dateLabel = remember(release.browseId) { release.relativeDateLabel() }
            YouTubeListItem(
                item = album,
                subtitleOverride = joinByBullet(release.artistName, dateLabel),
                centeredPlayButton = release.isPlayableSingle(),
                isActive = release.isNowPlaying(mediaMetadata),
                isPlaying = isPlaying,
                modifier = Modifier.combinedClickable(
                    onClick = { release.openOrPlay(navController, playerConnection, database) },
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

    TopAppBar(
        title = { Text(stringResource(R.string.latest_releases)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
