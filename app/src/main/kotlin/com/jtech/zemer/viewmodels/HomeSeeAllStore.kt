package com.jtech.zemer.viewmodels

import androidx.annotation.StringRes
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.LocalItem
import com.jtech.zemer.db.entities.Song
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which Home row a "See all" screen is showing. The [slug] is the nav argument (stable, route-safe)
 * and [titleRes] reuses the Home section's own title string, so the See-all header always matches the
 * row it opened from.
 */
enum class HomeSeeAllRow(
    val slug: String,
    @StringRes val titleRes: Int,
    // The blocked-video relabel ("... video songs"), when the row has one. ONE definition consumed by
    // BOTH the Home row title and the See-all page title, so the relabel can never split between them.
    @StringRes val blockedTitleRes: Int? = null,
) {
    FEATURED_ALBUMS("featured-albums", R.string.featured_albums),
    FEATURED_ARTISTS("featured-artists", R.string.featured_artists),
    FEATURED_VIDEOS("featured-videos", R.string.featured_videos, R.string.featured_video_songs),
    FEATURED_PLAYLISTS("featured-playlists", R.string.featured_playlists),
    // Operator-featured user-shared playlists; ids are SHARE ids (user_playlist/<id> routing).
    USER_PLAYLISTS("user-playlists", R.string.zemer_user_playlists),
    KEEP_LISTENING("keep-listening", R.string.keep_listening),
    FORGOTTEN_FAVORITES("forgotten-favorites", R.string.forgotten_favorites),
    QUICK_PICKS("quick-picks", R.string.quick_picks),
    // Podcasts-tab ranked rows (backed by [PodcastHomeSeeAllStore], not the music [HomeSeeAllStore]).
    FEATURED_PODCASTS("featured-podcasts", R.string.featured_podcasts),
    TOP_PODCASTS("top-podcasts", R.string.top_podcasts),
    TRENDING_EPISODES("trending-episodes", R.string.trending_episodes),
    SUBSCRIBED_CHANNELS("subscribed-channels", R.string.subscribed_channels),
    // Videos-tab ranked rows (backed by [VideoHomeSeeAllStore]).
    TRENDING_VIDEOS("trending-videos", R.string.trending_videos, R.string.trending_video_songs),
    NEW_VIDEOS("new-videos", R.string.new_videos, R.string.new_video_songs),
    TOP_VIDEO_ARTISTS("top-video-artists", R.string.top_video_artists),
    ;

    /** The row title under the current video flag - the relabel rows resolve here, everything else passes through. */
    @StringRes
    fun displayTitleRes(blockVideos: Boolean): Int =
        if (blockVideos) blockedTitleRes ?: titleRes else titleRes

    companion object {
        fun fromSlug(slug: String?): HomeSeeAllRow? = entries.firstOrNull { it.slug == slug }
    }
}

/** The full (un-capped, un-rotated) Home rows, already content-filtered, that back the See-all screens. */
data class HomeSeeAllData(
    val featuredAlbums: List<AlbumItem> = emptyList(),
    val featuredArtists: List<ArtistItem> = emptyList(),
    val featuredVideos: List<SongItem> = emptyList(),
    val featuredPlaylists: List<PlaylistItem> = emptyList(),
    // Operator-featured user-shared playlists; ids are SHARE ids (user_playlist/<id> routing).
    val featuredUserPlaylists: List<PlaylistItem> = emptyList(),
    val keepListening: List<LocalItem> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val quickPicks: List<Song> = emptyList(),
    // True when [featuredAlbums] is Zemer-sourced (telemetry) rather than the InnerTube scrape fallback,
    // so the See-all opens those albums through the server album route — same rule as the Home row.
    val featuredAlbumsAreZemer: Boolean = false,
    // Same, for [featuredPlaylists]: Zemer community playlists open via the server /playlist route.
    val featuredPlaylistsAreZemer: Boolean = false,
)

/**
 * A process-wide snapshot of the full Home rows, published by [HomeViewModel] on every load and read by
 * the See-all screens. Same pattern as the Latest-Releases store: the See-all screen shows exactly what
 * Home already computed and filtered (no re-fetch, no re-filter, so the two can never disagree), just
 * un-capped and as a vertical page. Empty until Home has loaded once — a See-all opened before then shows
 * an empty page, which cannot happen in practice because Home loads on app start.
 */
object HomeSeeAllStore {
    private val _data = MutableStateFlow(HomeSeeAllData())
    val data: StateFlow<HomeSeeAllData> = _data.asStateFlow()

    fun publish(data: HomeSeeAllData) {
        _data.value = data
    }
}

/** The full Podcasts-tab ranked rows that back their See-all screens (see [PodcastHomeSeeAllStore]). */
data class PodcastHomeSeeAllData(
    val featured: List<PodcastItem> = emptyList(),
    val topPodcasts: List<PodcastItem> = emptyList(),
    val trendingEpisodes: List<EpisodeItem> = emptyList(),
    val subscribedChannels: List<PodcastItem> = emptyList(),
)

/**
 * The podcast twin of [HomeSeeAllStore], kept SEPARATE from the music store. Two DIFFERENT VMs publish
 * into it — [PodcastHomeRowsViewModel] (the ranked discovery rows) and PodcastSubscriptionsHomeViewModel
 * (the subscribed-channels row) — so each writes only its OWN fields via a copy-update, never clobbering
 * the other's. The See-all screen reads it so what it shows is exactly the row.
 */
object PodcastHomeSeeAllStore {
    private val _data = MutableStateFlow(PodcastHomeSeeAllData())
    val data: StateFlow<PodcastHomeSeeAllData> = _data.asStateFlow()

    fun publishRows(featured: List<PodcastItem>, topPodcasts: List<PodcastItem>, trendingEpisodes: List<EpisodeItem>) {
        _data.update { it.copy(featured = featured, topPodcasts = topPodcasts, trendingEpisodes = trendingEpisodes) }
    }

    fun publishSubscribedChannels(subscribedChannels: List<PodcastItem>) {
        _data.update { it.copy(subscribedChannels = subscribedChannels) }
    }
}

data class VideoHomeSeeAllData(
    val trending: List<SongItem> = emptyList(),
    val newVideos: List<SongItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
)

/** The Videos-tab twin of [PodcastHomeSeeAllStore]; [VideoHomeRowsViewModel] publishes each load. */
object VideoHomeSeeAllStore {
    private val _data = MutableStateFlow(VideoHomeSeeAllData())
    val data: StateFlow<VideoHomeSeeAllData> = _data.asStateFlow()

    fun publishRows(trending: List<SongItem>, newVideos: List<SongItem>, artists: List<ArtistItem>) {
        _data.update { it.copy(trending = trending, newVideos = newVideos, artists = artists) }
    }
}
