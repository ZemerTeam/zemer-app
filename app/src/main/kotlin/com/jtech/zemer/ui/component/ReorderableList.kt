package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The one drag-handle glyph for every reorderable row; the caller passes the `draggableHandle` / `longPressDraggableHandle` modifier. */
@Composable
fun ReorderDragHandle(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.drag_handle),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(24.dp),
    )
}

data class ReorderableEntry(val id: String, val name: String)

/**
 * A plain drag-to-reorder list of named entries (the lyrics provider priority dialog). Every row is the shared
 * `PreferenceEntry` (D-pad focusable, focus ring) with the shared [ReorderDragHandle]; the new order is reported
 * once a drag ends. Rows that also carry a switch (Android Auto sections) compose the same pieces inline.
 */
@Composable
fun ReorderableList(
    items: MutableList<ReorderableEntry>,
    onItemsReordered: (List<ReorderableEntry>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    var hasDragged by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val moved = items.removeAt(from.index)
        items.add(to.index, moved)
        hasDragged = true
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasDragged) {
            onItemsReordered(items.toList())
            hasDragged = false
        }
    }

    LazyColumn(state = lazyListState, modifier = modifier) {
        itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
            ReorderableItem(state = reorderableState, key = item.id) {
                PreferenceEntry(
                    title = { Text(item.name) },
                    trailingContent = { ReorderDragHandle(Modifier.draggableHandle()) },
                    contentPadding = PreferenceEntryDefaults.compactContentPadding,
                )
            }
        }
    }
}
