package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.Material3MenuItemData
import com.jtech.zemer.ui.utils.albumRoute
import com.jtech.zemer.ui.utils.podcastRoute

/**
 * Where the "view collection" row navigates: an EPISODE opens its owning podcast SHOW
 * (`online_podcast/<MPSP>`), a regular song its ALBUM (`album/<id>`). Pure so the decision is unit-tested
 * (see ViewCollectionMenuItemTest); null for a blank id (the caller renders no row / the nav is skipped).
 */
internal fun viewCollectionRoute(isEpisode: Boolean, collectionId: String?): String? =
    if (isEpisode) podcastRoute(collectionId) else albumRoute(collectionId)

/**
 * The song-menu row that opens the item's owning collection, shared by the three song menus (player /
 * local / online) so they can't drift or mislabel an episode again.
 *
 * An EPISODE's `album`/`albumId` is its owning podcast SHOW (an `MPSP…` id, NOT an album), so the row
 * becomes **"View podcast"** → the show screen; a regular song keeps **"View album"** → the album screen.
 * Returns null when there is no collection id (the caller adds nothing). [beforeNavigate] runs before the
 * navigation for a caller-specific side effect (e.g. the player collapsing its sheet).
 */
fun viewCollectionMenuItem(
    isEpisode: Boolean,
    collectionId: String?,
    navController: NavController,
    onDismiss: () -> Unit,
    beforeNavigate: () -> Unit = {},
): Material3MenuItemData? {
    if (collectionId.isNullOrBlank()) return null
    return Material3MenuItemData(
        icon = {
            Icon(
                painterResource(if (isEpisode) R.drawable.podcast else R.drawable.album),
                null,
                Modifier.size(24.dp),
            )
        },
        title = { Text(stringResource(if (isEpisode) R.string.view_podcast else R.string.view_album)) },
        onClick = {
            beforeNavigate()
            viewCollectionRoute(isEpisode, collectionId)?.let { navController.navigate(it) }
            onDismiss()
        },
    )
}
