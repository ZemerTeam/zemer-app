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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jtech.zemer.statuses.StatusCreator
import com.jtech.zemer.statuses.allStatusesSeen
import com.jtech.zemer.ui.component.StatusCreatorCircle

/**
 * The Home "Music Statuses" strip (title above is the standard NavigationTitle emitted by HomeScreen):
 * ONE LazyRow of creator story-circles. Presentation-only; visibility (the ShowHomeStatuses preference
 * + empty-hides) is the caller's, and the tap carries the creator's INDEX so the story viewer can
 * advance across the same list. No impression tracking (impressions are per-videoId by contract).
 */
@Composable
fun HomeStatusesRow(
    creators: List<StatusCreator>,
    seenPostIds: Set<String>,
    onCreatorClick: (creatorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fully-viewed creators sink to the end (WhatsApp), preserving recency order within each group.
    val ordered = remember(creators, seenPostIds) {
        creators.sortedBy { it.allStatusesSeen(seenPostIds) }
    }
    LazyRow(
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
                onClick = { onCreatorClick(creator.id) },
            )
        }
    }
}
