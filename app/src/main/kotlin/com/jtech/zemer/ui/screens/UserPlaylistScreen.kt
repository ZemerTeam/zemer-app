package com.jtech.zemer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.ui.component.AppStateView
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.shimmer.ListItemPlaceHolder
import com.jtech.zemer.ui.component.YouTubeListItem
import com.jtech.zemer.ui.component.shimmer.ShimmerHost
import com.jtech.zemer.ui.menu.ImportPlaylistDialog
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.viewmodels.UserPlaylistViewModel

/**
 * A shared user playlist opened from its link (issue #176). Renders the immutable snapshot's
 * surviving tracks (server-filtered for THIS receiver + the local blocked-ids pass): tap/Play/
 * Shuffle via a [ListQueue] tagged `shared:<id>`, and **Save a copy** imports it as a local
 * playlist (the natural completion of the share loop, via the shared [ImportPlaylistDialog]).
 * States: 404 → "not available"; all-filtered → an honest empty state, never a broken screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: UserPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val page by viewModel.page.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val notFound by viewModel.notFound.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    val playSource = PlaySource.shared(viewModel.shareId)
    fun play(startIndex: Int, shuffle: Boolean) {
        val songs = page?.songs ?: return
        val items = (if (shuffle) songs.shuffled() else songs).map { it.toMediaMetadata().toMediaItem() }
        playerConnection?.playQueue(
            ListQueue(title = page?.header?.title, items = items, startIndex = startIndex, playSource = playSource),
        )
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> item(key = "loading") {
                ShimmerHost { repeat(8) { ListItemPlaceHolder() } }
            }

            notFound -> item(key = "not_found") {
                AppStateView(
                    title = stringResource(R.string.user_playlist_not_found),
                    subtitle = "",
                    icon = R.drawable.queue_music,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            page?.songs.isNullOrEmpty() -> item(key = "empty") {
                // The link is valid but nothing survives this receiver's content filters — an
                // honest state, per the handoff ("must not look broken").
                AppStateView(
                    title = page?.header?.title ?: "",
                    subtitle = stringResource(R.string.user_playlist_empty_filtered),
                    icon = R.drawable.queue_music,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            else -> {
                val songs = page!!.songs
                item(key = "header") {
                    Text(
                        text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item(key = "actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Button(onClick = { play(0, shuffle = false) }, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.play), null)
                            Text(stringResource(R.string.play), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { play(0, shuffle = true) }, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.shuffle), null)
                            Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                item(key = "save_copy") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { showImportDialog = true }) {
                            Text(stringResource(R.string.user_playlist_save_copy))
                        }
                    }
                }
                items(items = songs, key = { it.id }) { song ->
                    YouTubeListItem(
                        item = song,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { play(songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0), shuffle = false) },
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            isVisible = true,
            playlistTitle = page?.header?.title.orEmpty(),
            onGetSong = {
                val songs = page?.songs.orEmpty()
                database.transaction { songs.forEach { insert(it.toMediaMetadata()) } }
                songs.map { it.id }
            },
            onDismiss = { showImportDialog = false },
        )
    }

    TopAppBar(
        title = { Text(page?.header?.title ?: "") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
