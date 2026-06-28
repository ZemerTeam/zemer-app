package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.innertube.models.SearchSuggestions

/**
 * Localized section headings for the Zemer summary view. Supplied by the caller (the repository pulls
 * them from `context.getString(...)`) so this mapper stays pure and unit-testable without an Android
 * runtime. Albums and singles are shown under one "Albums" heading; community/featured playlists both
 * map to the one Zemer playlist category.
 */
data class SectionTitles(
    val songs: String,
    val videos: String,
    val albums: String,
    val artists: String,
    val playlists: String,
)

/**
 * Adapts a [ZemerSearchResponse] into the exact `YTItem`/page types the existing search UI already
 * renders, so the screens, rows, playback and navigation are all reused unchanged:
 *
 * - songs & videos → [SongItem] (thumbnail derived from the videoId; `endpoint` left null, which the
 *   results screen already handles by playing `WatchEndpoint(videoId = id)`).
 * - artists → [ArtistItem], albums + singles → [AlbumItem], playlists → [PlaylistItem].
 *
 * Zemer results are already whitelist-scoped server-side, so the local whitelist filter is NOT applied
 * here; only `hideExplicit` is honored (on the song/video lists — the other types are never explicit).
 */
object ZemerResultMapper {

    /** YouTube serves the video thumbnail for any videoId; `String.resize` no-ops on this host. */
    fun thumbnailFor(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    fun ZemerTrack.toSongItem(): SongItem =
        SongItem(
            id = videoId,
            title = title,
            artists = listOf(Artist(name = artist, id = null)),
            album = null,
            duration = null,
            thumbnail = thumbnailFor(videoId),
            explicit = explicit,
        )

    fun ZemerArtist.toArtistItem(): ArtistItem =
        ArtistItem(
            id = id,
            title = name,
            thumbnail = thumbnail,
            channelId = null,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

    fun ZemerAlbum.toAlbumItem(): AlbumItem =
        AlbumItem(
            browseId = id,
            playlistId = playlistId ?: id,
            title = title,
            artists = if (artist.isBlank()) null else listOf(Artist(name = artist, id = null)),
            year = year,
            thumbnail = thumbnail.orEmpty(),
        )

    fun ZemerPlaylist.toPlaylistItem(): PlaylistItem =
        PlaylistItem(
            id = id,
            title = title,
            author = if (artist.isBlank()) null else Artist(name = artist, id = null),
            songCountText = null,
            thumbnail = thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

    private fun songItems(tracks: List<ZemerTrack>, hideExplicit: Boolean): List<SongItem> =
        tracks.map { it.toSongItem() }.filterExplicit(hideExplicit)

    /** Albums + singles together, in that order — both navigate via the FILTER_ALBUM chip. */
    private fun albumItems(resp: ZemerSearchResponse): List<AlbumItem> =
        (resp.categories.albums + resp.categories.singles).map { it.toAlbumItem() }

    /** The grouped summary view (the `filter == null` screen). Empty sections are omitted. */
    fun summaryPage(
        resp: ZemerSearchResponse,
        titles: SectionTitles,
        hideExplicit: Boolean,
    ): SearchSummaryPage {
        val summaries = buildList {
            songItems(resp.categories.songs, hideExplicit)
                .takeIf { it.isNotEmpty() }?.let { add(SearchSummary(titles.songs, it)) }
            resp.categories.artists.map { it.toArtistItem() }
                .takeIf { it.isNotEmpty() }?.let { add(SearchSummary(titles.artists, it)) }
            albumItems(resp)
                .takeIf { it.isNotEmpty() }?.let { add(SearchSummary(titles.albums, it)) }
            songItems(resp.categories.videos, hideExplicit)
                .takeIf { it.isNotEmpty() }?.let { add(SearchSummary(titles.videos, it)) }
            resp.categories.playlists.map { it.toPlaylistItem() }
                .takeIf { it.isNotEmpty() }?.let { add(SearchSummary(titles.playlists, it)) }
        }
        return SearchSummaryPage(summaries = summaries)
    }

    /** A single chip's results. Zemer has no pagination, so `continuation` is always null. */
    fun filtered(
        resp: ZemerSearchResponse,
        filter: SearchFilter,
        hideExplicit: Boolean,
    ): SearchResult {
        val items: List<YTItem> = when (filter.value) {
            SearchFilter.FILTER_SONG.value -> songItems(resp.categories.songs, hideExplicit)
            SearchFilter.FILTER_VIDEO.value -> songItems(resp.categories.videos, hideExplicit)
            SearchFilter.FILTER_ARTIST.value -> resp.categories.artists.map { it.toArtistItem() }
            SearchFilter.FILTER_ALBUM.value -> albumItems(resp)
            SearchFilter.FILTER_COMMUNITY_PLAYLIST.value,
            SearchFilter.FILTER_FEATURED_PLAYLIST.value,
            -> resp.categories.playlists.map { it.toPlaylistItem() }
            else -> emptyList()
        }
        return SearchResult(items = items, continuation = null)
    }

    /**
     * As-you-type dropdown — the two-part layout Metrolist uses: tappable text **completions**
     * (`queries`) on top, then full live result rows (`recommendedItems`) across ALL categories in the
     * same order as the summary screen. Completions are Zemer-native: artist names first (the most
     * useful "search everything by…" completion and the one that absorbs Hebrew/romanization fuzz),
     * then a few song titles to fill — deduped case-insensitively and capped.
     */
    fun suggestions(resp: ZemerSearchResponse, hideExplicit: Boolean): SearchSuggestions {
        val items: List<YTItem> =
            songItems(resp.categories.songs, hideExplicit) +
                resp.categories.artists.map { it.toArtistItem() } +
                albumItems(resp) +
                songItems(resp.categories.videos, hideExplicit) +
                resp.categories.playlists.map { it.toPlaylistItem() }

        val completions: List<String> =
            (resp.categories.artists.map { it.name } + resp.categories.songs.map { it.title })
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(MAX_QUERY_SUGGESTIONS)

        return SearchSuggestions(queries = completions, recommendedItems = items)
    }

    private const val MAX_QUERY_SUGGESTIONS = 5
}
