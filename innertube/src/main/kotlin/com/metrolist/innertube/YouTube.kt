package com.metrolist.innertube

import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.GridRenderer
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.models.MusicCarouselShelfRenderer
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.MusicShelfRenderer
import com.metrolist.innertube.models.MusicTwoRowItemRenderer
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.oddElements
import com.metrolist.innertube.models.response.AccountMenuResponse
import com.metrolist.innertube.models.response.AddItemYouTubePlaylistResponse
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.models.response.CreatePlaylistResponse
import com.metrolist.innertube.models.response.EditPlaylistResponse
import com.metrolist.innertube.models.response.FeedbackResponse
import com.metrolist.innertube.models.response.GetQueueResponse
import com.metrolist.innertube.models.response.GetSearchSuggestionsResponse
import com.metrolist.innertube.models.response.GetTranscriptResponse
import com.metrolist.innertube.models.response.ImageUploadResponse
import com.metrolist.innertube.models.response.NextResponse
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.models.response.SearchResponse
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistItemsContinuationPage
import com.metrolist.innertube.pages.ArtistItemsPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.HistoryPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.LibraryContinuationPage
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.NewReleaseAlbumPage
import com.metrolist.innertube.pages.NextPage
import com.metrolist.innertube.pages.NextResult
import com.metrolist.innertube.pages.PlaylistContinuationPage
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.innertube.pages.RelatedPage
import com.metrolist.innertube.pages.SearchPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSuggestionPage
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) {
            innerTube.dataSyncId = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }

    var proxyAuth: String?
        get() = innerTube.proxyAuth
        set(value) {
            innerTube.proxyAuth = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            }.orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let { renderer ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                }
            }.orEmpty()
        )
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()

        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents

        // Extract top result section (musicCardShelfRenderer)
        val topResultSection = contents?.firstOrNull { it.musicCardShelfRenderer != null }?.musicCardShelfRenderer?.let { cardShelf ->
            val items = listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(cardShelf))
                .plus(
                    cardShelf.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                        .orEmpty()
                )
                .distinctBy { it.id }

            if (items.isNotEmpty()) {
                SearchSummary(
                    title = cardShelf.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text ?: "Top result",
                    items = items
                )
            } else null
        }

        // Collect all items from other sections (musicShelfRenderer + itemSectionRenderer).
        // YouTube migrated search results from musicShelfRenderer to itemSectionRenderer.
        val allOtherItems = contents
            ?.flatMap { section ->
                when {
                    section.musicShelfRenderer != null ->
                        section.musicShelfRenderer.contents?.getItems()
                            ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                            .orEmpty()
                    section.itemSectionRenderer != null ->
                        section.itemSectionRenderer.contents
                            ?.mapNotNull { it.musicResponsiveListItemRenderer }
                            ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                            .orEmpty()
                    else -> emptyList()
                }
            }
            ?.distinctBy { it.id }
            .orEmpty()

        // Group items by type and create categorized sections
        val categorizedSummaries = buildList {
            // Albums section
            val albums = allOtherItems.filterIsInstance<AlbumItem>()
            if (albums.isNotEmpty()) {
                add(SearchSummary("Albums", albums))
            }

            // Songs section
            val songs = allOtherItems.filterIsInstance<SongItem>()
            if (songs.isNotEmpty()) {
                add(SearchSummary("Songs", songs))
            }

            // Artists section
            val artists = allOtherItems.filterIsInstance<ArtistItem>()
            if (artists.isNotEmpty()) {
                add(SearchSummary("Artists", artists))
            }

            // Playlists section
            val playlists = allOtherItems.filterIsInstance<PlaylistItem>()
            if (playlists.isNotEmpty()) {
                add(SearchSummary("Playlists", playlists))
            }
        }

        // Combine top result with categorized sections
        SearchSummaryPage(
            summaries = buildList {
                topResultSection?.let { add(it) }
                addAll(categorizedSummaries)
            }
        )
    }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        val sections = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
        val searchItems = mutableListOf<YTItem>()
        var searchContinuation: String? = null
        sections?.forEach { section ->
            when {
                section.musicShelfRenderer != null -> {
                    section.musicShelfRenderer.contents?.getItems()
                        ?.mapNotNull { SearchPage.toYTItem(it) }
                        ?.let { searchItems.addAll(it) }
                    if (searchContinuation == null) {
                        searchContinuation = section.musicShelfRenderer.continuations?.getContinuation()
                    }
                }
                section.itemSectionRenderer != null -> {
                    section.itemSectionRenderer.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        ?.mapNotNull { SearchPage.toYTItem(it) }
                        ?.let { searchItems.addAll(it) }
                }
            }
        }
        SearchResult(
            items = searchItems.distinctBy { it.id },
            continuation = searchContinuation,
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        // Never `!!` here: a continuation response that isn't a musicShelfContinuation (or is empty)
        // must yield (emptyList, null), not throw. Throwing makes the ViewModel's loadMore() bail
        // WITHOUT clearing the continuation, so the Songs/Videos search list shimmers forever trying
        // to load more. Nulling the continuation when there are no items lets loadMore() stop.
        val items = response.continuationContents?.musicShelfContinuation?.contents
            ?.mapNotNull { SearchPage.toYTItem(it.musicResponsiveListItemRenderer) }
            ?: emptyList()
        SearchResult(
            items = items,
            continuation = if (items.isEmpty()) {
                null
            } else {
                response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
            },
        )
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
        if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
            val playlistId =
                response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.header.musicDetailHeaderRenderer.title.runs?.firstOrNull()?.text!!,
                artists = response.header.musicDetailHeaderRenderer.subtitle.runs?.filter { it.navigationEndpoint != null }?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                year = response.header.musicDetailHeaderRenderer.subtitle.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.header.musicDetailHeaderRenderer.thumbnail.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()!!.url,
                explicit = false, // TODO: Extract explicit badge for albums from YouTube response
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.getItems()?.mapNotNull {
                    AlbumPage.getSong(it, albumItem)
                }!!.toMutableList()
            )
        } else {
            val playlistId =
                response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                artists = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.straplineTextOne?.runs?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    }!!,
                year = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = response.contents.twoColumnBrowseResultsRenderer.tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
                explicit = false, // TODO: Extract explicit badge for albums from YouTube response
            )
            return@runCatching AlbumPage(
                album = albumItem,
                songs = if (withSongs) albumSongs(
                    playlistId, albumItem
                ).getOrThrow() else emptyList()
            )
        }
    }

    suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> = runCatching {
        var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()
        val shelf = response.contents?.twoColumnBrowseResultsRenderer
            ?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()
        // Some albums now return musicShelfRenderer instead of musicPlaylistShelfRenderer.
        val shelfContents = shelf?.musicPlaylistShelfRenderer?.contents
            ?: shelf?.musicShelfRenderer?.contents
            ?: emptyList()
        val songs = shelfContents.getItems()
            .mapNotNull {
                AlbumPage.getSong(it, album)
            }
            .toMutableList()
        var continuation = shelfContents.getContinuation()
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        val maxRequests = 50 // Prevent excessive API calls
        
        while (continuation != null && requestCount < maxRequests) {
            // Prevent infinite loops by tracking seen continuations
            if (continuation in seenContinuations) {
                break
            }
            seenContinuations.add(continuation)
            requestCount++
            
            response = innerTube.browse(
                client = WEB_REMIX,
                continuation = continuation,
            ).body<BrowseResponse>()
            songs += response.onResponseReceivedActions?.firstOrNull()?.appendContinuationItemsAction?.continuationItems?.getItems()?.mapNotNull {
                AlbumPage.getSong(it, album)
            }.orEmpty()
            continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        }
        songs
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()

        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
                channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                playEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer
                    ?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.overlay?.musicItemThumbnailOverlayRenderer
                    ?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                    ?: response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer
                        ?.contents?.firstOrNull()?.musicShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.gridRenderer
        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = gridRenderer.continuations?.getContinuation()
            )
        } else {
            val musicPlaylistShelfRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                items = musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    } ?: emptyList(),
                continuation = musicPlaylistShelfRenderer?.contents?.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

        when {
            response.continuationContents?.gridContinuation != null -> {
                val gridContinuation = response.continuationContents.gridContinuation
                ArtistItemsContinuationPage(
                    items = gridContinuation.items.mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    },
                    continuation = gridContinuation.continuations?.getContinuation()
                )
            }

            response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                ArtistItemsContinuationPage(
                    items = musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    },
                    continuation = musicPlaylistShelfContinuation.continuations?.getContinuation()
                )
            }

            else -> {
                val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                    ?.appendContinuationItemsAction?.continuationItems
                ArtistItemsContinuationPage(
                    items = continuationItems?.getItems()?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                    }!!,
                    continuation = continuationItems.getContinuation()
                )
            }
        }
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = true
        ).body<BrowseResponse>()
        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                // System auto-playlists (Liked Music, Sounds from Shorts) return their
                // header as response.header.musicHeaderRenderer; fall back to it.
                title = header?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: "",
                author = (header?.straplineTextOne
                    ?: response.header?.musicHeaderRenderer?.straplineTextOne)
                    ?.runs?.firstOrNull()?.let {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    },
                songCountText = (header?.secondSubtitle
                    ?: response.header?.musicHeaderRenderer?.secondSubtitle)
                    ?.runs?.firstOrNull()?.text,
                thumbnail = header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url
                    ?: response.header?.musicHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnails?.lastOrNull()?.url,
                playEndpoint = null,
                shuffleEndpoint = header?.buttons?.lastOrNull()
                    ?.menuRenderer?.items?.firstOrNull()
                    ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                    ?: response.header?.musicHeaderRenderer?.buttons?.lastOrNull()
                        ?.menuRenderer?.items?.firstOrNull()
                        ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = header?.buttons?.getOrNull(2)
                    ?.menuRenderer?.items?.find {
                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                    }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                    ?: response.header?.musicHeaderRenderer?.buttons?.getOrNull(2)
                        ?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = editable
            ),
            songs = run {
                val twoColShelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                    ?.contents?.firstOrNull()
                val twoColContents = twoColShelf?.musicPlaylistShelfRenderer?.contents
                    ?: twoColShelf?.musicShelfRenderer?.contents
                val singleColShelf = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                val singleColContents = singleColShelf?.musicPlaylistShelfRenderer?.contents
                    ?: singleColShelf?.musicShelfRenderer?.contents
                (twoColContents ?: singleColContents)?.getItems()?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                } ?: emptyList()
            },
            songsContinuation = run {
                val twoColShelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                    ?.contents?.firstOrNull()
                val twoColContents = twoColShelf?.musicPlaylistShelfRenderer?.contents
                    ?: twoColShelf?.musicShelfRenderer?.contents
                val twoColContinuations = twoColShelf?.musicPlaylistShelfRenderer?.continuations
                    ?: twoColShelf?.musicShelfRenderer?.continuations
                val singleColShelf = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                val singleColContents = singleColShelf?.musicPlaylistShelfRenderer?.contents
                    ?: singleColShelf?.musicShelfRenderer?.contents
                val singleColContinuations = singleColShelf?.musicPlaylistShelfRenderer?.continuations
                    ?: singleColShelf?.musicShelfRenderer?.continuations
                val mergedContents = twoColContents ?: singleColContents
                val mergedContinuations = twoColContinuations ?: singleColContinuations
                mergedContents?.getContinuation() ?: mergedContinuations?.getContinuation()
            },
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            browseId = "",
            setLogin = true
        ).body<BrowseResponse>()

        val mainContents = response.continuationContents?.sectionListContinuation?.contents
            ?.flatMap { it.musicPlaylistShelfRenderer?.contents.orEmpty() }
            ?: emptyList()

        val appendedContents = response.onResponseReceivedActions
            ?.firstOrNull()
            ?.appendContinuationItemsAction
            ?.continuationItems
            .orEmpty()

        val allContents = mainContents + appendedContents

        val songs = allContents
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }

        val nextContinuation = response.continuationContents
            ?.sectionListContinuation
            ?.continuations
            ?.getContinuation()
            ?: response.continuationContents
                ?.musicPlaylistShelfContinuation
                ?.continuations
                ?.getContinuation()
            ?: response.continuationContents
                ?.musicShelfContinuation
                ?.continuations
                ?.getContinuation()
            ?: response.onResponseReceivedActions
                ?.firstOrNull()
                ?.appendContinuationItemsAction
                ?.continuationItems
                ?.getContinuation()

        PlaylistContinuationPage(
            songs = songs,
            continuation = nextContinuation
        )
    }

    suspend fun home(continuation: String? = null, params: String? = null): Result<HomePage> = runCatching {
        if (continuation != null) {
            return@runCatching homeContinuation(continuation).getOrThrow()
        }

        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", params = params).body<BrowseResponse>()
        val continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
        val sectionListRender = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
        val sections = sectionListRender?.contents!!
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.toMutableList()
        val chips = sectionListRender.header?.chipCloudRenderer?.chips?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
        HomePage(chips, sections, continuation)
    }

    private suspend fun homeContinuation(continuation: String): Result<HomePage> = runCatching {
        val response =
            innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
        val continuation =
            response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        HomePage(
            null,
            response.continuationContents?.sectionListContinuation?.contents
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.orEmpty(), continuation
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    }

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> {
                        BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.gridRenderer.items
                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    content.musicCarouselShelfRenderer != null -> {
                        BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.musicCarouselShelfRenderer.contents
                                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    else -> null
                }
            }.orEmpty()
        )
    }

    suspend fun library(browseId: String, tabIndex: Int = 0) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents = if (tabs != null && tabs.size >= tabIndex) {
            tabs[tabIndex].tabRenderer.content?.sectionListRenderer?.contents?.firstOrNull()
        }
        else {
            null
        }

        when {
            contents?.gridRenderer != null -> {
                LibraryPage(
                    items = contents.gridRenderer.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridRenderer.continuations?.getContinuation()
                )
            }

            else -> { // contents?.musicShelfRenderer != null
                LibraryPage(
                    items = contents?.musicShelfRenderer?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.getContinuation()
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                LibraryContinuationPage(
                    items = contents.gridContinuation.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridContinuation.continuations?.getContinuation()
                )
            }

            else -> { // contents?.musicShelfContinuation != null
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.getContinuation()
                )
            }
        }
    }

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_charts",
            params = "ggMGCgQIgAQ%3D",
            continuation = continuation
        ).body<BrowseResponse>()

        val sections = mutableListOf<ChartsPage.ChartSection>()
    
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
            
                content.musicCarouselShelfRenderer?.let { renderer ->
                    val title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: return@forEach
                
                    val items = renderer.contents.mapNotNull { item ->
                        when {
                            item.musicResponsiveListItemRenderer != null -> 
                                convertToChartItem(item.musicResponsiveListItemRenderer)
                            item.musicTwoRowItemRenderer != null -> 
                                convertMusicTwoRowItem(item.musicTwoRowItemRenderer)
                            else -> null
                        }
                    }
                
                    if (items.isNotEmpty()) {
                        sections.add(
                            ChartsPage.ChartSection(
                                title = title,
                                items = items,
                                chartType = determineChartType(title)
                            )
                        )
                    }
                }
            
                content.gridRenderer?.let { renderer ->
                    val title = renderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: return@let
                
                    val items = renderer.items.mapNotNull { item ->
                        item.musicTwoRowItemRenderer?.let { renderer ->
                            convertMusicTwoRowItem(renderer)
                        }
                    }
                
                    if (items.isNotEmpty()) {
                        sections.add(
                            ChartsPage.ChartSection(
                                title = title,
                                items = items,
                                chartType = ChartsPage.ChartType.NEW_RELEASES
                            )
                        )
                    }
                }
            }

        ChartsPage(
            sections = sections,
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
        )
    }

    private fun determineChartType(title: String): ChartsPage.ChartType {
        return when {
            title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
            title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
            else -> ChartsPage.ChartType.GENRE
        }
    }

    private fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        return try {
            when {
                renderer.flexColumns.size >= 3 && renderer.videoId != null -> {
                    val firstColumn = renderer.flexColumns.getOrNull(0)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text ?: return null
                
                    val secondColumn = renderer.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text ?: return null

                    val titleRun = firstColumn.runs?.firstOrNull() ?: return null
                    val title = titleRun.text.takeIf { it.isNotBlank() } ?: return null

                    val artists = secondColumn.runs?.mapNotNull { run ->
                        run.text.takeIf { it.isNotBlank() }?.let { name ->
                            Artist(
                                name = name,
                                id = run.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        }
                    } ?: emptyList()

                    val thirdColumn = renderer.flexColumns.getOrNull(2)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text

                    SongItem(
                        id = renderer.videoId!!,
                        title = title,
                        artists = artists,
                        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.badges?.any { 
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" 
                        } == true,
                        chartPosition = thirdColumn?.runs?.firstOrNull()?.text?.toIntOrNull(),
                        chartChange = thirdColumn?.runs?.getOrNull(1)?.text
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            println("Error converting chart item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }

    private fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? {
        return try {
            when {
                renderer.isSong -> {
                    val subtitle = renderer.subtitle?.runs ?: return null
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = subtitle.mapNotNull {
                            it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                Artist(name = it.text, id = id)
                            }
                        },
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.any {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } == true
                    )
                }
                renderer.isAlbum -> {
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = renderer.subtitle?.runs?.oddElements()?.drop(1)?.mapNotNull {
                            it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                Artist(name = it.text, id = id)
                            }
                        },
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.any {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } == true
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            println("Error converting two row item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }

    suspend fun musicHistory() = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_history",
            setLogin = true
        ).body<BrowseResponse>()

        HistoryPage(
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull {
                    it.musicShelfRenderer?.let { musicShelfRenderer ->
                        HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                    }
                }
        )
    }

    suspend fun likeVideo(videoId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likeVideo(WEB_REMIX, videoId)
        else
            innerTube.unlikeVideo(WEB_REMIX, videoId)
    }

    suspend fun likePlaylist(playlistId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        else
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean, params: String? = null) = runCatching {
        if (subscribe)
            // Default params from the YouTube Music API - REQUIRED for the subscribe to actually take;
            // without it the endpoint returns 200 but the subscription never happens (Metrolist parity).
            innerTube.subscribeChannel(WEB_REMIX, channelId, params ?: "EgIIAhgA")
        else
            // Unsubscribe uses its own params (none for the music callers); never forward the
            // subscribe-intent default here, or an un-bookmark posts a subscribe intent and the
            // account stays subscribed (the local bookmark and the account would disagree).
            innerTube.unsubscribeChannel(WEB_REMIX, channelId, params)
    }

    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId ?: ""
        }
        return ""
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId)
    }

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = runCatching {
        innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) = runCatching {
        innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
    }

    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?) = runCatching {
        innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
    }

    fun createPlaylist(title: String) = runBlocking {
        innerTube.createPlaylist(WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
    }

    suspend fun uploadCustomThumbnailLink(playlistId: String, image: ByteArray) = runCatching {
        val uploadUrl = innerTube.getUploadCustomThumbnailLink(WEB_REMIX, image.size).headers["x-guploader-uploadid"]
        val blobReq = innerTube.uploadCustomThumbnail(
            WEB_REMIX,
            uploadUrl!!,
            image
        )
        val blobId = Json.decodeFromString<ImageUploadResponse>(blobReq.bodyAsText()).encryptedBlobId
        innerTube.setThumbnailPlaylist(WEB_REMIX, playlistId, blobId).body<EditPlaylistResponse>().newHeader?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
    }

    suspend fun removeThumbnailPlaylist(playlistId: String) = runCatching {
        innerTube.removeThumbnailPlaylist(WEB_REMIX, playlistId).body<EditPlaylistResponse>().newHeader?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        innerTube.deletePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null, webPlayerPot: String? = null, setLogin: Boolean = true): Result<PlayerResponse> = runCatching {
        innerTube.player(client, videoId, playlistId, signatureTimestamp, webPlayerPot, setLogin).body<PlayerResponse>()
    }

    /**
     * A 16-char client playback nonce. One cpn identifies ONE playback session: the session's
     * playback ping and every watchtime ping must share it (handoff: emulate-youtube-music-stream).
     */
    fun generateCpn(): String = (1..16).map {
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Random.Default.nextInt(0, 64)]
    }.joinToString("")

    /** `s.youtube.com` stats bases are fired against the music host, like the official web client. */
    private fun statsUrl(baseUrl: String) = baseUrl.replace(
        "https://s.youtube.com",
        "https://music.youtube.com",
    )

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        cpn: String = generateCpn(),
        cmt: String? = null,
        final: Boolean? = null,
        fmt: Int? = null,
        muted: Boolean? = null,
    ) = runCatching {
        innerTube.registerPlayback(
            url = statsUrl(playbackTracking),
            playlistId = playlistId,
            cpn = cpn,
            cmt = cmt,
            final = final,
            fmt = fmt,
            muted = muted,
        )
    }

    /** The watchtime beacon of a playback session — see [InnerTube.registerWatchtime]. */
    suspend fun registerWatchtime(
        watchtimeTracking: String,
        cpn: String,
        st: String,
        et: String,
        cmt: String,
        rt: String,
        final: Boolean,
        fmt: Int? = null,
        muted: Boolean? = null,
    ) = runCatching {
        innerTube.registerWatchtime(
            url = statsUrl(watchtimeTracking),
            cpn = cpn,
            st = st,
            et = et,
            cmt = cmt,
            rt = rt,
            final = final,
            fmt = fmt,
            muted = muted,
        )
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        val response = innerTube.next(
            WEB_REMIX,
            endpoint.videoId,
            endpoint.playlistId,
            endpoint.playlistSetVideoId,
            endpoint.index,
            endpoint.params,
            continuation).body<NextResponse>()
        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
            ?: response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer?.tabs?.get(0)?.tabRenderer?.content?.musicQueueRenderer
                ?.content?.playlistPanelRenderer!!
        val title = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer?.tabs?.get(0)?.tabRenderer?.content?.musicQueueRenderer
            ?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        val items = playlistPanelRenderer.contents.mapNotNull { content ->
            // Unwrap so a `playlistPanelVideoWrapperRenderer` row (song↔video counterpart carrier) still
            // feeds the queue instead of being silently dropped as an all-null Content.
            NextPage.primaryRendererOf(content)
                ?.let { renderer -> NextPage.fromPlaylistPanelVideoRenderer(renderer)?.let { it to renderer.selected } }
        }
        val songs = items.map { it.first }
        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }
        // Authoritative song→video counterpart mapping (empty for the common wrapper-less response).
        val counterparts = NextPage.counterpartsFrom(playlistPanelRenderer.contents)

        // load automix items
        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = songs + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = currentIndex,
                    endpoint = watchPlaylistEndpoint,
                    counterparts = counterparts + result.counterparts,
                )
            }
        }
        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint,
            counterparts = counterparts,
        )
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    ) songs.add(item)

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    is PodcastItem -> {} // Ignore podcasts in related page
                    is EpisodeItem -> {} // Ignore episodes in related page
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
        }
        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText
                .trim('♪')
                .trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        }!!
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            .actions[0].openPopupAction.popup.multiPageMenuRenderer
            .header?.activeAccountHeaderRenderer
            ?.toAccountInfo()!!
    }

    suspend fun feedback(tokens: List<String>): Result<Boolean> = runCatching {
        innerTube.feedback(WEB_REMIX, tokens).body<FeedbackResponse>().feedbackResponses.all { it.isProcessed }
    }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = runCatching {
        return innerTube.getMediaInfo(videoId)
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    @JvmInline
    value class LibraryFilter(val value: String) {
        companion object {
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000



    /**
     * Save a podcast show to library.
     * Uses likePlaylist API. Podcast IDs are "MPSP<playlistId>".
     */
    suspend fun savePodcast(podcastId: String, save: Boolean) = runCatching {
        val playlistId = podcastId.removePrefix("MPSP")
        if (save)
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        else
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
    }

    /**
     * Add episode to "Episodes for Later" playlist (SE).
     * Note: setVideoId is obtained from sync via episodesForLater(), NOT from this response.
     */
    suspend fun addEpisodeToSavedEpisodes(videoId: String) = runCatching {
        innerTube.addToPlaylist(WEB_REMIX, "SE", videoId)
    }

    /**
     * Remove episode from "Episodes for Later" playlist (SE).
     * Note: setVideoId is required for removal and must be obtained from the playlist response.
     */
    suspend fun removeEpisodeFromSavedEpisodes(videoId: String, setVideoId: String) = runCatching {
        innerTube.removeFromPlaylist(WEB_REMIX, "SE", videoId, setVideoId)
    }


    /**
     * Fetch saved podcast shows from library.
     * Uses FEmusic_library_non_music_audio_list and filters to only PodcastItem.
     */
    suspend fun libraryPodcasts(): Result<List<PodcastItem>> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_library_non_music_audio_list",
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()

        val items = when {
            contents?.gridRenderer != null -> {
                contents.gridRenderer.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }
            }
            contents?.musicShelfRenderer != null -> {
                contents.musicShelfRenderer.contents
                    ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?: emptyList()
            }
            else -> emptyList()
        }

        items.filterIsInstance<PodcastItem>()
    }

    /**
     * Fetch subscribed podcast channels from library.
     * Uses FEmusic_library_non_music_audio_channels_list.
     */
    suspend fun libraryPodcastChannels(): Result<LibraryPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_library_non_music_audio_channels_list",
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()

        val items = when {
            contents?.gridRenderer != null -> {
                contents.gridRenderer.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }
            }
            contents?.musicShelfRenderer != null -> {
                contents.musicShelfRenderer.contents
                    ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?: emptyList()
            }
            else -> emptyList()
        }

        LibraryPage(
            items = items,
            continuation = null
        )
    }

    /**
     * Fetch library podcast episodes page.
     * Uses FEmusic_library_non_music_audio_list and returns full LibraryPage.
     */
    suspend fun libraryPodcastEpisodes(): Result<LibraryPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_library_non_music_audio_list",
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()

        val items = when {
            contents?.gridRenderer != null -> {
                contents.gridRenderer.items
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }
            }
            contents?.musicShelfRenderer != null -> {
                contents.musicShelfRenderer.contents
                    ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?: emptyList()
            }
            else -> emptyList()
        }

        LibraryPage(
            items = items,
            continuation = null
        )
    }

    /**
     * Fetch saved podcast shows from library.
     * Uses libraryPodcastEpisodes and filters to only PodcastItem.
     */
    suspend fun savedPodcastShows(): Result<List<PodcastItem>> = runCatching {
        val libraryPage = libraryPodcastEpisodes().getOrThrow()
        libraryPage.items.filterIsInstance<PodcastItem>()
    }

    /**
     * Fetch "Episodes for Later" playlist (VLSE).
     * Returns manually saved episodes with setVideoId for removal.
     */
    suspend fun episodesForLater(): Result<List<SongItem>> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VLSE",
            setLogin = true
        ).body<BrowseResponse>()

        // VLSE uses musicPlaylistShelfRenderer, not musicShelfRenderer
        val contents = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstOrNull()

        val shelfContents = contents?.musicPlaylistShelfRenderer?.contents
            ?: contents?.musicShelfRenderer?.contents

        // Parse musicResponsiveListItemRenderer (standard playlist format)
        shelfContents?.mapNotNull { it.musicResponsiveListItemRenderer }
            ?.mapNotNull { renderer ->
                val videoId = renderer.playlistItemData?.videoId ?: return@mapNotNull null
                val setVideoId = renderer.playlistItemData?.playlistSetVideoId
                val title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return@mapNotNull null

                // For VLSE episodes, try to find podcast info from menu items
                // Look for "Go to podcast" or similar menu navigation with MPSP browseId
                val podcastFromMenu = renderer.menu?.menuRenderer?.items
                    ?.mapNotNull { it.menuNavigationItemRenderer }
                    ?.find { item ->
                        item.navigationEndpoint.browseEndpoint?.browseId?.startsWith("MPSP") == true
                    }?.let { item ->
                        item.navigationEndpoint.browseEndpoint?.let { endpoint ->
                            Artist(name = item.text.runs?.firstOrNull()?.text ?: "", id = endpoint.browseId)
                        }
                    }

                // Fallback: try to find any MPSP or UC (channel) browseEndpoint in flexColumns
                val artists = if (podcastFromMenu != null) {
                    listOf(podcastFromMenu)
                } else {
                    renderer.flexColumns.flatMap { col ->
                        col.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                            ?.mapNotNull { run ->
                                run.navigationEndpoint?.browseEndpoint?.let { endpoint ->
                                    // Only include if it's a podcast (MPSP) or channel (UC), not episode (MPED)
                                    if (endpoint.browseId.startsWith("MPSP") || endpoint.browseId.startsWith("UC")) {
                                        Artist(name = run.text, id = endpoint.browseId)
                                    } else null
                                }
                            } ?: emptyList()
                    }.takeIf { it.isNotEmpty() } ?: emptyList()
                }

                SongItem(
                    id = videoId,
                    title = title,
                    artists = artists,
                    album = null,
                    duration = null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: "",
                    setVideoId = setVideoId,
                    isEpisode = true,
                )
            } ?: emptyList()
    }
}
