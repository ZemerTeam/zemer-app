package com.jtech.zemer.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.jtech.zemer.R
import com.jtech.zemer.ui.utils.ItemWrapper

/**
 * THE select-mode top-bar action cluster, shared by every screen that supports multi-select (library
 * songs/videos, the playlist screens, album, etc.) so the close / count / select-all / overflow row
 * is identical everywhere and can't drift. Drop it inside the screen's `TopAppBar` title `Row` for the
 * `selection == true` branch.
 *
 * The screen keeps owning its selection state ([wrapped] is its `List<ItemWrapper<T>>`); this only
 * renders the actions. [countLabel] formats the selected count (e.g. `n_song` vs `n_video` plural),
 * [onExit] leaves selection mode, and [onMore] opens the screen's selection menu with the chosen items.
 */
@Composable
fun <T> RowScope.SelectionTopActions(
    wrapped: List<ItemWrapper<T>>,
    countLabel: @Composable (count: Int) -> String,
    onExit: () -> Unit,
    onMore: () -> Unit,
) {
    val count = wrapped.count { it.isSelected }
    val allSelected = count == wrapped.size

    IconButton(onClick = onExit) {
        Icon(painterResource(R.drawable.close), contentDescription = null)
    }
    Text(
        text = countLabel(count),
        modifier = Modifier.weight(1f),
    )
    IconButton(
        onClick = { wrapped.forEach { it.isSelected = !allSelected } },
    ) {
        Icon(
            painterResource(if (allSelected) R.drawable.deselect else R.drawable.select_all),
            contentDescription = null,
        )
    }
    IconButton(onClick = onMore) {
        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
    }
}

/**
 * The select-all/deselect + overflow pair for the `actions` slot of a `TopAppBar`-based select mode
 * (the playlist screens, downloaded videos, …). Those screens keep their own `navigationIcon` (close,
 * with per-screen long-press/search behaviour) and `title` (the count); only this action pair is
 * identical, so it lives here. [onMore] opens the screen's selection menu with the chosen items.
 */
@Composable
fun <T> RowScope.SelectionActions(
    wrapped: List<ItemWrapper<T>>,
    onMore: () -> Unit,
) {
    val count = wrapped.count { it.isSelected }
    val allSelected = count == wrapped.size
    IconButton(
        onClick = { wrapped.forEach { it.isSelected = !allSelected } },
    ) {
        Icon(
            painterResource(if (allSelected) R.drawable.deselect else R.drawable.select_all),
            contentDescription = null,
        )
    }
    IconButton(onClick = onMore) {
        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
    }
}
