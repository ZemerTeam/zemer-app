# InnerTube module documentation

## Module facts

| Fact | Value |
| --- | --- |
| Gradle module | `:innertube` |
| Kotlin target | JVM toolchain 21 |
| Serialization plugin | `libs.plugins.kotlin.serialization` |
| HTTP stack dependencies | Ktor client core, OkHttp engine, content negotiation, JSON serialization, encoding, OkHttp DNS-over-HTTPS |
| External extractor dependency | `libs.extractor` with `com.google.protobuf:protobuf-javalite` excluded |
| Test dependency | JUnit |
| Primary packages | `com.metrolist.innertube`, `.models`, `.models.body`, `.models.response`, `.pages`, `.utils` |

## Architecture visible in code

| Layer | Files / declarations | Role |
| --- | --- | --- |
| Transport wrapper | `InnerTube.kt` / `class InnerTube` | Creates the Ktor client, manages locale, visitor data, data sync ID, cookie, proxy, proxy auth, and request methods for YouTube endpoints. |
| Public facade | `YouTube.kt` / `object YouTube` | Exposes app-facing functions that call `InnerTube`, deserialize responses, and convert raw renderer trees into typed page/domain models. |
| Domain models | `models/*.kt` | Kotlin serialization models and app-level item types such as `SongItem`, `AlbumItem`, `ArtistItem`, `PlaylistItem`, endpoints, thumbnails, runs, clients, and response-context structures. |
| Request bodies | `models/body/*.kt` | Bodies for browse, search, next, player, queue, playlist mutations, feedback, like, subscribe, transcript, account menu, and playlist creation/editing. |
| Response models | `models/response/*.kt` | Top-level response wrappers for account menu, browse, continuations, playlist edits, feedback, queue, suggestions, transcript, image upload, next, player, and search. |
| Page parsers | `pages/*.kt` | Converts YouTube Music renderers/responses into page objects: home, search, album, artist, playlist, library, charts, history, related, next, mood/genres, and continuations. |
| Utilities | `utils/*.kt` | Resilient DNS and continuation helpers. |
| NewPipe bridge | `pages/NewPipe.kt` and functions in `YouTube.kt` | Implements a NewPipe downloader and exposes stream URL/media-info helpers. |

## `InnerTube` request methods

`InnerTube` defines mutable request context (`visitorData`, `dataSyncId`, `cookie`, `proxy`, `proxyAuth`, `useLoginForBrowse`) and these endpoint methods:

| Method | Endpoint/operation visible from name/body type |
| --- | --- |
| `search` | Search request. |
| `player` | Player request with video ID, playlist ID, signature timestamp, PoToken fields, and login flag. |
| `registerPlayback` | Playback tracking registration. |
| `browse` | Browse request with browse ID, params, continuation, and login flag. |
| `next` | Next/queue request. |
| `feedback` | Feedback token request. |
| `getSearchSuggestions` | Search suggestions request. |
| `getQueue` | Queue request. |
| `getTranscript` | Transcript request. |
| `getSwJsData` | Service worker JavaScript data request. |
| `accountMenu` | Account menu request. |
| `likeVideo` / `unlikeVideo` | Video like state mutation. |
| `subscribeChannel` / `unsubscribeChannel` | Channel subscription mutation. |
| `likePlaylist` / `unlikePlaylist` | Playlist like state mutation. |
| `addToPlaylist`, `addPlaylistToPlaylist`, `removeFromPlaylist`, `moveSongPlaylist` | Playlist item mutations. |
| `createPlaylist`, `renamePlaylist`, `deletePlaylist` | Playlist lifecycle mutations. |
| `getUploadCustomThumbnailLink`, `uploadCustomThumbnail`, `setThumbnailPlaylist`, `removeThumbnailPlaylist` | Playlist thumbnail upload/removal flow. |
| `returnYouTubeDislike` | Return YouTube Dislike lookup. |
| `getMediaInfo` | NewPipe-backed media information. |

## `YouTube` facade methods

`YouTube` forwards context properties to a private `InnerTube` instance and exposes these app-facing operations:

