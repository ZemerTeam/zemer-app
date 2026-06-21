package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.latestreleases.LatestReleaseCard
import com.jtech.zemer.latestreleases.LatestReleaseFilter
import com.jtech.zemer.latestreleases.applyFilter
import com.jtech.zemer.latestreleases.shufflePlay
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.HideOnScrollFAB
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.LatestReleasesViewModel

/**
 * The "See all" screen for the Home Latest Releases section: the full kosher latest-releases feed as
 * a vertical list, newest-first. Uses its own [LatestReleasesViewModel] instance but is backed by the
 * same process-wide [com.jtech.zemer.latestreleases.LatestReleasesStore] cache as the Home section, so
 * the feed is reused (a conditional refresh, not a fresh fetch). When the feed is empty/unavailable the
 * list is simply empty — nothing is forced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestReleasesScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LatestReleasesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val lazyListState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(LatestReleaseFilter.ALL) }
    val visibleReleases = remember(releases, filter) { releases.applyFilter(filter) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            // All / Albums / Songs filter — scrolls with the list so it never steals vertical space
            // on small screens.
            item(key = "filter") {
                ChipsRow(
                    chips = listOf(
                        LatestReleaseFilter.ALL to stringResource(R.string.filter_all),
                        LatestReleaseFilter.ALBUMS to stringResource(R.string.filter_albums),
                        LatestReleaseFilter.SONGS to stringResource(R.string.filter_songs),
                    ),
                    currentValue = filter,
                    onValueUpdate = { filter = it },
                )
            }
            items(
                items = visibleReleases,
                key = { it.browseId },
            ) { release ->
                LatestReleaseCard(
                    release = release,
                    navController = navController,
                    playerConnection = playerConnection,
                    database = database,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    asGrid = false,
                )
            }
        }

        HideOnScrollFAB(
            visible = visibleReleases.isNotEmpty(),
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = { visibleReleases.shufflePlay(playerConnection, context.getString(R.string.latest_releases)) },
        )
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
