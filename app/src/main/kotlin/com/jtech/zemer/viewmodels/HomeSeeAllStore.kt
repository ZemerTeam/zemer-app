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

/**
 * Which Home row a "See all" screen is showing. The [slug] is the nav argument (stable, route-safe)
 * and [titleRes] reuses the Home section's own title string, so the See-all header always matches the
 * row it opened from.
 */
enum class HomeSeeAllRow(val slug: String, @StringRes val titleRes: Int) {
    FEATURED_ALBUMS("featured-albums", R.string.featured_albums),
    FEATURED_ARTISTS("featured-artists", R.string.featured_artists),
    FEATURED_VIDEOS("featured-videos", R.string.featured_videos),
    FEATURED_PLAYLISTS("featured-playlists", R.string.featured_playlists),
    KEEP_LISTENING("keep-listening", R.string.keep_listening),
    FORGOTTEN_FAVORITES("forgotten-favorites", R.string.forgotten_favorites),
    QUICK_PICKS("quick-picks", R.string.quick_picks),
    // Podcasts-tab ranked rows (backed by [PodcastHomeSeeAllStore], not the music [HomeSeeAllStore]).
    TOP_PODCASTS("top-podcasts", R.string.top_podcasts),
    TRENDING_EPISODES("trending-episodes", R.string.trending_episodes),
    NEW_SHOWS("new-shows", R.string.new_shows),
    ;

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
    val topPodcasts: List<PodcastItem> = emptyList(),
    val trendingEpisodes: List<EpisodeItem> = emptyList(),
    val newShows: List<PodcastItem> = emptyList(),
)

/**
 * The podcast twin of [HomeSeeAllStore], kept SEPARATE so the two publishers (HomeViewModel for music,
 * [PodcastHomeRowsViewModel] for podcasts) never clobber each other's snapshot. Published on every
 * successful `/podcast-home-rows` load; the See-all screen reads it so what it shows is exactly the row.
 */
object PodcastHomeSeeAllStore {
    private val _data = MutableStateFlow(PodcastHomeSeeAllData())
    val data: StateFlow<PodcastHomeSeeAllData> = _data.asStateFlow()

    fun publish(data: PodcastHomeSeeAllData) {
        _data.value = data
    }
}
