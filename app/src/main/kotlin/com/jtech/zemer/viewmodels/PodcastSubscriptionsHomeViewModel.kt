package com.jtech.zemer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.utils.NewEpisodesFeed
import com.jtech.zemer.utils.PodcastLibrarySources
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Home Podcasts-tab "New Episodes" + "Subscribed Channels" rows (isolated, fail-soft, like the other
 * home-row VMs). Both are driven by the user's LOCAL subscriptions, so they work identically for an
 * anonymous session and a Google login — no account gate:
 * - [newEpisodes] is the server `/podcasts/new-episodes` feed scoped CLIENT-SIDE to locally-subscribed
 *   shows ([NewEpisodesFeed] / [PodcastLibrarySources.whitelistedNewEpisodes]); empty when nothing is
 *   subscribed, so the row simply hides. A fetch failure keeps it empty.
 * - [subscribedChannels] merges channels the user bookmarked from a channel page (`isPodcastChannel`
 *   ArtistEntity) with those derivable from locally-subscribed shows, gated by the channel whitelist —
 *   the same local sources the Library Channels tab uses (never the pooled-account API read).
 */
@HiltViewModel
class PodcastSubscriptionsHomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    zemerRepository: ZemerSearchRepository,
) : ViewModel() {
    private val newEpisodesFeed = NewEpisodesFeed(zemerRepository, context, database)
    val newEpisodes: StateFlow<List<SongItem>> = newEpisodesFeed.episodes

    val subscribedChannels: StateFlow<List<PodcastChannel>> =
        combine(
            PodcastLibrarySources.whitelistedSubscribedPodcasts(database),
            database.bookmarkedPodcastChannels(),
        ) { subscribedShows, bookmarked ->
            val bookmarkedChannels = bookmarked.map {
                PodcastChannel(id = it.id, name = it.name, thumbnailUrl = it.thumbnailUrl)
            }
            val showChannels = subscribedShows.mapNotNull { p ->
                p.channelId?.takeIf { it.isNotBlank() }?.let {
                    PodcastChannel(id = it, name = p.author ?: p.title, thumbnailUrl = p.thumbnailUrl)
                }
            }
            (bookmarkedChannels + showChannels)
                .filter { PodcastLibrarySources.podcastChannelAllowed(it.id) }
                .distinctBy { it.id }
        }
            // Publish the full list (as SQUARE PodcastItem cards) so the row's "See all" grid shows exactly
            // these channels; channel-first routing opens each channel page.
            .onEach { channels ->
                PodcastHomeSeeAllStore.publishSubscribedChannels(
                    channels.map {
                        PodcastItem(
                            id = it.id,
                            title = it.name,
                            author = null,
                            episodeCountText = null,
                            thumbnail = it.thumbnailUrl,
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            channelId = it.id,
                        )
                    },
                )
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun fetchNewEpisodes() = newEpisodesFeed.fetch(viewModelScope)
}
