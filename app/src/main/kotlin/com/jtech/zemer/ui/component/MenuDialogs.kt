package com.jtech.zemer.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListItemHeight
import com.jtech.zemer.constants.ListThumbnailSize

/** One selectable artist row in [SelectArtistDialog]. */
data class ArtistChoice(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

/**
 * "Which artist?" picker shown by the song / album / player menus when an item has more than one
 * artist. Renders a circular avatar when a thumbnail is available, otherwise just the name. The row
 * carries the standard [focusBorder] D-pad treatment. Unifies five hand-rolled copies (which were
 * also the source of the 18.sp R8 font-size violations).
 *
 * On click it invokes [onArtistClick] with the artist id and then [onDismiss]; the caller's
 * [onArtistClick] performs the navigation (and any player-sheet collapse / parent-menu dismissal).
 */
@Composable
fun SelectArtistDialog(
    artists: List<ArtistChoice>,
    onDismiss: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    ListDialog(onDismiss = onDismiss) {
        items(artists, key = { it.id }) { artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillParentMaxWidth()
                    .height(ListItemHeight)
                    .focusBorder()
                    .clickable {
                        onArtistClick(artist.id)
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp),
            ) {
                if (!artist.thumbnailUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                }
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * "Already in playlist" notice shown after an add-to-playlist attempt where some songs were already
 * present. Provides the shared dialog frame + dismissable header; the caller supplies the song rows
 * via [songs] (their item types differ — single Song, a not-added list, MediaMetadata, etc.).
 * Unifies four hand-rolled copies.
 */
@Composable
fun AlreadyInPlaylistDialog(
    onDismiss: () -> Unit,
    songs: LazyListScope.() -> Unit,
) {
    ListDialog(onDismiss = onDismiss) {
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                leadingContent = {
                    Image(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.size(ListThumbnailSize),
                    )
                },
                modifier = Modifier.clickable { onDismiss() },
            )
        }
        songs()
    }
}
