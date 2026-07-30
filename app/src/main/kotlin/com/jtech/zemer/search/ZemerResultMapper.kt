package com.jtech.zemer.search

import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
import com.metrolist.innertube.pages.PodcastPage
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
// Zemer-only search chips — podcasts and episodes have no YouTube [SearchFilter] equivalent. The value
// is a private key used ONLY client-side to pick the `/search` response's podcast/episode category in
// [ZemerResultMapper.filtered]; it is never sent to any server (Zemer filtering is response-side).
val ZEMER_FILTER_PODCAST = SearchFilter("zemer_podcast")
val ZEMER_FILTER_EPISODE = SearchFilter("zemer_episode")

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

    fun ZemerTrack.toSongItem(isVideo: Boolean = false): SongItem =
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
            isVideo = isVideo,
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
    private fun songItems(tracks: List<ZemerTrack>, hideExplicit: Boolean, isVideo: Boolean = false): List<SongItem> =
        tracks.filter { it.videoId.isNotBlank() }
            .map { it.toSongItem(isVideo) }
            .filterExplicit(hideExplicit)
            .distinctBy { it.id }
            .dropBlocked()

    /** Plain songs only (the Songs chip and the summary "Songs" section). */
    private fun plainSongItems(resp: ZemerSearchResponse, hideExplicit: Boolean): List<SongItem> =
        songItems(resp.categories.songs, hideExplicit)

    /**
     * Videos as [SongItem]s (flagged `isVideo`) — the dedicated Videos / "Video songs" chip and its own
     * summary section. Songs and videos are kept in SEPARATE sections/chips so a video-song never shows
     * up in both the Songs chip and the Video songs chip.
     */
    private fun videoSongItems(resp: ZemerSearchResponse, hideExplicit: Boolean): List<SongItem> =
        songItems(resp.categories.videos, hideExplicit, isVideo = true)

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
            videos = songItems(resp.topVideos, hideExplicit = false, isVideo = true),
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
     * A shared user playlist's tracks as playable [SongItem]s, in shared order, via the SAME
     * [songItems] pipeline as `/playlist`, `/zemer-playlists` and `/radio` (sparse-row drop,
     * de-dup, hide-explicit, [dropBlocked]; `female`-tagged snapshot members are
     * receiver-conditional by contract, so the receiver's own table decides). [blockVideos] is the
     * CLIENT backstop for the server's query flag - the one surface whose content is sender-chosen
     * gets a second gate from the per-track `isVideo` the server already sends, like female/
     * blocked-ids everywhere else.
     */
    fun ZemerUserPlaylistResponse.toSongItems(hideExplicit: Boolean, blockVideos: Boolean): List<SongItem> =
        songItems(tracks.filterNot { blockVideos && it.isVideo }, hideExplicit)

    /**
     * One station schedule slot as a playable [SongItem], so station items ride the SAME
     * [SongItem.toMediaMetadata] path as every other Zemer queue: the derived-[thumbnailFor]
     * fallback for coverless slots and the 544x544 artwork resize both come for free (a hand-built
     * MediaMetadata lost them - blank player/notification art on coverless standalones).
     */
    fun ZemerStationEntry.toSongItem(): SongItem =
        SongItem(
            id = videoId,
            title = title,
            artists = listOf(Artist(name = artist, id = artistId)),
            duration = durationSec,
            thumbnail = thumbnail?.takeIf { it.isNotBlank() } ?: thumbnailFor(videoId),
            explicit = false,
        )

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
            // `artistId` (when the server sends it) makes the insert resolve the credit by ID —
            // the name-only fallback is what collided with generated artist rows (stuck-skeleton).
            artists = if (album.artist.isBlank()) null else listOf(Artist(name = album.artist, id = album.artistId)),
            year = album.year,
            thumbnail = album.thumbnail.orEmpty(),
        )
        val songs = tracks
            .filter { it.videoId.isNotBlank() }
            // sortedBy is stable, so untagged tracks keep server order (after the numbered ones).
            .sortedBy { it.trackNumber ?: Int.MAX_VALUE }
            .map { track ->
                track.toSongItem().let { song ->
                    // Thread the album's artistId into track credits matching the album artist —
                    // the wire tracks are name-only, and this covers the song-map side of the same
                    // generated-row collision.
                    if (album.artistId != null && song.artists.singleOrNull()?.name == album.artist) {
                        song.copy(artists = listOf(Artist(name = album.artist, id = album.artistId)))
                    } else song
                }.copy(
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
            songItems(videos, hideExplicit, isVideo = true).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_VIDEOS, it, null)) }
            playlistItems(playlists, formatSongCount).takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_PLAYLISTS, it, null)) }
        }
        return ArtistPage(artist = artist.toArtistItem(), sections = sections, description = null)
    }

    // --- Podcasts. The server serves discovery ready-to-render (whitelist-pure, art guaranteed); these
    // adapt the wire rows into the SAME InnerTube item/page types the podcast UI already consumes, so
    // playback (episode = videoId via InnerTube) and the screens are unchanged. ---

    private fun ZemerPodcastShow.toPodcastItem(): PodcastItem =
        PodcastItem(
            id = id,
            title = name,
            author = author?.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = channelId) },
            episodeCountText = episodeCountText,
            thumbnail = thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            channelId = channelId,
        )

    private fun ZemerPodcastDetail.toPodcastItem(): PodcastItem =
        PodcastItem(
            id = id,
            title = name,
            author = author?.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = channelId) },
            episodeCountText = null,
            thumbnail = thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            channelId = channelId,
            categories = categories,
        )

    private fun ZemerPodcastEpisode.toEpisodeItem(): EpisodeItem =
        EpisodeItem(
            id = videoId,
            title = title,
            // The episode's "author" is its show/host — carries the channelId so an episode row can reach
            // the host channel, and the podcast name shows under the title.
            author = podcastName?.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = channelId) },
            // The owning SHOW ({id, name}) — powers the song menu's "View podcast".
            podcast = podcastId?.takeIf { it.isNotBlank() }?.let { Album(name = podcastName.orEmpty(), id = it) },
            duration = durationSeconds.takeIf { it > 0 },
            publishDateText = publishedAt,
            // REQUIRED non-null: fall back to the derived video frame when the server omits per-episode art.
            thumbnail = thumbnail?.takeIf { it.isNotBlank() } ?: thumbnailFor(videoId),
        )

    private fun List<ZemerPodcastEpisode>.toEpisodeItems(): List<EpisodeItem> =
        filter { it.videoId.isNotBlank() }.map { it.toEpisodeItem() }.distinctBy { it.id }.dropBlocked()

    private fun List<ZemerPodcastShow>.toPodcastItems(): List<PodcastItem> =
        filter { it.id.isNotBlank() }.map { it.toPodcastItem() }.distinctBy { it.id }.dropBlocked()

    /** The telemetry-ranked Podcasts-tab rows in native item types (see [podcastHomeRows]). */
    data class PodcastHomeRows(
        val featured: List<PodcastItem>,
        val topPodcasts: List<PodcastItem>,
        val trendingEpisodes: List<EpisodeItem>,
    )

    /**
     * `GET /podcast-home-rows` → the Podcasts-tab ranked rows as the InnerTube item types the podcast UI
     * already consumes (the podcast analogue of [homeRows]). Whitelist-pure server-side; only the surgical
     * id-overrides ([dropBlocked], applied inside [toPodcastItems]/[toEpisodeItems]) run client-side.
     */
    fun podcastHomeRows(resp: ZemerPodcastHomeRowsResponse): PodcastHomeRows =
        PodcastHomeRows(
            featured = resp.featured.toPodcastItems(),
            topPodcasts = resp.topPodcasts.toPodcastItems(),
            trendingEpisodes = resp.trendingEpisodes.toEpisodeItems(),
        )

    data class VideoHomeRows(
        val trending: List<SongItem>,
        val newVideos: List<SongItem>,
        val artists: List<ArtistItem>,
    )

    /**
     * The `/video-home-rows` rows as the item types the Videos tab already renders. Both track rows are
     * video-classified (`isVideo = true` — the badge/menu/toggle flag, set once at this mapper boundary);
     * `hideExplicit = false` matches the `/home-rows` topVideos treatment. Blocked-id overrides run
     * inside [songItems]; artists drop blank ids like every artist row.
     */
    fun videoHomeRows(resp: ZemerVideoHomeRowsResponse): VideoHomeRows =
        VideoHomeRows(
            trending = songItems(resp.trendingVideos, hideExplicit = false, isVideo = true),
            newVideos = songItems(resp.newVideos, hideExplicit = false, isVideo = true),
            artists = resp.topVideoArtists.filter { it.id.isNotBlank() }
                .map { it.toArtistItem() }
                .distinctBy { it.id }
                .dropBlocked(),
        )

    /**
     * A `/podcast` response as the [PodcastPage] the SHOW screen already consumes. Null when the header
     * is missing (treated as a 404 → the screen backs out). `continuation` carries the server's
     * `nextOffset` so the screen can page more episodes.
     */
    fun ZemerPodcastResponse.toPodcastPage(): PodcastPage? {
        val detail = podcast?.takeIf { it.id.isNotBlank() } ?: return null
        return PodcastPage(
            podcast = detail.toPodcastItem(),
            episodes = episodes.toEpisodeItems(),
            continuation = nextOffset?.toString(),
        )
    }

    /**
     * A `/podcast-channel` response as the [ArtistPage] the host-channel screen (ArtistScreen, opened
     * with `isPodcastChannel=true`) already consumes — the shows shelf becomes a "Podcasts" section and
     * the loose latest episodes an "Episodes" section. Null when the channel header is missing (404).
     */
    fun ZemerPodcastChannelResponse.toArtistPage(): ArtistPage? {
        val ch = channel?.takeIf { it.id.isNotBlank() } ?: return null
        val sections = buildList {
            shows.toPodcastItems().takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_PODCASTS, it, null)) }
            episodes.toEpisodeItems().takeIf { it.isNotEmpty() }
                ?.let { add(ArtistSection(TITLE_EPISODES, it, null)) }
        }
        return ArtistPage(
            artist = ArtistItem(
                id = ch.id,
                title = ch.name,
                thumbnail = ch.thumbnail,
                channelId = ch.id,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            ),
            sections = sections,
            description = ch.description,
        )
    }

    /**
     * The [toArtistPage] mapping plus the episodes shelf's paging cursor: `nextOffset` pages the
     * channel-wide episode list on `/podcast-channel` (null / absent = no more pages — pre-paging
     * servers and the offline snapshot both read as unpaged).
     */
    data class PodcastChannelPage(val artistPage: ArtistPage, val episodesNextOffset: Int?)

    fun ZemerPodcastChannelResponse.toPodcastChannelPage(): PodcastChannelPage? =
        toArtistPage()?.let { PodcastChannelPage(it, nextOffset) }

    /** An `offset > 0` `/podcast-channel` page as its episode rows (shows ride page 0 only). */
    fun ZemerPodcastChannelResponse.toChannelEpisodeItems(): List<EpisodeItem> = episodes.toEpisodeItems()

    /** New Episodes (`/podcasts/new-episodes`) as the episode rows the library feed renders. */
    fun ZemerNewEpisodesResponse.toEpisodeItems(): List<EpisodeItem> = episodes.toEpisodeItems()

    /** Search-folded podcast shows / episodes (the `podcasts` + `episodes` categories). */
    fun ZemerSearchResponse.podcastShowItems(): List<PodcastItem> = categories.podcasts.toPodcastItems()
    fun ZemerSearchResponse.podcastEpisodeItems(): List<EpisodeItem> = categories.episodes.toEpisodeItems()

    /** One podcast genre's page: the display title + its member shows as the standard podcast show cards. */
    data class PodcastGenrePage(val title: String, val shows: List<PodcastItem>)

    /**
     * A `/podcast-genres?id=` response as a [PodcastGenrePage] (title + shows). Null when the header is
     * missing (treated as a 404 → the screen backs out). Shows reuse the existing show mapping, so they
     * render + route through the same podcast show card as the browse grid / channel shelf.
     */
    fun ZemerPodcastGenrePageResponse.toPodcastGenrePage(): PodcastGenrePage? {
        val g = genre.takeIf { it.id.isNotBlank() } ?: return null
        return PodcastGenrePage(title = g.title, shows = shows.toPodcastItems())
    }

    /**
     * One genre's page in native item types (see [toGenrePage]). [header] carries the true
     * post-filter totals (the artist/album/single lists are the server's capped top-k, not the
     * totals); [nextOffset] pages the songs/videos tracklist — the categorized lists belong to
     * page 0 only. Zemer-owned rather than [ArtistPage] because that type has no paging field.
     */
    data class ZemerGenrePage(
        val header: ZemerGenreHeader,
        val artists: List<ArtistItem>,
        val albums: List<AlbumItem>,
        val singles: List<AlbumItem>,
        val songs: List<SongItem>,
        val videos: List<SongItem>,
        val nextOffset: Int?,
    )

    /**
     * The genre header mosaic's covers: the genre's top release art (albums first — the strongest
     * covers — then singles, then song art to fill), blanks dropped, de-duped, downsized to the
     * mosaic variant ([mosaicVariant] — sized for the ~230dp-tall header band, not full art),
     * de-duped, and capped at [max]. Needs at least [min] UNIQUE covers to render (they tile evenly
     * to fill the width, so 3-4 covers look like a proper strip; below that a lone/stretched cover
     * "breaks the flow" — the owner rule — so the weave header carries those genres instead).
     * Songs reuse their album's art, so many genres have only a handful of unique covers. A LAZY
     * sequence so map/distinct/take short-circuit instead of processing a 100-track page. Lives here
     * (not the screen) so the ViewModel can preload the same URLs the moment the page lands.
     */
    fun ZemerGenrePage.headerCovers(min: Int = 3, max: Int = 5): List<String> =
        (albums.asSequence().map { it.thumbnail } +
            singles.asSequence().map { it.thumbnail } +
            songs.asSequence().map { it.thumbnail })
            .filter { it.isNotBlank() }
            .map(::mosaicVariant)
            .distinct()
            .take(max)
            .toList()
            .takeIf { it.size >= min }
            .orEmpty()

    /**
     * The header-mosaic rendition of a cover, sized for the tall header band (the header was
     * expanded "a lot bigger", so the previous mqdefault/320px hints upscaled and read blurry):
     * ytimg frames go to `hqdefault` (480x360), googleusercontent art to a 480px square. Distinctness
     * runs AFTER this mapping, so two renditions of one image can never slip in as "different"
     * covers. Isolated to the mosaic — NOT the shared [thumbnailFor] player-surface variant.
     */
    internal fun mosaicVariant(url: String): String = when {
        url.contains("i.ytimg.com") ->
            url.replace(Regex("/(mq|sd|maxres|hq720|0)default"), "/hqdefault")
        url.contains("googleusercontent.com") -> {
            // Strip an existing FIFE size suffix (`=w544-h544-…`, `=s120-c`, …) before appending —
            // the old code only handled `=w`, so `=s`-sized URLs got a SECOND `=` param and 4xx'd.
            val marker = url.lastIndexOf('=')
            val base = if (marker > 0 && FIFE_SIZE.matches(url.substring(marker + 1))) url.substring(0, marker) else url
            "$base=w480-h480-l90-rj"
        }
        else -> url
    }

    /** A googleusercontent FIFE size suffix (the part after the last `=`), e.g. `s120-c`, `w544-h544-l90-rj`. */
    private val FIFE_SIZE = Regex("[sw]\\d+.*")

    /** One page of a genre facet's full list (albums/singles) as [AlbumItem]s + the next offset. */
    data class ZemerGenreFacetPage(val albums: List<AlbumItem>, val nextOffset: Int?)

    /**
     * A `/genres?id=&facet=albums|singles` page as browsable [AlbumItem] rows, with the same
     * defense-in-depth every Zemer collection gets (sparse-row drop, de-dup, blocked-ids). Powers
     * the genre see-all screens.
     */
    fun ZemerGenreFacetResponse.toAlbumFacetPage(): ZemerGenreFacetPage =
        ZemerGenreFacetPage(
            albums = items.filter { it.id.isNotBlank() }.map { it.toAlbumItem() }.distinctBy { it.browseId }.dropBlocked(),
            nextOffset = nextOffset,
        )

    /**
     * A `/genres?id=` response in the same native item types the artist page maps to, with the same
     * defense-in-depth every Zemer surface gets — sparse-row drop, de-dup, hide-explicit on the
     * track lists, and the surgical id-overrides ([dropBlocked]). The artist-membership whitelist is
     * NOT re-run (the corpus is whitelist-pure server-side).
     */
    fun ZemerGenrePageResponse.toGenrePage(hideExplicit: Boolean): ZemerGenrePage {
        fun albumSection(list: List<ZemerAlbum>): List<AlbumItem> =
            list.filter { it.id.isNotBlank() }.map { it.toAlbumItem() }.distinctBy { it.id }.dropBlocked()
        return ZemerGenrePage(
            header = genre,
            artists = artists.filter { it.id.isNotBlank() }.map { it.toArtistItem() }.distinctBy { it.id }.dropBlocked(),
            albums = albumSection(albums),
            singles = albumSection(singles),
            songs = songItems(songs, hideExplicit),
            videos = songItems(videos, hideExplicit, isVideo = true),
            nextOffset = nextOffset,
        )
    }

    /**
     * The grouped summary view (`filter == null`): items grouped by type into sections. Songs and
     * videos get SEPARATE sections (Songs / Videos) — each drills into its own chip — so a video-song
     * is never shown in both. The "Videos" section header is relabelled "Video songs" by the screen when
     * videos play as audio. The "Playlists" section shows the community playlists only — its header
     * drills into the Community chip, so previewing community here keeps tap-through consistent
     * (artist-owned/featured playlists are reached via the Featured chip). Empty sections are omitted.
     * (No "Top result" card — the Zemer server does not return one.)
     */
    fun summaryPage(
        resp: ZemerSearchResponse,
        hideExplicit: Boolean,
        formatSongCount: (Int) -> String? = { null },
    ): SearchSummaryPage {
        val playlists = playlistItems(resp.categories.community, formatSongCount)
        // Each section is a compact preview; the merged sections (albums+singles) would otherwise run
        // long. The full per-category list is one tap away on the chip.
        fun MutableList<SearchSummary>.section(title: String, items: List<YTItem>) =
            items.take(SUMMARY_SECTION_LIMIT).takeIf { it.isNotEmpty() }?.let { add(SearchSummary(title, it)) }
        val summaries = buildList {
            section(TITLE_ALBUMS, albumItems(resp))
            section(TITLE_SONGS, plainSongItems(resp, hideExplicit))
            section(TITLE_VIDEOS, videoSongItems(resp, hideExplicit))
            section(TITLE_ARTISTS, artistItems(resp))
            section(TITLE_PLAYLISTS, playlists)
            // Podcast SHOWS + EPISODES folded in (server reply 2026-08-01). No filter chip, so these
            // sections render with a non-interactive header (NavigationTitle no-ops without a chip) —
            // the rows themselves open the show / play the episode.
            section(TITLE_PODCASTS, resp.podcastShowItems())
            section(TITLE_EPISODES, resp.podcastEpisodeItems())
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
            // Songs and videos are separate: the Songs chip returns plain songs only, the Videos /
            // "Video songs" chip returns videos only — so a video-song never appears in both.
            SearchFilter.FILTER_SONG.value -> plainSongItems(resp, hideExplicit)
            SearchFilter.FILTER_VIDEO.value -> videoSongItems(resp, hideExplicit)
            SearchFilter.FILTER_ARTIST.value -> artistItems(resp)
            SearchFilter.FILTER_ALBUM.value -> albumItems(resp)
            SearchFilter.FILTER_COMMUNITY_PLAYLIST.value -> playlistItems(resp.categories.community, formatSongCount)
            SearchFilter.FILTER_FEATURED_PLAYLIST.value -> playlistItems(resp.categories.playlists, formatSongCount)
            ZEMER_FILTER_PODCAST.value -> resp.podcastShowItems()
            ZEMER_FILTER_EPISODE.value -> resp.podcastEpisodeItems()
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
                songItems(resp.categories.videos, hideExplicit, isVideo = true) +
                playlistItems(resp.categories.playlists, formatSongCount) +
                playlistItems(resp.categories.community, formatSongCount) +
                resp.podcastShowItems() +
                resp.podcastEpisodeItems())
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
    private const val TITLE_PODCASTS = "Podcasts"

    /** The channel page's episodes-section title — public so the see-all screen can key its paging on it. */
    const val TITLE_EPISODES = "Episodes"
}
