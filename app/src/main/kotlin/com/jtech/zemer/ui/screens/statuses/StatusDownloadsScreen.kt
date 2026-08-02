package com.jtech.zemer.ui.screens.statuses

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.statuses.StatusDownload
import com.jtech.zemer.statuses.StatusDownloadSort
import com.jtech.zemer.statuses.StatusKindFilter
import com.jtech.zemer.statuses.filterByKind
import com.jtech.zemer.statuses.formatPostedAt
import com.jtech.zemer.statuses.groupByCreator
import com.jtech.zemer.statuses.sortedFlat
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.SortHeader
import com.jtech.zemer.ui.theme.HeaderFontFamily
import com.jtech.zemer.ui.utils.rememberVideoThumbnail
import com.jtech.zemer.ui.utils.savedStatusRoute
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.StatusDownloadsViewModel

private val TILE_WIDTH = 112.dp

@StringRes
private fun statusSortLabel(sort: StatusDownloadSort): Int = when (sort) {
    StatusDownloadSort.CREATOR -> R.string.status_sort_creator
    StatusDownloadSort.RECENT_SAVED -> R.string.status_sort_recent_saved
    StatusDownloadSort.RECENT_POSTED -> R.string.status_sort_recent_posted
}

/**
 * The Status downloads library: statuses the user saved to their device. Grouped by creator (per-creator
 * shelves) by default, or a flat chronological grid. Reached from the Downloaded screen's Status card;
 * hidden when videos are blocked (the entry card is gated the same way, this is the defensive backstop).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDownloadsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: StatusDownloadsViewModel = hiltViewModel(),
) {
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)
    val downloads by viewModel.downloads.collectAsState()
    var kind by rememberSaveable { mutableStateOf(StatusKindFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(StatusDownloadSort.CREATOR) }

    val filtered = remember(downloads, kind) { downloads.filterByKind(kind) }

    fun open(download: StatusDownload) =
        navController.navigate(savedStatusRoute(download.creatorId, download.id))

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        BackTopAppBar(
            title = { AppBarTitle(text = stringResource(R.string.status)) },
            navController = navController,
            scrollBehavior = scrollBehavior,
        )

        ChipsRow(
            chips = listOf(
                StatusKindFilter.ALL to stringResource(R.string.status_chip_all),
                StatusKindFilter.VIDEO to stringResource(R.string.status_chip_video),
                StatusKindFilter.IMAGE to stringResource(R.string.status_chip_image),
                StatusKindFilter.TEXT to stringResource(R.string.status_chip_text),
            ),
            currentValue = kind,
            onValueUpdate = { kind = it },
        )

        SortHeader(
            sortType = sort,
            sortDescending = false,
            onSortTypeChange = { sort = it },
            onSortDescendingChange = {},
            sortTypeText = { statusSortLabel(it) },
            showDescending = false,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (filtered.isEmpty() || blockVideos) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.status_downloads_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
        if (sort == StatusDownloadSort.CREATOR) {
            // Per-creator shelves - orderly rows, no ragged grid cells.
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset),
                modifier = Modifier.fillMaxSize(),
            ) {
                filtered.groupByCreator().forEach { group ->
                    item(key = "hdr_${group.creatorId}") {
                        NavigationTitle(title = group.creatorName)
                    }
                    item(key = "row_${group.creatorId}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(group.items, key = { it.id }) { download ->
                                SavedStatusTile(
                                    download = download,
                                    showCreator = false,
                                    modifier = Modifier.width(TILE_WIDTH),
                                    onOpen = { open(download) },
                                    onRemove = { viewModel.remove(download) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_WIDTH),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered.sortedFlat(sort), key = { it.id }) { download ->
                    SavedStatusTile(
                        download = download,
                        showCreator = true,
                        modifier = Modifier.fillMaxWidth(),
                        onOpen = { open(download) },
                        onRemove = { viewModel.remove(download) },
                    )
                }
            }
        }
    }
}

/**
 * One saved status: a portrait 9:16 tile (real video frame / image / natively-rendered text) with a
 * posted date caption. Tap opens the local viewer; long-press offers Remove. Fully themed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedStatusTile(
    download: StatusDownload,
    showCreator: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when (download.kind) {
                "video" -> {
                    val frame = rememberVideoThumbnail(download.mediaUri)
                    if (frame != null) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // Play marker on a scrim chip (bottom-start), always shown so a video reads as video.
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                "text" -> Text(
                    // Rendered natively so it fits the tile (the saved file is a full image).
                    text = download.textBody ?: download.caption.orEmpty(),
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
                else -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(download.mediaUri).crossfade(true).build(),
                    contentDescription = download.creatorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.status_remove)) },
                    leadingIcon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }

        if (showCreator) {
            Text(
                text = download.creatorName,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = HeaderFontFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = formatPostedAt(download.postedAt),
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (showCreator) 0.dp else 4.dp),
        )
    }
}
