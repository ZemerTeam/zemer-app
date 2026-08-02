package com.jtech.zemer.ui.screens.statuses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.StatusSource
import com.jtech.zemer.statuses.sortedByUnseenFirst
import com.jtech.zemer.ui.component.AppBarTitle
import com.jtech.zemer.ui.component.ArtistSearchField
import com.jtech.zemer.ui.component.BackNavigationIcon
import com.jtech.zemer.ui.component.NavigationTitle
import com.jtech.zemer.ui.component.StatusCreatorCircle
import com.jtech.zemer.ui.component.TopAppBarActionButton
import com.jtech.zemer.ui.component.zemerTopAppBarColors
import com.jtech.zemer.ui.utils.storyRoute
import com.jtech.zemer.viewmodels.ZemerStatusesViewModel

/**
 * The "See all" screen for the Home "Music Status" row. A search field (filtering ALL sources) sits on
 * top, then the creators grouped into SEPARATE sections by platform (JewishStatus, YidStatus) - the Home
 * row is uniform, this screen shows the split. Reuses the shared [ZemerStatusesViewModel] state (same
 * creators + seen the row shows), so it is instant from Home and self-populates if entered cold.
 * Fully-viewed creators sink to the end within each section, matching the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusesScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ZemerStatusesViewModel = hiltViewModel(),
) {
    val creators by viewModel.creators.collectAsState()
    val seenPostIds by viewModel.seenPostIds.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    val jewish = remember(creators, seenPostIds, query) {
        creators.filterSourceAndQuery(StatusSource.JEWISH_STATUS, query).sortedByUnseenFirst(seenPostIds)
    }
    val yid = remember(creators, seenPostIds, query) {
        creators.filterSourceAndQuery(StatusSource.YID_STATUS, query).sortedByUnseenFirst(seenPostIds)
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    fun open(creator: StatusCreator) = navController.navigate(storyRoute(creator.id))

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ArtistSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    searchFocus = searchFocus,
                    placeholderRes = R.string.search_status_hint,
                    modifier = Modifier.padding(top = 8.dp), // sit a bit lower under the app bar
                )
            }
            statusSection(R.string.status_source_jewishstatus, jewish, seenPostIds, ::open)
            statusSection(R.string.status_source_yidstatus, yid, seenPostIds, ::open)
        }
    }

    TopAppBar(
        title = { AppBarTitle(stringResource(R.string.statuses)) },
        navigationIcon = { BackNavigationIcon(navController) },
        // A gear to the status content filters (hide text-only / image), which live in Appearance settings.
        actions = {
            TopAppBarActionButton(
                icon = R.drawable.settings,
                contentDescription = stringResource(R.string.settings),
                onClick = { navController.navigate("settings/appearance?scrollTo=status") },
            )
        },
        scrollBehavior = scrollBehavior,
        colors = zemerTopAppBarColors(),
    )
}

private fun List<StatusCreator>.filterSourceAndQuery(source: StatusSource, query: String) =
    filter { it.source == source && (query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true)) }

/** One platform section: the shared Home-row section title (only when it has matches) then its circles. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.statusSection(
    titleRes: Int,
    creators: List<StatusCreator>,
    seenPostIds: Set<String>,
    onOpen: (StatusCreator) -> Unit,
) {
    if (creators.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$titleRes") {
        NavigationTitle(title = stringResource(titleRes))
    }
    items(items = creators, key = { it.id }) { creator ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            StatusCreatorCircle(
                creator = creator,
                seenPostIds = seenPostIds,
                onClick = { onOpen(creator) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
