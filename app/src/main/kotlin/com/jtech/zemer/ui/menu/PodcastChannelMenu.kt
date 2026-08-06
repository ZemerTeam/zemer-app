package com.jtech.zemer.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jtech.zemer.R
import com.jtech.zemer.constants.ThumbnailCornerRadius
import com.jtech.zemer.db.entities.PodcastWhitelistEntity
import com.jtech.zemer.extensions.shareText
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.jtech.zemer.ui.component.NewAction
import com.jtech.zemer.ui.component.NewActionGrid
import com.jtech.zemer.ui.utils.whitelistedPodcastRoute

/**
 * The long-press menu for a whitelisted podcast CHANNEL (the browse grid/list in
 * [com.jtech.zemer.ui.screens.WhitelistedPodcastsScreen]). Built entirely from the shared menu pieces —
 * the [ArtistMenu] header shape, [NewActionGrid]/[NewAction], [shareText] and [whitelistedPodcastRoute]
 * — so it can't drift from the rest of the app. A channel has no local library/song state (unlike an
 * [ArtistMenu]'s [com.jtech.zemer.db.entities.Artist]); the actions are the two that apply to a browse
 * entry: open the channel page and share it. Subscribe lives on the channel page itself (account-gated).
 */
@Composable
fun PodcastChannelMenu(
    podcast: PodcastWhitelistEntity,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Header — the same roomy avatar + bold name row ArtistMenu uses (rounded, not circular, matching the
    // podcast rows' square art).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(podcast.thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = podcast.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        contentPadding = PaddingValues(
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.podcast),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.view_channel),
                            onClick = {
                                onDismiss()
                                whitelistedPodcastRoute(null, podcast.channelId)?.let(navController::navigate)
                            },
                        ),
                    )
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.share),
                            onClick = {
                                onDismiss()
                                Tracker.action(TrackingActionKind.SHARE, podcast.channelId)
                                context.shareText("https://music.zemer.io/channel/${podcast.channelId}")
                            },
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
    }
}
