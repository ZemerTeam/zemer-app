package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.innertube.models.SearchSuggestions
import com.jtech.zemer.utils.BlockedIdsCache
import com.jtech.zemer.utils.ContentFilterState

/**
 * Adapts a [ZemerSearchResponse] into the exact `YTItem`/page types the existing search UI already
 * renders, so the screens, rows, playback and navigation are all reused unchanged:
 *
 * - songs & videos → [SongItem] (thumbnail derived from the videoId; `endpoint` left null, which the
 *   results screen already handles by playing `WatchEndpoint(videoId = id)`).
 * - artists → [ArtistItem], albums + singles → [AlbumItem], playlists & community → [PlaylistItem]
 *   (the artist-owned `playlists` back the Featured chip, the `community` list backs the Community chip).
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

    fun ZemerPlaylist.toPlaylistItem(formatSongCount: (Int) -> String?): PlaylistItem =
        PlaylistItem(
            id = id,
            title = title,
            author = if (artist.isBlank()) null else Artist(name = artist, id = null),
            // e.g. "12 songs"; omitted when the server sends no/zero count. The row renders this after a
            // bullet next to the curator (Items.kt), and the count is regex-read elsewhere, so the
            // localized "N songs" string keeps both working.
            songCountText = songCount?.takeIf { it > 0 }?.let(formatSongCount),
            thumbnail = thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

    // Drop items hidden by the server-listed id overrides, gated on the live content-filter config (a
    // `female` override only hides for users filtering out female). This is surgical (a specific known
    // id), NOT the artist-membership whitelist the app deliberately never runs over raw Zemer results —
    // so it is safe here and gives the override coverage on the Zemer engine too. See BlockedIdsCache.
    private fun <T : YTItem> List<T>.dropBlocked(): List<T> {
        val config = ContentFilterState.current
        return filterNot { BlockedIdsCache.isBlocked(it.id, config) }
    }

    // Each helper drops rows missing their id (the server should never send those, but one sparse row
    // must not crash navigation) and de-dupes by id, since the id-keyed LazyColumns reject duplicates.
    private fun songItems(tracks: List<ZemerTrack>, hideExplicit: Boolean): List<SongItem> =
        tracks.filter { it.videoId.isNotBlank() }
            .map { it.toSongItem() }
            .filterExplicit(hideExplicit)
            .distinctBy { it.id }
            .dropBlocked()

    /**
     * Songs + videos as one list (videos are [SongItem]s). The summary "Songs" section and the
     * `FILTER_SONG` chip BOTH use this, so drilling into "Songs" from the summary returns exactly what
     * the section showed — videos folded into the preview never disappear (or yield "No results" on a
     * video-only query) on tap. The dedicated Videos chip still narrows to videos only.
     */
    private fun songAndVideoItems(resp: ZemerSearchResponse, hideExplicit: Boolean): List<SongItem> =
        (songItems(resp.categories.songs, hideExplicit) + songItems(resp.categories.videos, hideExplicit))
            .distinctBy { it.id }

    private fun artistItems(resp: ZemerSearchResponse): List<ArtistItem> =
        resp.categories.artists.filter { it.id.isNotBlank() }.map { it.toArtistItem() }.distinctBy { it.id }.dropBlocked()

    /** Albums + singles together, in that order — both navigate via the FILTER_ALBUM chip. */
    private fun albumItems(resp: ZemerSearchResponse): List<AlbumItem> =
        (resp.categories.albums + resp.categories.singles)
            .filter { it.id.isNotBlank() }
            .map { it.toAlbumItem() }
            .distinctBy { it.id }
            .dropBlocked()

    /** Shared playlist adaptation — used for both the artist-owned `playlists` and the `community` lists. */
    private fun playlistItems(playlists: List<ZemerPlaylist>, formatSongCount: (Int) -> String?): List<PlaylistItem> =
        playlists.filter { it.id.isNotBlank() }.map { it.toPlaylistItem(formatSongCount) }.distinctBy { it.id }.dropBlocked()

    /**
     * A Zemer `/playlist` response as playable [SongItem]s. The server already whitelist-scoped and
     * content-filtered the tracks, so — like every other Zemer surface — the local artist whitelist is
     * NOT re-run here (re-filtering would re-introduce the card-vs-open count mismatch this endpoint
     * fixes); only `hideExplicit` and the surgical id-overrides ([dropBlocked]) are applied.
     */
    fun ZemerPlaylistResponse.toSongItems(hideExplicit: Boolean): List<SongItem> =
        songItems(tracks, hideExplicit)

    /**
     * A Zemer `/album` response as the [AlbumPage] the album screen + DB persist flow already consume,
     * so the Zemer path reuses that whole pipeline unchanged. Like every Zemer surface the tracks are
     * whitelist-scoped server-side, so only the surgical id-overrides ([dropBlocked]) run here
     * (hide-explicit is applied by the album screen itself, over the persisted rows). [playlistId] is
     * the search card's OP playlist id — the server header carries none — falling back to the browseId
     * (whose only consumer then is the disabled automix).
     */
    fun ZemerAlbumResponse.toAlbumPage(playlistId: String?): AlbumPage {
        val albumItem = AlbumItem(
            browseId = album.id,
            playlistId = playlistId ?: album.id,
            title = album.title,
            artists = if (album.artist.isBlank()) null else listOf(Artist(name = album.artist, id = null)),
            year = album.year,
            thumbnail = album.thumbnail.orEmpty(),
        )
        val songs = tracks
            .filter { it.videoId.isNotBlank() }
            // sortedBy is stable, so untagged tracks keep server order (after the numbered ones).
            .sortedBy { it.trackNumber ?: Int.MAX_VALUE }
            .map { track ->
                track.toSongItem().copy(
                    album = Album(name = albumItem.title, id = albumItem.browseId),
                    duration = track.durationSec,
                    // Prefer the square album art over the derived (letterboxed) video frame.
                    thumbnail = album.thumbnail ?: thumbnailFor(track.videoId),
                )
            }
            .distinctBy { it.id }
            .dropBlocked()
        return AlbumPage(album = albumItem, songs = songs)
    }

    /**
     * The grouped summary view (`filter == null`), matching the YouTube summary's shape exactly
     * (`YouTube.searchSummary`): items grouped by type into the same sections, in the same order, with
     * the same hardcoded English titles, so toggling engines never changes the summary's headers or
     * layout. Videos are folded into the Songs section (they are [SongItem]s — exactly how YouTube
     * groups them); there is no separate Videos section. The "Playlists" section shows the community
     * playlists only — its header drills into the Community chip, so previewing community here keeps
     * tap-through consistent (artist-owned/featured playlists are reached via the Featured chip).
     * Empty sections are omitted. (No "Top result" card — the Zemer server does not return one.)
     */
    fun summaryPage(
        resp: ZemerSearchResponse,
        hideExplicit: Boolean,
        formatSongCount: (Int) -> String? = { null },
    ): SearchSummaryPage {
        val songsAndVideos = songAndVideoItems(resp, hideExplicit)
        val playlists = playlistItems(resp.categories.community, formatSongCount)
        // Each section is a compact preview; the merged sections (songs+videos, albums+singles) would
        // otherwise run up to ~16 rows. The full per-category list is one tap away on the chip.
        fun MutableList<SearchSummary>.section(title: String, items: List<YTItem>) =
            items.take(SUMMARY_SECTION_LIMIT).takeIf { it.isNotEmpty() }?.let { add(SearchSummary(title, it)) }
        val summaries = buildList {
            section(TITLE_ALBUMS, albumItems(resp))
            section(TITLE_SONGS, songsAndVideos)
            section(TITLE_ARTISTS, artistItems(resp))
            section(TITLE_PLAYLISTS, playlists)
        }
        return SearchSummaryPage(summaries = summaries)
    }

    /** A single chip's results. Zemer has no pagination, so `continuation` is always null. */
    fun filtered(
        resp: ZemerSearchResponse,
        filter: SearchFilter,
        hideExplicit: Boolean,
        formatSongCount: (Int) -> String? = { null },
    ): SearchResult {
        val items: List<YTItem> = when (filter.value) {
            // Songs chip returns songs + videos (the summary "Songs" section folds them together, so the
            // drill-in must too); the Videos chip narrows to videos only.
            SearchFilter.FILTER_SONG.value -> songAndVideoItems(resp, hideExplicit)
            SearchFilter.FILTER_VIDEO.value -> songItems(resp.categories.videos, hideExplicit)
            SearchFilter.FILTER_ARTIST.value -> artistItems(resp)
            SearchFilter.FILTER_ALBUM.value -> albumItems(resp)
            SearchFilter.FILTER_COMMUNITY_PLAYLIST.value -> playlistItems(resp.categories.community, formatSongCount)
            SearchFilter.FILTER_FEATURED_PLAYLIST.value -> playlistItems(resp.categories.playlists, formatSongCount)
            else -> emptyList()
        }
        return SearchResult(items = items, continuation = null)
    }

    /**
     * As-you-type dropdown — the two-part layout Metrolist uses: tappable text **completions**
     * (`queries`) on top, then full live result rows (`recommendedItems`) across ALL categories in the
     * same order as the summary screen. Completions are Zemer-native: artist names first (the most
     * useful "search everything by…" completion and the one that absorbs Hebrew/romanization fuzz),
     * then a few song titles to fill — deduped case-insensitively and capped. The combined rows are
     * de-duped by id (a videoId can appear in both songs and videos) so the id-keyed list can't crash.
     */
    fun suggestions(
        resp: ZemerSearchResponse,
        hideExplicit: Boolean,
        formatSongCount: (Int) -> String? = { null },
    ): SearchSuggestions {
        val items: List<YTItem> =
            (songItems(resp.categories.songs, hideExplicit) +
                artistItems(resp) +
                albumItems(resp) +
                songItems(resp.categories.videos, hideExplicit) +
                playlistItems(resp.categories.playlists, formatSongCount) +
                playlistItems(resp.categories.community, formatSongCount))
                .distinctBy { it.id }

        // Drop explicit-flagged songs from the completion strings too (not just the result rows) so an
        // explicit title can't be offered as a tappable suggestion when Hide explicit is on.
        val completions: List<String> =
            (resp.categories.artists.map { it.name } +
                resp.categories.songs.filter { !hideExplicit || !it.explicit }.map { it.title })
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .take(MAX_QUERY_SUGGESTIONS)

        return SearchSuggestions(queries = completions, recommendedItems = items)
    }

    private const val MAX_QUERY_SUGGESTIONS = 5

    /** Per-section preview cap on the grouped summary, so a merged section isn't a long scroll. */
    private const val SUMMARY_SECTION_LIMIT = 8

    // Verbatim match of the YouTube summary section titles/order (YouTube.searchSummary hardcodes
    // these English literals too), so the summary looks identical whichever engine is selected.
    private const val TITLE_ALBUMS = "Albums"
    private const val TITLE_SONGS = "Songs"
    private const val TITLE_ARTISTS = "Artists"
    private const val TITLE_PLAYLISTS = "Playlists"
}
