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
 * Reporting is keyed off the list's own item KEYS rather than visible indices. Indices identify
 * nothing in a heterogeneous list — headers, chips and section titles share the index space with
 * results — and an index-based variant would additionally require the caller to pass a list
 * identical to the one it renders, an invariant nothing can enforce and which fails silently by
 * reporting the wrong videoIds under the right surface. [idOfKey] maps a key to a videoId and
 * returns null for everything that isn't a song.
 *
 * Dedup, chunking, videoId filtering and the drop-under-backoff policy all live in
 * [Tracker.impression]; this layer only decides what is on screen.
 */
private const val DWELL_MS = 300L

/** Rows a fling passes through are never reported — only what the user settled on. */
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

/** Grid variant — same definition, same dwell. */
@Composable
fun TrackImpressionsByKey(
    surface: String,
    state: LazyGridState,
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
