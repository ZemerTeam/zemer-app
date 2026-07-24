package com.jtech.zemer.tracking

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reports what the app SHOWED, for the ranking side's exposure dampener.
 *
 * The normative definition of an impression (agreed with the tracking maintainer, mirrored in
 * `docs/tracking/README.md`) is deliberately STRICTER than "rendered": an item counts only once it
 * is inside the viewport AND has stayed there for [DWELL_MS]. Compose composes ahead of the
 * viewport, so counting composition would credit songs with attention they never got — and since
 * the dampener DOCKS a song for being widely shown, over-counting exposure silently penalises
 * songs, while under-counting is merely conservative. When in doubt, do not report.
 *
 * Dedup, chunking, videoId filtering and the drop-under-backoff policy all live in
 * [Tracker.impression]; this layer only decides what is on screen.
 */
private const val DWELL_MS = 300L

/** Rows a fling passes through are never reported — only what the user settled on. */
@Composable
fun <T> TrackImpressions(
    surface: String,
    state: LazyListState,
    items: List<T>,
    idOf: (T) -> String?,
    parent: LazyListState? = null,
    parentKey: Any? = null,
) {
    LaunchedEffect(surface, state, items, parent, parentKey) {
        reportOnDwell(
            surface = surface,
            items = items,
            idOf = idOf,
            visibleIndices = snapshotFlow {
                if (isOffScreen(parent, parentKey)) emptyList()
                else state.layoutInfo.visibleItemsInfo.map { it.index }
            },
        )
    }
}

/** Grid variant — same definition, same dwell. */
@Composable
fun <T> TrackImpressions(
    surface: String,
    state: LazyGridState,
    items: List<T>,
    idOf: (T) -> String?,
    parent: LazyListState? = null,
    parentKey: Any? = null,
) {
    LaunchedEffect(surface, state, items, parent, parentKey) {
        reportOnDwell(
            surface = surface,
            items = items,
            idOf = idOf,
            visibleIndices = snapshotFlow {
                if (isOffScreen(parent, parentKey)) emptyList()
                else state.layoutInfo.visibleItemsInfo.map { it.index }
            },
        )
    }
}

/**
 * A row nested inside a scrolling parent reports its OWN viewport, which says nothing about whether
 * the row itself is on screen — and the parent composes an item or so beyond its viewport, so an
 * ungated inner row happily reports a screenful of songs the user never reached. Callers inside a
 * [androidx.compose.foundation.lazy.LazyColumn] pass the parent's state and their item key so the
 * two viewports are ANDed together.
 */
private fun isOffScreen(parent: LazyListState?, parentKey: Any?): Boolean {
    if (parent == null || parentKey == null) return false
    return parent.layoutInfo.visibleItemsInfo.none { it.key == parentKey }
}

/**
 * For heterogeneous lists, where a visible INDEX identifies nothing: headers, chips and section
 * titles share the index space with results, so index→item arithmetic silently reports the wrong
 * songs. [idOfKey] maps a list item's key — which the caller already assigned — to a videoId,
 * returning null for everything that isn't a song.
 */
@Composable
fun TrackImpressionsByKey(
    surface: String,
    state: LazyListState,
    parent: LazyListState? = null,
    parentKey: Any? = null,
    idOfKey: (Any?) -> String?,
) {
    LaunchedEffect(surface, state, parent, parentKey, idOfKey) {
        reportOnDwell(
            surface = surface,
            visibleIds = snapshotFlow {
                if (isOffScreen(parent, parentKey)) emptyList()
                else state.layoutInfo.visibleItemsInfo.mapNotNull { idOfKey(it.key) }
            },
        )
    }
}

private suspend fun <T> reportOnDwell(
    surface: String,
    items: List<T>,
    idOf: (T) -> String?,
    visibleIndices: Flow<List<Int>>,
) = reportOnDwell(
    surface = surface,
    visibleIds = visibleIndices.map { indices ->
        indices.mapNotNull { index -> items.getOrNull(index)?.let(idOf) }
    },
)

/**
 * [collectLatest] restarts on every scroll frame, so the [delay] only elapses once the viewport
 * holds still — which is what makes a fling report nothing rather than reporting everything it flew
 * past.
 */
private suspend fun reportOnDwell(surface: String, visibleIds: Flow<List<String>>) {
    visibleIds
        .distinctUntilChanged()
        .collectLatest { ids ->
            delay(DWELL_MS)
            if (ids.isNotEmpty()) Tracker.impression(ids, surface)
        }
}