| Category | Methods |
| --- | --- |
| Search | `searchSuggestions`, `searchSummary`, `search`, `searchContinuation` |
| Catalog pages | `album`, `albumSongs`, `artist`, `artistItems`, `artistItemsContinuation`, `playlist`, `playlistContinuation`, `home`, `explore`, `newReleaseAlbums`, `moodAndGenres`, `browse` |
| Library/history/charts | `library`, `libraryContinuation`, `libraryRecentActivity`, `getChartsPage`, `musicHistory` |
| Mutations | `likeVideo`, `likePlaylist`, `subscribeChannel`, `addToPlaylist`, `addPlaylistToPlaylist`, `removeFromPlaylist`, `moveSongPlaylist`, `createPlaylist`, `renamePlaylist`, `uploadCustomThumbnailLink`, `removeThumbnailPlaylist`, `deletePlaylist`, `feedback` |
| Playback | `player`, `registerPlayback`, `next`, `lyrics`, `related`, `queue`, `transcript`, `getMediaInfo`, `getNewPipeStreamUrls`, `newPipePlayer` |
| Account | `accountInfo`, `getChannelId` |

## InnerTube consumers in `app`

The app imports `com.metrolist.innertube.YouTube` from source files in these areas:

| Area | Representative files |
| --- | --- |
| App/session initialization | `App.kt`, `MainActivity.kt` |
| Playback | `playback/MusicService.kt`, queue files, MediaStore/Exo download services |
| View models | Home, search, album, artist, playlist, library, lyrics, player, and sync-related view models |
| UI screens | Album, browse, charts, explore, home, mood/genres, new release, YouTube browse, artist screens, playlist screens, search screens |
| Utilities | `SyncUtils.kt`, `QueueBoardRadio.kt`, stream/cache helpers |

## Page parser inventory

| File | Lines | Key declarations |
| --- | ---: | --- |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/AlbumPage.kt` | 127 | class AlbumPage, val album, val songs, fun getPlaylistId, var playlistId, fun getTitle, val title, fun getYear, val title, fun getThumbnail, fun getArtists, val artists |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsContinuationPage.kt` | 8 | class ArtistItemsContinuationPage, val items, val continuation |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsPage.kt` | 122 | class ArtistItemsPage, val title, val items, val continuation, fun fromMusicResponsiveListItemRenderer, fun fromMusicTwoRowItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistPage.kt` | 187 | class ArtistSection, val title, val items, val moreEndpoint, class ArtistPage, val artist, val sections, val description, fun fromSectionListRendererContent, fun fromMusicShelfRenderer, fun fromMusicCarouselShelfRenderer, fun fromMusicResponsiveListItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/BrowseResult.kt` | 31 | class BrowseResult, val title, val items, class Item, val title, val items, fun filterExplicit |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ChartsPage.kt` | 18 | class ChartsPage, val sections, val continuation, class ChartSection, val title, val items, val chartType, class ChartType |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/ExplorePage.kt` | 8 | class ExplorePage, val newReleaseAlbums, val moodAndGenres |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HistoryPage.kt` | 68 | class HistoryPage, val sections, class HistorySection, val title, val songs, fun fromMusicShelfRenderer, fun fromMusicResponsiveListItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/HomePage.kt` | 166 | class HomePage, val chips, val sections, val continuation, class Chip, val title, val endpoint, val deselectEndPoint, fun fromChipCloudChipRenderer, class Section, val title, val label |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryAlbumsPage.kt` | 36 | class LibraryAlbumsPage, val albums, val continuation, fun fromMusicTwoRowItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryContinuationPage.kt` | 8 | class LibraryContinuationPage, val items, val continuation |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryPage.kt` | 148 | class LibraryPage, val items, val continuation, fun fromMusicTwoRowItemRenderer, fun fromMusicResponsiveListItemRenderer, fun parseArtists, val artists |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/MoodAndGenres.kt` | 47 | class MoodAndGenres, val title, val items, class Item, val title, val stripeColor, val endpoint, fun fromSectionListRendererContent, fun fromMusicNavigationButtonRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewPipe.kt` | 133 | class NewPipeDownloaderImpl, val client, val httpMethod, val url, val headers, val dataToSend, val requestBuilder, val response, val responseBodyToReturn, val latestUrl, object NewPipeUtils, fun getSignatureTimestamp |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NewReleaseAlbumPage.kt` | 44 | object NewReleaseAlbumPage, fun fromMusicTwoRowItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt` | 76 | class NextResult, val title, val items, val currentIndex, val lyricsEndpoint, val relatedEndpoint, val continuation, val endpoint, object NextPage, fun fromPlaylistPanelVideoRenderer, val longByLineRuns |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PageHelper.kt` | 38 | object PageHelper, fun extractRuns, val filteredRuns, val runs, val typeStr, fun extractFeedbackToken, val defaultToken, val toggledToken |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt` | 8 | class PlaylistContinuationPage, val songs, val continuation |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt` | 52 | class PlaylistPage, val playlist, val songs, val songsContinuation, val continuation, fun fromMusicResponsiveListItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/RelatedPage.kt` | 163 | class RelatedPage, val songs, val albums, val artists, val playlists, fun fromMusicResponsiveListItemRenderer, fun fromMusicTwoRowItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt` | 210 | class SearchResult, val items, val continuation, object SearchPage, fun toYTItem, val secondaryLine |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSuggestionPage.kt` | 146 | object SearchSuggestionPage, fun fromMusicResponsiveListItemRenderer, val secondaryLine |
| `innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSummaryPage.kt` | 377 | class SearchSummary, val title, val items, class SearchSummaryPage, val summaries, fun filterExplicit, fun fromMusicCardShelfRenderer, val subtitle, fun fromMusicResponsiveListItemRenderer, val secondaryLine, val thirdLine, val listRun |

