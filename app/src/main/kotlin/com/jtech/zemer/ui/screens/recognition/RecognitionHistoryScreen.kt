package com.jtech.zemer.ui.screens.recognition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.LocalPlayerConnection
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.ui.component.DefaultDialog
import com.jtech.zemer.ui.component.IconButton
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.utils.backToMain
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.viewmodels.RecognitionHistoryViewModel
import com.metrolist.innertube.models.WatchEndpoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionHistoryScreen(
    navController: NavController,
    viewModel: RecognitionHistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.history.collectAsState()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognition_history)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }, onLongClick = {}) {
                            Icon(
                                painterResource(R.drawable.clear_all),
                                contentDescription = stringResource(R.string.recognition_history_clear),
                            )
                        }
                    }
                },
            )
        },
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.recognition_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(items, key = { it.id }) { entry ->
                    RecognitionHistoryRow(
                        entry = entry,
                        onPlay = {
                            playerConnection?.playQueue(
                                YouTubeQueue(WatchEndpoint(videoId = entry.songId), database = database),
                            )
                        },
                        onDelete = { viewModel.delete(entry) },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        DefaultDialog(
            onDismiss = { showClearDialog = false },
            title = { Text(stringResource(R.string.recognition_history_clear_confirm)) },
            buttons = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.recognition_history_clear))
                }
            },
            content = {},
        )
    }
}

@Composable
private fun RecognitionHistoryRow(
    entry: RecognitionHistoryEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .focusBorder()
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = entry.thumbnailUrl?.resize(144, 144),
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, onLongClick = {}) {
            Icon(
                painterResource(R.drawable.delete),
                contentDescription = stringResource(R.string.recognition_history_delete),
            )
        }
    }
}
