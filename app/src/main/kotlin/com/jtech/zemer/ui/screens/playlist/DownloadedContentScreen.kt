package com.jtech.zemer.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.IconCategoryCard
import com.jtech.zemer.viewmodels.DownloadedContentViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadedContentScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: DownloadedContentViewModel = hiltViewModel(),
) {
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val musicCount by viewModel.downloadedMusicCount.collectAsState()
    val videoCount by viewModel.downloadedVideoCount.collectAsState()
    val statusCount by viewModel.downloadedStatusCount.collectAsState()
    val podcastCount by viewModel.downloadedPodcastCount.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                // Two-per-row so the tiles stay readable now that there are four download types; each
                // shares the one neutral IconCategoryCard box (color/shape/typography identical), the
                // caller only varies icon/labels/destination.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 2,
                ) {
                    IconCategoryCard(
                        iconRes = R.drawable.music_note,
                        title = stringResource(R.string.music),
                        subtitle = pluralStringResource(R.plurals.n_song, musicCount, musicCount),
                        onClick = { navController.navigate("auto_playlist/downloaded") },
                        modifier = Modifier.weight(1f),
                    )

                    // The Videos tile stays reachable when videos are blocked (like every other video
                    // surface in this redesign): the screen plays audio-first and never renders watchable
                    // video, and it's the only place blocked users can reach the "Show in downloaded
                    // music" switch for video-song downloads they may already have. Relabeled to match
                    // the artist page's "Video songs" section for blocked users.
                    IconCategoryCard(
                        iconRes = R.drawable.slow_motion_video,
                        title = stringResource(if (blockVideos) R.string.video_songs else R.string.videos),
                        subtitle = pluralStringResource(R.plurals.n_video, videoCount, videoCount),
                        onClick = { navController.navigate("downloaded_videos") },
                        modifier = Modifier.weight(1f),
                    )

                    // Downloaded podcast episodes. Reuses the auto-playlist screen (same as Music) with a
                    // downloaded_episodes source; a video episode keeps its in-player video toggle there.
                    IconCategoryCard(
                        iconRes = R.drawable.podcast,
                        title = stringResource(R.string.podcasts),
                        subtitle = pluralStringResource(R.plurals.n_episode, podcastCount, podcastCount),
                        onClick = { navController.navigate("auto_playlist/downloaded_episodes") },
                        modifier = Modifier.weight(1f),
                    )

                    // The Status tile stays gated: Music Status is genuinely video-first/watchable
                    // content, unlike the Videos tile above (see AGENTS.md §Music Status).
                    if (!blockVideos) {
                        IconCategoryCard(
                            iconRes = R.drawable.music_status,
                            title = stringResource(R.string.status),
                            subtitle = pluralStringResource(R.plurals.n_status, statusCount, statusCount),
                            onClick = { navController.navigate("status_downloads") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        BackTopAppBar(
            title = {
                AppBarTitle(text = stringResource(R.string.offline))
            },
            navController = navController,
            scrollBehavior = scrollBehavior,
        )
    }
}
