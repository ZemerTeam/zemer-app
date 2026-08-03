package com.jtech.zemer.ui.screens.statuses

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.pluralStringResource
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
import com.jtech.zemer.statuses.sortedFlat
import com.jtech.zemer.statuses.statusAvatarUrl
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.BackTopAppBar
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.ui.component.LocalMenuState
import com.jtech.zemer.ui.component.Material3MenuGroup
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.component.SelectionTopActions
import com.jtech.zemer.ui.component.SortHeader
import com.jtech.zemer.ui.menu.SavedStatusMenu
import com.jtech.zemer.ui.utils.ItemWrapper
import com.jtech.zemer.ui.theme.HeaderFontFamily
import com.jtech.zemer.ui.utils.rememberVideoThumbnail
import com.jtech.zemer.ui.utils.savedStatusRoute
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.viewmodels.StatusDownloadsViewModel

private val TILE_WIDTH = 112.dp

@StringRes
private fun statusSortLabel(sort: StatusDownloadSort): Int = when (sort) {
    StatusDownloadSort.RECENT_SAVED -> R.string.status_sort_recent_saved
    StatusDownloadSort.RECENT_POSTED -> R.string.status_sort_recent_posted
}

/**
 * The Status downloads library: statuses the user saved to their device, as a flat chronological grid
 * (Recently saved / Recently posted) with kind chips. Reached from the Downloaded screen's Status card;
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
    val menuState = LocalMenuState.current
    var kind by rememberSaveable { mutableStateOf(StatusKindFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(StatusDownloadSort.RECENT_SAVED) }
    // Optional per-creator filter (null = all creators). One representative download per creator drives
    // the filter row's avatars.
    var selectedCreator by rememberSaveable { mutableStateOf<String?>(null) }
    val creatorReps = remember(downloads) { downloads.distinctBy { it.creatorId } }
    // Drop the selection if that creator no longer has any saved status.
    if (selectedCreator != null && creatorReps.none { it.creatorId == selectedCreator }) selectedCreator = null

    val filtered = remember(downloads, kind, selectedCreator) {
        downloads.filterByKind(kind).let { list ->
            selectedCreator?.let { id -> list.filter { it.creatorId == id } } ?: list
        }
    }
    val sortedFiltered = remember(filtered, sort) { filtered.sortedFlat(sort) }

    // Multi-select: wrap the visible items for the shared selection machinery. Re-derived (selection
    // resets) whenever the visible set changes; the wrapper's own default is `true`, so start unselected.
    var selection by remember { mutableStateOf(false) }
    val wrapped = remember(sortedFiltered) { sortedFiltered.map { ItemWrapper(it).apply { isSelected = false } } }
    if (selection && wrapped.isEmpty()) selection = false

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

        if (selection) {
            // Multi-select action cluster (shared): close / count / select-all / bulk-remove menu.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionTopActions(
                    wrapped = wrapped,
                    countLabel = { pluralStringResource(R.plurals.n_status, it, it) },
                    onExit = { selection = false },
                    onMore = {
                        val toRemove = wrapped.filter { it.isSelected }.map { it.item }
                        if (toRemove.isNotEmpty()) {
                            menuState.show {
                                Material3MenuGroup(
                                    items = listOf(
                                        Material3MenuItemData(
                                            icon = { Icon(painterResource(R.drawable.delete), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                            title = { Text(stringResource(R.string.status_remove)) },
                                            onClick = {
                                                menuState.dismiss()
                                                viewModel.removeAll(toRemove)
                                                selection = false
                                            },
                                        ),
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        } else {
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

            // A row of creator avatars (only when there's more than one creator to choose between);
            // tapping one filters the grid to that creator, tapping it again clears back to all.
            if (!blockVideos && creatorReps.size > 1) {
                SavedCreatorFilterRow(
                    creators = creatorReps,
                    selectedId = selectedCreator,
                    onSelect = { selectedCreator = it },
                )
            }

            SortHeader(
                sortType = sort,
                sortDescending = false,
                onSortTypeChange = { sort = it },
                onSortDescendingChange = {},
                sortTypeText = { statusSortLabel(it) },
                showDescending = false,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TILE_WIDTH),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomInset + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(wrapped, key = { it.item.id }) { wrapper ->
                val download = wrapper.item
                SavedStatusTile(
                    download = download,
                    selected = selection && wrapper.isSelected,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (selection) wrapper.isSelected = !wrapper.isSelected
                        else open(download)
                    },
                    onLongClick = {
                        if (selection) {
                            wrapper.isSelected = !wrapper.isSelected
                        } else {
                            menuState.show {
                                SavedStatusMenu(
                                    download = download,
                                    onRemove = { viewModel.remove(download) },
                                    onSelect = { selection = true; wrapper.isSelected = true },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * A horizontal row of creator avatars for filtering the saved-status grid to one creator. The selected
 * avatar gets an accent ring + label; tapping the selected one clears the filter. Non-lazy (a scrollable
 * Row) since the creator count is small. [creators] is one representative saved status per creator.
 */
@Composable
private fun SavedCreatorFilterRow(
    creators: List<StatusDownload>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        creators.forEach { rep ->
            val selected = rep.creatorId == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .focusBorder()
                    .clickable { onSelect(if (selected) null else rep.creatorId) },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(statusAvatarUrl(rep.creatorAvatar))
                        .crossfade(true)
                        .build(),
                    contentDescription = rep.creatorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant)
                        .then(
                            if (selected) Modifier.border(2.5.dp, colorScheme.primary, CircleShape) else Modifier
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rep.creatorName,
                    color = if (selected) colorScheme.primary else colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(60.dp),
                )
            }
        }
    }
}

/**
 * One saved status: a portrait 9:16 tile (real video frame / image / natively-rendered text) with a
 * posted date caption. Tap opens the local viewer (or toggles selection in select mode); long-press opens
 * the menu (or toggles). A [selected] tile shows an accent border + a check badge. Fully themed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedStatusTile(
    download: StatusDownload,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surfaceVariant)
                .then(
                    if (selected) Modifier.border(2.dp, colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier
                ),
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

            // Selection state: a dim scrim + an accent check badge in the corner.
            if (selected) {
                Box(Modifier.matchParentSize().background(colorScheme.scrim.copy(alpha = 0.35f)))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.done),
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

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
        Text(
            text = formatPostedAt(download.postedAt),
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
