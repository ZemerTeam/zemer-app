package com.jtech.zemer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jtech.zemer.statuses.StatusContentFilter
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.shouldFollowPinnedLeader
import com.jtech.zemer.statuses.sortedByUnseenFirst
import com.jtech.zemer.ui.component.StatusCreatorCircle

/**
 * The Home "Music Statuses" strip (title above is the standard NavigationTitle emitted by HomeScreen):
 * ONE LazyRow of creator story-circles. Presentation-only; visibility (the ShowHomeStatuses preference
 * + empty-hides) is the caller's, and the tap carries the creator's STABLE id so the story viewer opens
 * the right creator even after a recency re-sort. No impression tracking (impressions are per-videoId).
 */
@Composable
fun HomeStatusesRow(
    creators: List<StatusCreator>,
    seenPostIds: Set<String>,
    contentFilter: StatusContentFilter,
    onCreatorClick: (creatorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(creators, seenPostIds, contentFilter) {
        creators.sortedByUnseenFirst(seenPostIds, contentFilter)
    }
    // A keyed LazyRow keeps its current first item anchored when a new one is inserted before it, so a
    // pinned creator landing after the row rendered would sit off-screen to the left. Follow a NEWLY
    // arrived pinned leader back to index 0 (only while the row is at its start), once: the saved
    // leader keeps a re-entry or a read-state reorder from yanking a scrolled row.
    val listState = rememberLazyListState()
    val pinnedLeader = ordered.firstOrNull()?.takeIf { it.pinnedSection != null }?.id
    var followedLeader by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pinnedLeader) {
        if (shouldFollowPinnedLeader(pinnedLeader, followedLeader, listState.firstVisibleItemIndex)) {
            listState.scrollToItem(0)
        }
        if (pinnedLeader != null) followedLeader = pinnedLeader
    }
    LazyRow(
        state = listState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
            .add(WindowInsets(left = 12.dp, right = 12.dp))
            .asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        items(
            items = ordered,
            key = { creator -> creator.id },
        ) { creator ->
            StatusCreatorCircle(
                creator = creator,
                seenPostIds = seenPostIds,
                contentFilter = contentFilter,
                onClick = { onCreatorClick(creator.id) },
            )
        }
    }
}