## Model and response inventory

| File | Lines | Key declarations |
| --- | ---: | --- |
| `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` | 705 | class InnerTube, var httpClient, var locale, var visitorData, var dataSyncId, var cookie, var cookieMap, var proxy, var proxyAuth, var useLoginForBrowse, fun createClient, fun HttpRequestBuilder |
| `innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` | 1246 | object YouTube, val innerTube, var locale, var visitorData, var dataSyncId, var cookie, var proxy, var proxyAuth, var useLoginForBrowse, val response, val response, val contents |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AccountInfo.kt` | 8 | class AccountInfo, val name, val email, val channelHandle, val thumbnailUrl |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/AutomixPreviewVideoRenderer.kt` | 18 | class AutomixPreviewVideoRenderer, val content, class Content, val automixPlaylistVideoRenderer, class AutomixPlaylistVideoRenderer, val navigationEndpoint |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Badges.kt` | 13 | class Badges, val musicInlineBadgeRenderer, class MusicInlineBadgeRenderer, val icon |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Button.kt` | 16 | class Button, val buttonRenderer, class ButtonRenderer, val text, val navigationEndpoint, val command, val icon |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Context.kt` | 60 | class Context, val client, val thirdParty, val request, val user, class Client, val clientName, val clientVersion, val osName, val osVersion, val deviceMake, val deviceModel |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Continuation.kt` | 20 | class Continuation, val nextContinuationData, class NextContinuationData, val continuation, fun List |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ContinuationItemRenderer.kt` | 18 | class ContinuationItemRenderer, val continuationEndpoint, class ContinuationEndpoint, val continuationCommand, class ContinuationCommand, val token |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Endpoint.kt` | 118 | class Endpoint, class WatchEndpoint, val videoId, val playlistId, val playlistSetVideoId, val params, val index, val watchEndpointMusicSupportedConfigs, class WatchEndpointMusicSupportedConfigs, val watchEndpointMusicConfig, class WatchEndpointMusicConfig, val musicVideoType |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/GridRenderer.kt` | 26 | class GridRenderer, val header, val items, val continuations, class Header, val gridHeaderRenderer, class GridHeaderRenderer, val title, class Item, val musicNavigationButtonRenderer, val musicTwoRowItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Icon.kt` | 8 | class Icon, val iconType |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MediaInfo.kt` | 15 | class MediaInfo, val videoId, val title, val author, val authorId, val authorThumbnail, val description, val uploadDate, val subscribers, val viewCount, val like, val dislike |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Menu.kt` | 52 | class Menu, val menuRenderer, class MenuRenderer, val items, val topLevelButtons, class Item, val menuNavigationItemRenderer, val menuServiceItemRenderer, val toggleMenuServiceItemRenderer, class MenuNavigationItemRenderer, val text, val icon |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCardShelfRenderer.kt` | 30 | class MusicCardShelfRenderer, val title, val subtitle, val thumbnail, val header, val contents, val buttons, val onTap, val subtitleBadges, class Header, val musicCardShelfHeaderBasicRenderer, class MusicCardShelfHeaderBasicRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCarouselShelfRenderer.kt` | 31 | class MusicCarouselShelfRenderer, val header, val contents, val itemSize, val numItemsPerColumn, class Header, val musicCarouselShelfBasicHeaderRenderer, class MusicCarouselShelfBasicHeaderRenderer, val strapline, val title, val thumbnail, val moreContentButton |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicDescriptionShelfRenderer.kt` | 11 | class MusicDescriptionShelfRenderer, val header, val subheader, val description, val footer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicEditablePlaylistDetailHeaderRenderer.kt` | 35 | class MusicEditablePlaylistDetailHeaderRenderer, val header, val editHeader, class Header, val musicDetailHeaderRenderer, val musicResponsiveHeaderRenderer, class EditHeader, val musicPlaylistEditHeaderRenderer, class MusicDetailHeaderRenderer, val title, val subtitle, val secondSubtitle |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicNavigationButtonRenderer.kt` | 21 | class MusicNavigationButtonRenderer, val buttonText, val solid, val iconStyle, val clickCommand, class Solid, val leftStripeColor, class IconStyle, val icon |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicPlaylistShelfRenderer.kt` | 11 | class MusicPlaylistShelfRenderer, val playlistId, val contents, val collapsedItemCount, val continuations |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicQueueRenderer.kt` | 25 | class MusicQueueRenderer, val content, val header, class Content, val playlistPanelRenderer, class Header, val musicQueueHeaderRenderer, class MusicQueueHeaderRenderer, val title, val subtitle |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveHeaderRenderer.kt` | 24 | class MusicResponsiveHeaderRenderer, val thumbnail, val buttons, val title, val subtitle, val secondSubtitle, val straplineTextOne, class Button, val musicPlayButtonRenderer, val menuRenderer, class MusicPlayButtonRenderer, val playNavigationEndpoint |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveListItemRenderer.kt` | 98 | class MusicResponsiveListItemRenderer, val badges, val fixedColumns, val flexColumns, val thumbnail, val menu, val playlistItemData, val overlay, val navigationEndpoint, val isSong, val isPlaylist, val isAlbum |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicShelfRenderer.kt` | 28 | class MusicShelfRenderer, val title, val contents, val continuations, val bottomEndpoint, val moreContentButton, class Content, val musicResponsiveListItemRenderer, val continuationItemRenderer, fun List, fun List |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/MusicTwoRowItemRenderer.kt` | 52 | class MusicTwoRowItemRenderer, val title, val subtitle, val subtitleBadges, val menu, val thumbnailRenderer, val navigationEndpoint, val thumbnailOverlay, val isSong, val isPlaylist, val isAlbum, val isArtist |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/NavigationEndpoint.kt` | 27 | class NavigationEndpoint, val watchEndpoint, val watchPlaylistEndpoint, val browseEndpoint, val searchEndpoint, val queueAddEndpoint, val shareEntityEndpoint, val feedbackEndpoint, val endpoint, val anyWatchEndpoint |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistDeleteBody.kt` | 10 | class PlaylistDeleteBody, val context, val playlistId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt` | 21 | class PlaylistPanelRenderer, val title, val titleText, val shortBylineText, val contents, val isInfinite, val numItemsToShow, val playlistId, val continuations, class Content, val playlistPanelVideoRenderer, val automixPreviewVideoRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoRenderer.kt` | 19 | class PlaylistPanelVideoRenderer, val title, val lengthText, val longBylineText, val shortBylineText, val badges, val videoId, val playlistSetVideoId, val selected, val thumbnail, val unplayableText, val menu |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ResponseContext.kt` | 21 | class ResponseContext, val visitorData, val serviceTrackingParams, class ServiceTrackingParam, val params, val service, class Param, val key, val value |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt` | 14 | class ReturnYouTubeDislikeResponse, val id, val dateCreated, val likes, val dislikes, val rating, val viewCount, val deleted |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Runs.kt` | 43 | class Runs, val runs, class Run, val text, val navigationEndpoint, fun List, val res, var tmp, fun List, fun List |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestions.kt` | 6 | class SearchSuggestions, val queries, val recommendedItems |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestionsSectionRenderer.kt` | 20 | class SearchSuggestionsSectionRenderer, val contents, class Content, val searchSuggestionRenderer, val musicResponsiveListItemRenderer, class SearchSuggestionRenderer, val suggestion, val navigationEndpoint |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SectionListRenderer.kt` | 73 | class SectionListRenderer, val header, val contents, val continuations, class Header, val chipCloudRenderer, class ChipCloudRenderer, val chips, class Chip, val chipCloudChipRenderer, class ChipCloudChipRenderer, val isSelected |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/SubscriptionButton.kt` | 14 | class SubscriptionButton, val subscribeButtonRenderer, class SubscribeButtonRenderer, val subscribed, val channelId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Tabs.kt` | 26 | class Tabs, val tabs, class Tab, val tabRenderer, class TabRenderer, val title, val content, val endpoint, class Content, val sectionListRenderer, val musicQueueRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/ThumbnailRenderer.kt` | 29 | class ThumbnailRenderer, val musicThumbnailRenderer, val musicAnimatedThumbnailRenderer, val croppedSquareThumbnailRenderer, class MusicThumbnailRenderer, val thumbnail, val thumbnailCrop, val thumbnailScale, fun getThumbnailUrl, class MusicAnimatedThumbnailRenderer, val animatedThumbnail, val backupRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/Thumbnails.kt` | 15 | class Thumbnails, val thumbnails, class Thumbnail, val url, val width, val height |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/TwoColumnBrowseResultsRenderer.kt` | 26 | class TwoColumnBrowseResultsRenderer, val secondaryContents, val tabs, class SecondaryContents, val sectionListRenderer, class SectionListRenderer, val contents, val continuations, class Content, val musicPlaylistShelfRenderer, val musicShelfRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` | 92 | class YTItem, val id, val title, val thumbnail, val explicit, val shareLink, class Artist, val name, val id, class Album, val name, val id |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt` | 256 | class YouTubeClient, val clientName, val clientVersion, val clientId, val userAgent, val osName, val osVersion, val deviceMake, val deviceModel, val androidSdkVersion, val buildId, val cronetVersion |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeDataPage.kt` | 183 | class YouTubeDataPage, val contents, class Contents, val twoColumnWatchNextResults, class TwoColumnWatchNextResults, val results, class Results, val results, class Results, val content, class Content, val videoPrimaryInfoRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeLocale.kt` | 9 | class YouTubeLocale, val gl, val hl |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/AccountMenuBody.kt` | 11 | class AccountMenuBody, val context, val deviceTheme, val userInterfaceTheme |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/BrowseBody.kt` | 13 | class BrowseBody, val context, val browseId, val params, val continuation |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/CreatePlaylistBody.kt` | 18 | class CreatePlaylistBody, val context, val title, val privacyStatus, val videoIds, object PrivacyStatus |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/EditPlaylistBody.kt` | 79 | class EditPlaylistBody, val context, val playlistId, val actions, class Action, class AddVideoAction, val action, val addedVideoId, class AddPlaylistAction, val action, val addedFullListId, class MoveVideoAction |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/FeedbackBody.kt` | 12 | class FeedbackBody, val context, val feedbackTokens, val isFeedbackTokenUnencrypted, val shouldMerge |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetQueueBody.kt` | 11 | class GetQueueBody, val context, val videoIds, val playlistId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetSearchSuggestionsBody.kt` | 10 | class GetSearchSuggestionsBody, val context, val input |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetTranscriptBody.kt` | 10 | class GetTranscriptBody, val context, val params |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/LikeBody.kt` | 18 | class LikeBody, val context, val target, class Target, class VideoTarget, class PlaylistTarget |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/NextBody.kt` | 15 | class NextBody, val context, val videoId, val playlistId, val playlistSetVideoId, val index, val params, val continuation |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/PlayerBody.kt` | 30 | class PlayerBody, val context, val videoId, val playlistId, val playbackContext, val serviceIntegrityDimensions, val contentCheckOk, val racyCheckOk, class PlaybackContext, val contentPlaybackContext, class ContentPlaybackContext, val signatureTimestamp |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SearchBody.kt` | 11 | class SearchBody, val context, val query, val params |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/body/SubscribeBody.kt` | 10 | class SubscribeBody, val channelIds, val context |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountMenuResponse.kt` | 53 | class AccountMenuResponse, val actions, class Action, val openPopupAction, class OpenPopupAction, val popup, class Popup, val multiPageMenuRenderer, class MultiPageMenuRenderer, val header, class Header, val activeAccountHeaderRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/AddItemYouTubePlaylistResponse.kt` | 20 | class AddItemYouTubePlaylistResponse, val status, val playlistEditResults, class PlaylistEditResult, val playlistEditVideoAddedResultData, class PlaylistEditVideoAddedResultData, val setVideoId, val videoId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/BrowseResponse.kt` | 142 | class BrowseResponse, val contents, val continuationContents, val onResponseReceivedActions, val header, val microformat, val responseContext, val background, class Contents, val singleColumnBrowseResultsRenderer, val sectionListRenderer, val twoColumnBrowseResultsRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ContinuationResponse.kt` | 20 | class ContinuationResponse, val onResponseReceivedActions, class ResponseAction, val appendContinuationItemsAction, class ContinuationItems, val continuationItems |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/CreatePlaylistResponse.kt` | 8 | class CreatePlaylistResponse, val playlistId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/EditPlaylistResponse.kt` | 8 | class EditPlaylistResponse, val newHeader |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/FeedbackResponse.kt` | 13 | class FeedbackResponse, val feedbackResponses, class Status, val isProcessed |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetQueueResponse.kt` | 14 | class GetQueueResponse, val queueDatas, class QueueData, val content |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetSearchSuggestionsResponse.kt` | 14 | class GetSearchSuggestionsResponse, val contents, class Content, val searchSuggestionsSectionRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetTranscriptResponse.kt` | 65 | class GetTranscriptResponse, val actions, class Action, val updateEngagementPanelAction, class UpdateEngagementPanelAction, val content, class Content, val transcriptRenderer, class TranscriptRenderer, val body, class Body, val transcriptBodyRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/ImageUploadResponse.kt` | 8 | class ImageUploadResponse, val encryptedBlobId |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/NextResponse.kt` | 40 | class NextResponse, val contents, val continuationContents, val currentVideoEndpoint, class Contents, val singleColumnMusicWatchNextResultsRenderer, val twoColumnWatchNextResults, class SingleColumnMusicWatchNextResultsRenderer, val tabbedRenderer, class TabbedRenderer, val watchNextTabbedResultsRenderer, class WatchNextTabbedResultsRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/PlayerResponse.kt` | 117 | class PlayerResponse, val responseContext, val playabilityStatus, val playerConfig, val streamingData, val videoDetails, val playbackTracking, class PlayabilityStatus, val status, val reason, class PlayerConfig, val audioConfig |
| `innertube/src/main/kotlin/com/metrolist/innertube/models/response/SearchResponse.kt` | 33 | class SearchResponse, val contents, val continuationContents, class Contents, val tabbedSearchResultsRenderer, class ContinuationContents, val musicShelfContinuation, class MusicShelfContinuation, val contents, val continuations, class Content, val musicResponsiveListItemRenderer |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/ResilientDns.kt` | 84 | class ResilientDns, val bootstrapGoogle, val bootstrapCloudflare, val bootstrapQuad9, val dohClients, val systemAddresses, val usable, val dohResult, val dohUsable |
| `innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt` | 95 | val page, val songs, var continuation, val seenContinuations, var requestCount, val maxRequests, val continuationPage, val page, val items, var continuation, val seenContinuations, var requestCount |
