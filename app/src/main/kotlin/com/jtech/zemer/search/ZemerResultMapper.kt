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
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
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

    /**
     * YouTube serves the video thumbnail for any videoId. `String.resize` no-ops on this host (it
     * only rewrites googleusercontent FIFE params), so the variant IS the sizing decision — and it
     * is NOT a list-only decision: the same URL becomes the player, lockscreen and notification
     * artwork through `SongItem.toMediaMetadata()`, which asks for 544x544.
     *
     * `hqdefault` (480x360) is kept for that reason. `mqdefault` (320x180) crops to a square
     * without the letterbox bars `hqdefault` carries, which looks better in a 48dp row — but it
     * leaves 180px behind a 544px request, so the now-playing art visibly degrades. The row is the
     * cheaper thing to compromise.
     *
     * The real fix is the server sending real album art for playlist tracks the way it already does
     * for `/album`; until then the bars stay. Do NOT "fix" this by switching variants again without
     * checking the player surface.
     */
    fun thumbnailFor(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    fun ZemerTrack.toSongItem(): SongItem =
        SongItem(
            id = videoId,
            // `artistId` is present on /home-rows video cards (null elsewhere) so the artist carries a
            // real channel id — required for the home one-per-artist dedup + female/israeli check.
            artists = listOf(Artist(name = artist, id = artistId)),
            title = title,
            // The album link, when the server sends it (/artist tracks) — enables the song menu's
            // "View album". Absent elsewhere / for standalone singles + videos.
            album = album?.takeIf { it.id.isNotBlank() }?.let { Album(name = it.name, id = it.id) },
            // Present on /album and /zemer-playlists tracks; the search categories send none.
            duration = durationSec,
            // Prefer the server's square album art; fall back to the (letterboxed) video frame until the
            // track carries one — see the /artist per-track thumbnail request.
            thumbnail = thumbnail?.takeIf { it.isNotBlank() } ?: thumbnailFor(videoId),
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
            // `artistId` present on /home-rows album cards (null elsewhere) — see [toSongItem].
            artists = if (artist.isBlank()) null else listOf(Artist(name = artist, id = artistId)),
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
     * The telemetry-ranked home rows as the app's native item types, in the server's ranked order.
     * Each list is dropped of missing/duplicate ids and passed through [dropBlocked] (the surgical
     * id-overrides). No explicit filtering — Zemer's whitelist-pure corpus has none. The artist-membership
     * whitelist is NOT re-run (whitelist-pure server-side), but each card carries its artist channel id
     * ([ZemerAlbum.artistId]/[ZemerTrack.artistId]/[ZemerArtist.id]) so the caller can run the home
     * one-per-artist dedup + female/israeli defence-in-depth. `topCommunity` maps to [PlaylistItem]s for
     * the featured-playlists row (discovery-sourced, view-ranked, whitelist-pure + content-filtered
     * server-side); [formatSongCount] renders the localized "N songs" count and defaults to omitting it.
     * See [HomeRows].
     */
    fun homeRows(
        resp: ZemerHomeRowsResponse,
        formatSongCount: (Int) -> String? = { null },
    ): HomeRows =
        HomeRows(
            albums = resp.topAlbums.filter { it.id.isNotBlank() }
                .map { it.toAlbumItem() }
                .distinctBy { it.id }
                .dropBlocked(),
            videos = songItems(resp.topVideos, hideExplicit = false),
            artists = resp.topArtists.filter { it.id.isNotBlank() }
                .map { it.toArtistItem() }
                .distinctBy { it.id }
                .dropBlocked(),
            community = playlistItems(resp.topCommunity, formatSongCount),
        )

    /** The four telemetry/discovery-ranked home rows in native item types (see [homeRows]). */
    data class HomeRows(
        val albums: List<AlbumItem>,
        val videos: List<SongItem>,
        val artists: List<ArtistItem>,
        val community: List<PlaylistItem>,
    )

    /**
     * A Zemer `/playlist` response as playable [SongItem]s. The server already whitelist-scoped and
     * content-filtered the tracks, so — like every other Zemer surface — the local artist whitelist is
     * NOT re-run here (re-filtering would re-introduce the card-vs-open count mismatch this endpoint
     * fixes); only `hideExplicit` and the surgical id-overrides ([dropBlocked]) are applied.
     */
    fun ZemerPlaylistResponse.toSongItems(hideExplicit: Boolean): List<SongItem> =
        songItems(tracks, hideExplicit)

    /**
     * A curated `/zemer-playlists?id=…` response as playable [SongItem]s, in curated order. Filtering
     * (whitelist, female, videos, id-overrides) already ran server-side against the sent flags, so —
     * like every Zemer surface — only `hideExplicit` and the surgical [dropBlocked] run here.
     */
    fun ZemerCuratedPlaylistResponse.toSongItems(hideExplicit: Boolean): List<SongItem> =
        songItems(tracks, hideExplicit)

    /**
     * A `/radio` page's tracks as playable [SongItem]s with the same defense-in-depth every other
     * Zemer surface gets — sparse-row drop, de-dup, and the surgical id-overrides ([dropBlocked]):
     * a Firestore-blocked id must not play even when the server's override sync lags the app's.
     * Explicit filtering is centrally applied by MusicService over every queue page, so
     * `hideExplicit` is not re-run here.
     */
    fun ZemerRadioResponse.toSongItems(): List<SongItem> =
        songItems(tracks, hideExplicit = false)

    /**
     * The curated albums as browsable [AlbumItem] rows (the detail screen's Albums chip), with the
     * same defense-in-depth every other Zemer collection gets: sparse-row drop, de-dup, and the
     * surgical id-overrides ([dropBlocked]) — a Firestore-blocked album must not render as a row
     * even if the server's serve-time strip lags the app's fresher local table.
     */
    fun ZemerCuratedPlaylistResponse.toAlbumItems(): List<AlbumItem> =
        albums.filter { it.id.isNotBlank() }.map { it.toAlbumItem() }.distinctBy { it.browseId }.dropBlocked()

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
            // Opener-threaded id first — but only when it's a real OP id: [toAlbumItem] falls cards'
            // playlistId back to the browseId, and persisting that MPRE would dead-press album radio
            // and mis-id share links. Then the server's own playlistId, then the browseId fallback.
            playlistId = playlistId?.takeIf { it != album.id } ?: album.playlistId ?: album.id,
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
                    // Prefer the square album art over the derived (letterboxed) video frame.
                    thumbnail = album.thumbnail ?: thumbnailFor(track.videoId),
                )
            }
            .distinctBy { it.id }
            .dropBlocked()
        return AlbumPage(album = albumItem, songs = songs)
    }

    /**
     * A Zemer `/artist` response as the [ArtistPage] the artist screen already consumes: the flat
     * songs / videos / albums / singles / playlists arrays become the screen's sections, in that order.
     * Tracks are whitelist-scoped server-side, so only hide-explicit + the surgical id-overrides
     * ([dropBlocked]) run here. Section titles reuse the same English constants as the summary view. The
     * header carries no play/shuffle/radio endpoint (the corpus has none): the screen plays Shuffle from
     * these tracks locally, and the Radio button waits for Zemer Radio.
     */
    fun ZemerArtistResponse.toArtistPage(
        hideExplicit: Boolean,
        formatSongCount: (Int) -> String? = { null },
    ): ArtistPage {
        fun albumSection(list: List<ZemerAlbum>): List<AlbumItem> =
            list.filter { it.id.isNotBlank() }.map { it.toAlbumItem() }.distinctBy { it.id }.dropBlocked()
        // Section order mirrors the InnerTube artist page: Songs, Albums, Singles, Videos, Playlists.
        val sections = buildList {
            songItems(songs, hideExplicit).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_SONGS, it, null)) }
            albumSection(albums).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_ALBUMS, it, null)) }
            albumSection(singles).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_SINGLES, it, null)) }
            songItems(videos, hideExplicit).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_VIDEOS, it, null)) }
            playlistItems(playlists, formatSongCount).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_PLAYLISTS, it, null)) }
        }
        return ArtistPage(artist = artist.toArtistItem(), sections = sections, description = null)
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
    private const val TITLE_SINGLES = "Singles"
    private const val TITLE_SONGS = "Songs"
    private const val TITLE_VIDEOS = "Videos"
    private const val TITLE_ARTISTS = "Artists"
    private const val TITLE_PLAYLISTS = "Playlists"
}
