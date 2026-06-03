# UI documentation

## UI stack facts

- The app module enables Jetpack Compose in `buildFeatures`.
- UI source is under `app/src/main/kotlin/com/jtech/zemer/ui`.
- The main package groups are `component`, `component/shimmer`, `menu`, `player`, `screens`, `screens/artist`, `screens/library`, `screens/player`, `screens/playlist`, `screens/search`, `screens/settings`, `screens/settings/integrations`, `theme`, and `utils`.
- Navigation uses `androidx.navigation.compose.composable` routes inside `NavigationBuilder.kt`.
- The main bottom navigation set is `Home`, `Artists`, `KidZone`, `Search`, and `Library` from `Screens.MainScreens`.

## Main screen model

| Screen object | Route | Title resource | Inactive icon | Active icon |
| --- | --- | --- | --- | --- |
| `Screens.Home` | `home` | `R.string.home` | `R.drawable.home_outlined` | `R.drawable.home_filled` |
| `Screens.Artists` | `artists` | `R.string.artists` | `R.drawable.artist` | `R.drawable.artist` |
| `Screens.KidZone` | `kid_zone` | `R.string.kid_zone` | `R.drawable.kid_zone` | `R.drawable.kid_zone` |
| `Screens.Search` | `search` | `R.string.search` | `R.drawable.search` | `R.drawable.search` |
| `Screens.Library` | `library` | `R.string.filter_library` | `R.drawable.library_music_outlined` | `R.drawable.library_music_filled` |

## Navigation routes declared in `NavigationBuilder.kt`

| Route | Destination function |
| --- | --- |
| `home` | `HomeScreen` |
| `artists` | `WhitelistedArtistsScreen` |
| `kid_zone` | `KidZoneScreen` |
| `library` | `LibraryScreen` |
| `history` | `HistoryScreen` |
| `stats` | `StatsScreen` |
| `mood_and_genres` | `MoodAndGenresScreen` |
| `account` | `AccountScreen` |
| `new_release` | `NewReleaseScreen` |
| `charts_screen` | `ChartsScreen` |
| `browse/{browseId}` | `BrowseScreen` |
| `search/{query}?filter={filter}` | `OnlineSearchResult` |
| `album/{albumId}` | `AlbumScreen` |
| `artist/{artistId}` | `ArtistScreen` |
| `artist/{artistId}/songs` | `ArtistSongsScreen` |
| `artist/{artistId}/albums` | `ArtistAlbumsScreen` |
| `artist/{artistId}/items?browseId={browseId}?params={params}` | `ArtistItemsScreen` |
| `video/{videoId}?title={title}&artist={artist}` | `VideoPlayerScreen` |
| `online_playlist/{playlistId}` | `OnlinePlaylistScreen` |
| `local_playlist/{playlistId}` | `LocalPlaylistScreen` |
| `auto_playlist/{playlist}` | `AutoPlaylistScreen` |
| `cache_playlist/{playlist}` | `CachePlaylistScreen` |
| `downloaded_content` | `DownloadedContentScreen` |
| `downloaded_videos` | `DownloadedVideosScreen` |
| `top_playlist/{top}` | `TopPlaylistScreen` |
| `youtube_browse/{browseId}?params={params}` | `YouTubeBrowseScreen` |
| `settings` | `SettingsScreen` |
| `settings/appearance` | `AppearanceSettings` |
| `settings/content` | `ContentSettings` |
| `settings/player` | `PlayerSettings` |
| `settings/general` | `GeneralSettings` |
| `settings/dpad` | `ButtonSetupScreen` |
| `settings/storage` | `StorageSettings` |
| `settings/privacy` | `PrivacySettings` |
| `settings/backup_restore` | `BackupAndRestore` |
| `settings/integrations` | `IntegrationScreen` |
| `settings/updater` | `UpdaterScreen` |
| `settings/about` | `AboutScreen` |
| `login` | `LoginScreen` |
| `login_gate` | `LoginGateScreen` |

## Screen groups

| Group | Files | Observable responsibility |
| --- | --- | --- |
| Root screens | `AccountScreen`, `AlbumScreen`, `BrowseScreen`, `ChartsScreen`, `ExploreScreen`, `HistoryScreen`, `HomeScreen`, `KidZoneScreen`, `LoginGateScreen`, `LoginScreen`, `MoodAndGenresScreen`, `NewReleaseScreen`, `OnboardingScreen`, `SplashScreen`, `StatsScreen`, `WhitelistedArtistsScreen`, `YouTubeBrowseScreen` | Top-level and feature screens wired from navigation or startup/auth flows. |
| Artist screens | `ArtistScreen`, `ArtistSongsScreen`, `ArtistAlbumsScreen`, `ArtistItemsScreen` | Artist detail, song, album, and extra item views. |
| Library screens | `LibraryScreen`, `LibrarySongsScreen`, `LibraryAlbumsScreen`, `LibraryArtistsScreen`, `LibraryPlaylistsScreen`, `LibraryMixScreen`, `LibraryVideosScreen` | Local/library tabs and media groupings. |
| Playlist screens | `AutoPlaylistScreen`, `CachePlaylistScreen`, `DownloadedContentScreen`, `DownloadedVideosScreen`, `LocalPlaylistScreen`, `OnlinePlaylistScreen`, `TopPlaylistScreen` | Playlist, cache, downloaded, online, and ranked media views. |
| Search screens | `OnlineSearchScreen`, `OnlineSearchResult` | Search suggestions and search result presentation. |
| Settings screens | `SettingsScreen`, `AccountSettings`, `AppearanceSettings`, `BackupAndRestore`, `ButtonSetupScreen`, `ContentSettings`, `ContributeScreen`, `GeneralSettings`, `PlayerSettings`, `PrivacySettings`, `StorageSettings`, `UpdaterSettings`, `integrations/IntegrationScreen` | Settings hub and individual settings/detail flows. |
| Player UI | `player/*.kt`, `screens/player/VideoPlayerScreen.kt` | Now-playing surface, lyrics, queue, thumbnails, controls, and video playback screen. |
| Reusable components | `component/*.kt`, `component/shimmer/*.kt`, `menu/*.kt`, `utils/*.kt` | Cards, list/grid rows, dialogs, menus, app bars, layout utilities, shimmer placeholders, and support components. |
| Theme | `theme/*.kt` | Compose colors, typography/theme wrapper, slider colors, and dynamic color helpers. |

## Composable inventory

| File | Lines | Composable declarations found |
| --- | ---: | --- |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AccountSettingsDialog.kt` | 66 | AccountSettingsDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AnonymousAuthEmailDialog.kt` | 121 | AnonymousAuthEmailDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppStateViews.kt` | 111 | AppStateView |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AutoResizeText.kt` | 97 | AutoResizeText |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BigSeekBar.kt` | 58 | BigSeekBar |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheet.kt` | 348 | BottomSheet, rememberBottomSheetState |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetMenu.kt` | 86 | BottomSheetMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetPage.kt` | 166 | BottomSheetPage |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CreatePlaylistDialog.kt` | 129 | CreatePlaylistDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Dialog.kt` | 367 | DefaultDialog, ActionPromptDialog, ListDialog, InfoLabel, TextFieldDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableScrollBarOverlay.kt` | 242 | DraggableScrollbar |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/EmptyPlaceholder.kt` | 47 | EmptyPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GridMenu.kt` | 197 | GridMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HideOnScrollFAB.kt` | 117 | BoxScope, BoxScope, BoxScope |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconButton.kt` | 118 | ResizableIconButton, IconButton |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Items.kt` | 1619 | ListItem, GridItem, GridItem, SongListItem, SongGridItem, ArtistListItem, ArtistGridItem, AlbumListItem, AlbumGridItem, PlaylistListItem, PlaylistGridItem, MediaMetadataListItem, YouTubeListItem, YouTubeGridItem, LocalSongsGrid, LocalArtistsGrid, LocalAlbumsGrid, ItemThumbnail, LocalThumbnail, PlaylistThumbnail, BoxScope, BoxScope, BoxScope, SwipeToSongBox, Favorite, Library, Download, Download, Explicit |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 410 | LibraryArtistListItem, WhitelistedArtistListItem, LibraryArtistGridItem, WhitelistedArtistGridItem, LibraryAlbumListItem, LibraryAlbumGridItem, LibraryPlaylistListItem, LibraryPlaylistGridItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Lyrics.kt` | 1013 | Lyrics |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LyricsImageCard.kt` | 287 | rememberAdjustedFontSize, LyricsImageCard |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3SettingsGroup.kt` | 215 | Material3SettingsGroup, Material3SettingsItemRow |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTile.kt` | 59 | NavigationTile |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTitle.kt` | 77 | NavigationTitle |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NetworkRequiredDialog.kt` | 98 | NetworkRequiredDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NewMenuComponents.kt` | 322 | NewActionButton, NewMenuItem, NewMenuSectionHeader, NewActionGrid, NewMenuContent, NewIconButton, NewMenuContainer |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayerSlider.kt` | 112 | PlayerSliderTrack |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayingIndicator.kt` | 113 | PlayingIndicator, PlayingIndicatorBox |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Preference.kt` | 367 | PreferenceEntry, SwitchPreference, EditTextPreference, SliderPreference, PreferenceGroupTitle |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchBar.kt` | 367 | TopSearch, SearchBarInputField |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/WebViewAuthDialog.kt` | 129 | WebViewAuthDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ButtonPlaceholder.kt` | 21 | ButtonPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/GridItemPlaceholder.kt` | 56 | GridItemPlaceHolder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ListItemPlaceholder.kt` | 53 | ListItemPlaceHolder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ShimmerHost.kt` | 64 | ShimmerHost |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/TextPlaceholder.kt` | 33 | TextPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialog.kt` | 191 | AddToPlaylistDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialogOnline.kt` | 283 | AddToPlaylistDialogOnline |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AlbumMenu.kt` | 676 | AlbumMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ArtistMenu.kt` | 364 | ArtistMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/CustomThumbnailMenu.kt` | 70 | CustomThumbnailMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ImportPlaylistDialog.kt` | 63 | ImportPlaylistDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LoadingScreen.kt` | 35 | LoadingScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LyricsMenu.kt` | 378 | LyricsMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlayerMenu.kt` | 725 | PlayerMenu, TempoPitchDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlaylistMenu.kt` | 332 | PlaylistMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SelectionSongsMenu.kt` | 902 | SelectionSongMenu, SelectionMediaMetadataMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SongMenu.kt` | 862 | SongMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeAlbumMenu.kt` | 610 | YouTubeAlbumMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeArtistMenu.kt` | 319 | YouTubeArtistMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubePlaylistMenu.kt` | 561 | YouTubePlaylistMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeSongMenu.kt` | 750 | YouTubeSongMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/LyricsScreen.kt` | 796 | LyricsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/MiniPlayer.kt` | 900 | MiniPlayer, NewMiniPlayer, LegacyMiniPlayer, LegacyMiniMediaInfo |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlaybackError.kt` | 45 | PlaybackError |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Player.kt` | 1364 | BottomSheetPlayer, BottomSheetPlayerPreview |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Queue.kt` | 1131 | Queue |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Thumbnail.kt` | 472 | Thumbnail |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AccountScreen.kt` | 200 | AccountScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AlbumScreen.kt` | 723 | AlbumScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/BrowseScreen.kt` | 143 | BrowseScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ChartsScreen.kt` | 307 | ChartsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ExploreScreen.kt` | 400 | ExploreScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HistoryScreen.kt` | 518 | HistoryScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeScreen.kt` | 1037 | HomeScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 336 | KidZoneScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginGateScreen.kt` | 253 | LoginGateScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginScreen.kt` | 227 | LoginScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/MoodAndGenresScreen.kt` | 171 | MoodAndGenresScreen, MoodAndGenresButton |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NewReleaseScreen.kt` | 254 | NewReleaseScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 2077 | NetworkStatusBanner, OnboardingFlow, WelcomeScreen, DensityScreen, RestartDialog, CustomDensityDialog, ContentFiltersScreen, FilterOptionCard, PermissionsScreen, PermissionCard, LegalOverlay, LoadingScreen, DisposableLifecycle, DisposableEffectWithLifecycle, BottomNavSetupScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 165 | SplashScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/StatsScreen.kt` | 426 | StatsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 404 | WhitelistedArtistsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/YouTubeBrowseScreen.kt` | 284 | YouTubeBrowseScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistAlbumsScreen.kt` | 157 | ArtistAlbumsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistItemsScreen.kt` | 329 | ArtistItemsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistScreen.kt` | 858 | ArtistScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSongsScreen.kt` | 211 | ArtistSongsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryAlbumsScreen.kt` | 324 | LibraryAlbumsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryArtistsScreen.kt` | 302 | LibraryArtistsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryMixScreen.kt` | 797 | LibraryMixScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPlaylistsScreen.kt` | 545 | LibraryPlaylistsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryScreen.kt` | 87 | LibraryScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibrarySongsScreen.kt` | 356 | LibrarySongsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryVideosScreen.kt` | 156 | LibraryVideosScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/player/VideoPlayerScreen.kt` | 1114 | VideoPlayerScreen, formatTime |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/AutoPlaylistScreen.kt` | 680 | AutoPlaylistScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/CachePlaylistScreen.kt` | 490 | CachePlaylistScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedContentScreen.kt` | 182 | DownloadedContentScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedVideosScreen.kt` | 510 | DownloadedVideosScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1503 | LocalPlaylistScreen, LocalPlaylistHeader |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/OnlinePlaylistScreen.kt` | 730 | OnlinePlaylistScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/TopPlaylistScreen.kt` | 631 | TopPlaylistScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchResult.kt` | 441 | OnlineSearchResult |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchScreen.kt` | 458 | OnlineSearchScreen, SuggestionItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AboutScreen.kt` | 191 | AboutScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AccountSettings.kt` | 453 | AccountSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AppearanceSettings.kt` | 1028 | AppearanceSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/BackupAndRestore.kt` | 190 | BackupAndRestore |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ButtonSetupScreen.kt` | 374 | ButtonSetupScreen, ListeningOverlay, CompletedCard, AssignmentRow, DpadDirectionIcon, PrimingOverlay, AccessibilityPermissionRequired |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 681 | ContentSettings, SyncStatusCard |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | 388 | ProgressRow, ProfilePrompt, ArtistTaskCard, ToggleRow |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/GeneralSettings.kt` | 88 | GeneralSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PlayerSettings.kt` | 246 | PlayerSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PrivacySettings.kt` | 208 | PrivacySettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/SettingsScreen.kt` | 219 | SettingsScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StorageSettings.kt` | 440 | StorageSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/UpdaterSettings.kt` | 278 | UpdaterScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/integrations/IntegrationScreen.kt` | 49 | IntegrationScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerSliderColors.kt` | 146 | getSliderColors, defaultSliderColors, squigglySliderColors, slimSliderColors |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Theme.kt` | 113 | ZemerTheme |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppBar.kt` | 75 | appBarScrollBehavior |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ScrollUtils.kt` | 59 | LazyListState, LazyGridState, ScrollState |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShowMediaInfo.kt` | 342 | ShowMediaInfo |

## UI Kotlin file inventory

| File | Lines | Key declarations |
| --- | ---: | --- |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AccountSettingsDialog.kt` | 66 | fun AccountSettingsDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AnonymousAuthEmailDialog.kt` | 121 | fun AnonymousAuthEmailDialog, val coroutineScope, var isLoading |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AppStateViews.kt` | 111 | fun AppStateView |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/AutoResizeText.kt` | 97 | fun AutoResizeText, var fontSizeValue, var readyToDraw, val nextFontSizeValue, class FontSizeRange, val min, val max, val step, val DEFAULT_TEXT_STEP |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BigSeekBar.kt` | 58 | fun BigSeekBar, var width |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheet.kt` | 348 | fun BottomSheet, val y, val velocityTracker, val velocity, class BottomSheetState, val coroutineScope, val animatable, val onAnchorChanged, val collapsedBound, val dismissedBound, val expandedBound, val value, val isDismissed, val isCollapsed |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetMenu.kt` | 86 | val LocalMenuState, class MenuState, var isVisible, var content, fun show, fun dismiss, fun BottomSheetMenu, val focusManager |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/BottomSheetPage.kt` | 166 | val LocalBottomSheetPageState, class BottomSheetPageState, var isVisible, var content, fun show, fun dismiss, fun BottomSheetPage, val focusManager, var dragOffset |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/ChipsRow.kt` | 247 | var isFocused, val borderColor, var expandIconDegree, val rotationAnimation, var expanded, var isFocused, val borderColor, var isFocused, val borderColor |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/CreatePlaylistDialog.kt` | 129 | fun CreatePlaylistDialog, val database, val coroutineScope, var syncedPlaylist, val context, val isSignedIn, val isSyncEnabled, val browseId |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Dialog.kt` | 367 | fun DefaultDialog, fun ActionPromptDialog, fun ListDialog, fun InfoLabel, fun TextFieldDialog, val legacyFieldState, val focusRequester, val isValid |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/DraggableScrollBarOverlay.kt` | 242 | fun DraggableScrollbar, val density, val coroutineScope, var isDragging, var lastScrollTime, var smoothedY, var smoothedThumbY, var lastThumbPosition, val animatedThumbY, val isUserScrolling, val isScrollable, val layoutInfo, val total, val visible |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/EmptyPlaceholder.kt` | 47 | fun EmptyPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/GridMenu.kt` | 197 | val GridMenuItemHeight, fun GridMenu, fun LazyGridScope, fun LazyGridScope, fun LazyGridScope, fun LazyGridScope |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/HideOnScrollFAB.kt` | 117 | fun BoxScope, fun BoxScope, fun BoxScope |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/IconButton.kt` | 118 | fun ResizableIconButton, val isFocused, val borderColor, val bgColor, fun IconButton, val contentColor |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Items.kt` | 1619 | var isFocused, val backgroundColor, val borderColor, fun ListItem, fun GridItem, var isFocused, val backgroundColor, val borderColor, val baseModifier, fun GridItem, fun SongListItem, val downloadState, val swipeEnabled, val content |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Library.kt` | 410 | fun LibraryArtistListItem, fun WhitelistedArtistListItem, fun LibraryArtistGridItem, fun WhitelistedArtistGridItem, fun LibraryAlbumListItem, fun LibraryAlbumGridItem, fun LibraryPlaylistListItem, fun LibraryPlaylistGridItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Lyrics.kt` | 1013 | fun Lyrics, val playerConnection, val density, val context, val configuration, val landscapeOffset, val lyricsTextPosition, val changeLyrics, val scrollLyrics, val scope, val mediaMetadata, val lyricsEntity, val lyrics, val playerBackground |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/LyricsImageCard.kt` | 287 | fun rememberAdjustedFontSize, val measurer, var calculatedFontSize, val initialSize, val targetWidthPx, val targetHeightPx, val largerSize, val result, val largerSize, val result, var minSize, var maxSize, var bestFit, var iterations |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Material3SettingsGroup.kt` | 215 | fun Material3SettingsGroup, fun Material3SettingsItemRow, var isFocused, val backgroundColor, val borderColor, class Material3SettingsItem, val icon, val title, val description, val trailingContent, val showBadge, val isHighlighted, val onClick |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTile.kt` | 59 | fun NavigationTile |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NavigationTitle.kt` | 77 | fun NavigationTitle |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NetworkRequiredDialog.kt` | 98 | fun NetworkRequiredDialog, val context, var isRetrying, var currentConnectionState |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/NewMenuComponents.kt` | 322 | fun NewActionButton, var isFocused, val animatedBackground, val animatedContent, val borderColor, fun NewMenuItem, var isFocused, val backgroundColor, val borderColor, fun NewMenuSectionHeader, fun NewActionGrid, val rows, class NewAction, val icon |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayerSlider.kt` | 112 | fun PlayerSliderTrack, val inactiveTrackColor, val activeTrackColor, val inactiveTickColor, val activeTickColor, val valueRange, fun DrawScope, val isRtl, val sliderLeft, val sliderRight, val sliderStart, val sliderEnd, val tickSize, val trackStrokeWidth |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/PlayingIndicator.kt` | 113 | fun PlayingIndicator, val animatables, fun PlayingIndicatorBox |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/Preference.kt` | 367 | fun PreferenceEntry, var isFocused, val backgroundColor, val borderColor, var showDialog, fun SwitchPreference, fun EditTextPreference, var showDialog, fun SliderPreference, var showDialog, var sliderValue, fun PreferenceGroupTitle |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SearchBar.kt` | 367 | fun TopSearch, val animationProgress, val defaultInputFieldShape, val defaultFullScreenShape, val animatedShape, val animatedRadius, val topInset, val startInset, val endInset, val topPadding, val animatedSurfaceTopPadding, val animatedInputFieldPadding, val height, val width |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/SortHeader.kt` | 109 | var menuExpanded |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/WebViewAuthDialog.kt` | 129 | fun WebViewAuthDialog, val context, val coroutineScope, var isLoading |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ButtonPlaceholder.kt` | 21 | fun ButtonPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/GridItemPlaceholder.kt` | 56 | fun GridItemPlaceHolder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ListItemPlaceholder.kt` | 53 | fun ListItemPlaceHolder |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/ShimmerHost.kt` | 64 | fun ShimmerHost, val ShimmerTheme |
| `app/src/main/kotlin/com/jtech/zemer/ui/component/shimmer/TextPlaceholder.kt` | 33 | fun TextPlaceholder |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialog.kt` | 191 | fun AddToPlaylistDialog, val database, val coroutineScope, var playlists, var showCreatePlaylistDialog, var showDuplicateDialog, var selectedPlaylist, var songIds, var duplicates |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AddToPlaylistDialogOnline.kt` | 283 | fun AddToPlaylistDialogOnline, val database, val coroutineScope, var playlists, var showCreatePlaylistDialog, var showDuplicateDialog, var selectedPlaylist, val songIds, val duplicates, val allArtists, val query, val songList, val firstSong, val progress |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/AlbumMenu.kt` | 676 | fun AlbumMenu, val context, val database, val downloadUtil, val playerConnection, val scope, val libraryAlbum, val album, var songs, val auth, val firestore, var showReportDialog, var selectedReason, var comment |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ArtistMenu.kt` | 364 | fun ArtistMenu, val context, val auth, val firestore, var showReportDialog, var selectedReason, var comment, var isSubmitting, val database, val playerConnection, val artistState, val artist, val reasons, val uid |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/CustomThumbnailMenu.kt` | 70 | fun CustomThumbnailMenu |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/ImportPlaylistDialog.kt` | 63 | fun ImportPlaylistDialog, val database, val coroutineScope, val textFieldValue, var songIds, val newPlaylist, val playlist |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LoadingScreen.kt` | 35 | fun LoadingScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/LyricsMenu.kt` | 378 | fun LyricsMenu, val context, val database, var showEditDialog, var showSearchDialog, var showSearchResultDialog, val searchMediaMetadata, val isNetworkAvailable, val videoId, val results, val isLoading, var expandedItemIndex, val configuration, val isPortrait |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlayerMenu.kt` | 725 | fun PlayerMenu, val context, val auth, val firestore, val database, val playerConnection, val playerVolume, val activityResultLauncher, val coroutineScope, val downloadUtil, val mediaStoreDownload, val download, val artists, var showChoosePlaylistDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/PlaylistMenu.kt` | 332 | fun PlaylistMenu, val context, val database, val downloadUtil, val playerConnection, val auth, val firestore, val dbPlaylist, var songs, var showReportDialog, var selectedReason, var comment, var isSubmitting, val reasons |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SelectionSongsMenu.kt` | 902 | fun SelectionSongMenu, val context, val database, val downloadUtil, val coroutineScope, val playerConnection, val syncUtils, val auth, val firestore, var showReportDialog, var selectedReason, var comment, var isSubmitting, var targetSong |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/SongMenu.kt` | 862 | fun SongMenu, val context, val database, val playerConnection, val songState, val song, val downloadUtil, val mediaStoreDownload, val coroutineScope, val syncUtils, val scope, var refetchIconDegree, var showReportDialog, var selectedReason |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeAlbumMenu.kt` | 610 | fun YouTubeAlbumMenu, val context, val auth, val firestore, val database, val downloadUtil, val playerConnection, val album, val coroutineScope, var downloadState, val songs, var showChoosePlaylistDialog, var showErrorPlaylistAddDialog, var showReportDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeArtistMenu.kt` | 319 | fun YouTubeArtistMenu, val context, val auth, val firestore, val scope, var showReportDialog, var selectedReason, var comment, var isSubmitting, val database, val playerConnection, val libraryArtist, val configuration, val isPortrait |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubePlaylistMenu.kt` | 561 | fun YouTubePlaylistMenu, val context, val database, val downloadUtil, val playerConnection, val dbPlaylist, var showChoosePlaylistDialog, var showImportPlaylistDialog, var showErrorPlaylistAddDialog, val notAddedList, val allSongs, val playlistEntity, val currentPlaylist, var downloadState |
| `app/src/main/kotlin/com/jtech/zemer/ui/menu/YouTubeSongMenu.kt` | 750 | fun YouTubeSongMenu, val context, val auth, val firestore, val database, val playerConnection, val downloadUtil, val librarySong, val mediaStoreDownload, val download, val coroutineScope, val syncUtils, var showReportDialog, var selectedReason |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/LyricsScreen.kt` | 796 | fun LyricsScreen, val context, val activity, val playerConnection, val player, val menuState, val database, val coroutineScope, val playbackState, val isPlaying, val repeatMode, val shuffleModeEnabled, val sliderStyle, val currentLyrics |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/MiniPlayer.kt` | 900 | class MiniPlayerFocusTargets, val play, val account, val heart, val afterHeart, val down, fun Modifier, fun MiniPlayer, val useNewMiniPlayerDesign, fun NewMiniPlayer, val playerConnection, val database, val isPlaying, val playbackState |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/PlaybackError.kt` | 45 | fun PlaybackError |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Player.kt` | 1364 | fun BottomSheetPlayer, val context, val clipboardManager, val menuState, val bottomSheetPageState, val playerConnection, val floatingMiniPlayerEnabled, val playerBackground, val playerButtonsStyle, val isSystemInDarkTheme, val darkTheme, val useDarkTheme, val onBackgroundColor, val useBlackBackground |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Queue.kt` | 1131 | fun Queue, val context, val haptic, val menuState, val bottomSheetPageState, val playerConnection, val isPlaying, val repeatMode, val currentWindowIndex, val mediaMetadata, val selectedSongs, val selectedItems, var selection, var locked |
| `app/src/main/kotlin/com/jtech/zemer/ui/player/Thumbnail.kt` | 472 | fun Thumbnail, val playerConnection, val context, val mediaMetadata, val error, val queueTitle, val swipeThumbnail, val hidePlayerThumbnail, val canSkipPrevious, val canSkipNext, val playerBackground, val textBackgroundColor, val thumbnailLazyGridState, val timeline |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AccountScreen.kt` | 200 | fun AccountScreen, val menuState, val haptic, val coroutineScope, val playlists, val albums, val artists, val selectedContentType |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/AlbumScreen.kt` | 723 | fun AlbumScreen, val context, val menuState, val database, val haptic, val coroutineScope, val playerConnection, val isPlaying, val mediaMetadata, val playlistId, val albumWithSongs, val hideExplicit, val wrappedSongs, val filteredSongs |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/BrowseScreen.kt` | 143 | fun BrowseScreen, val menuState, val playerConnection, val isPlaying, val title, val items, val coroutineScope |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ChartsScreen.kt` | 307 | fun ChartsScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val chartsPage, val isLoading, val lazyListState, val horizontalLazyGridItemWidthFactor, val horizontalLazyGridItemWidth, val horizontalLazyGridItemWidthFactor, val horizontalLazyGridItemWidth |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/ExploreScreen.kt` | 400 | fun ExploreScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val explorePage, val chartsPage, val isChartsLoading, val coroutineScope, val scrollState, val backStackEntry, val scrollToTop |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HistoryScreen.kt` | 518 | fun HistoryScreen, val context, val database, val menuState, val haptic, val playerConnection, val isPlaying, val mediaMetadata, var selection, var isSearching, var query, val focusRequester, val historySource, val historyPage |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/HomeScreen.kt` | 1037 | fun HomeScreen, val viewModel, val menuState, val database, val playerConnection, val haptic, val context, val isPlaying, val mediaMetadata, val homeUiState, val quickPicks, val featuredPlaylists, val trendingSongs, val forgottenFavorites |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/KidZoneScreen.kt` | 336 | fun KidZoneScreen, val menuState, var viewType, val firstFocus, val searchFocus, val firstArtistFocus, val artists, val searchQuery, val syncProgress, val isSyncing, val coroutineScope, var showSyncOverlay, val lazyListState, val lazyGridState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginGateScreen.kt` | 253 | fun LoginGateScreen, val context, val coroutineScope, var isAnonymousLoading, var visitorData, var dataSyncId, var innerTubeCookie, var accountName, var accountEmail, var accountChannelHandle, val gradient, val httpClient, val responseText, val json |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/LoginScreen.kt` | 227 | fun LoginScreen, val context, val coroutineScope, var visitorData, var dataSyncId, var innerTubeCookie, var accountName, var accountEmail, var accountChannelHandle, var hasCompletedLogin, var webView, val url, val blockedUrls, val intent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/MoodAndGenresScreen.kt` | 171 | fun MoodAndGenresScreen, val localConfiguration, val itemsPerRow, val moodAndGenresList, val isLoading, val error, fun MoodAndGenresButton, val MoodAndGenresButtonHeight |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NavigationBuilder.kt` | 338 | fun NavGraphBuilder, val videoId, val title, val artist |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/NewReleaseScreen.kt` | 254 | fun NewReleaseScreen, val menuState, val haptic, val playerConnection, val database, val isPlaying, val mediaMetadata, val newReleaseAlbums, val newReleaseSongs, val isLoading, val error, val coroutineScope |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/OnboardingScreen.kt` | 2077 | class OnboardingStep, class LegalKind, fun NetworkStatusBanner, val context, var isConnected, var isChecking, val newConnectionState, fun OnboardingFlow, val context, val viewModel, val uiState, val densityAlreadySet, val prefs, val contentFiltersAlreadySet |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/Screens.kt` | 53 | class Screens, val route, object Home, object Artists, object KidZone, object Search, object Library, val MainScreens |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/SplashScreen.kt` | 165 | fun SplashScreen, var hasTappedSkip, val composition, val lottieColors, val loopingState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/StatsScreen.kt` | 426 | fun StatsScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val context, val indexChips, val mostPlayedSongs, val mostPlayedSongsStats, val mostPlayedArtists, val mostPlayedAlbums, val firstEvent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/VideoNavigation.kt` | 12 | fun videoRoute, val params, val query |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/WhitelistedArtistsScreen.kt` | 404 | fun WhitelistedArtistsScreen, val menuState, var viewType, val firstFocus, val searchFocus, val firstArtistFocus, val artists, val searchQuery, val syncProgress, val isSyncing, val coroutineScope, var showSyncOverlay, val lazyListState, val lazyGridState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/YouTubeBrowseScreen.kt` | 284 | fun YouTubeBrowseScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val browseResult, val coroutineScope, val horizontalLazyGridItemWidthFactor, val lazyGridState, val snapLayoutInfoProvider |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistAlbumsScreen.kt` | 157 | fun ArtistAlbumsScreen, val menuState, val playerConnection, val isPlaying, val mediaMetadata, val artist, val albums, val coroutineScope, val lazyGridState, var inSelectMode, val selection, val onExitSelectionMode, val snackbarHostState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistItemsScreen.kt` | 329 | fun ArtistItemsScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val lazyListState, val lazyGridState, val coroutineScope, val title, val itemsPage, val isVideoSection, val artistDisplay |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistScreen.kt` | 858 | fun ArtistScreen, val context, val database, val menuState, val haptic, val coroutineScope, val playerConnection, val isPlaying, val mediaMetadata, val artistPage, val isLoadingArtist, val libraryArtist, val librarySongs, val libraryAlbums |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/artist/ArtistSongsScreen.kt` | 211 | fun ArtistSongsScreen, val context, val menuState, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val artist, val songs, val lazyListState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryAlbumsScreen.kt` | 324 | fun LibraryAlbumsScreen, val menuState, val playerConnection, val isPlaying, val mediaMetadata, var viewType, var filter, val gridItemSize, val hideExplicit, val filterContent, val albums, val coroutineScope, val lazyListState, val lazyGridState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryArtistsScreen.kt` | 302 | fun LibraryArtistsScreen, val menuState, var viewType, var filter, val gridItemSize, val filterContent, val artists, val coroutineScope, val lazyListState, val lazyGridState, val backStackEntry, val scrollToTop, val headerContent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryMixScreen.kt` | 797 | fun LibraryMixScreen, val menuState, val haptic, val playerConnection, val isPlaying, val mediaMetadata, var viewType, val gridItemSize, var isSyncing, val context, val topSize, val autoPlaylistsState, val likedPlaylist, val downloadPlaylist |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryPlaylistsScreen.kt` | 545 | fun LibraryPlaylistsScreen, val menuState, val coroutineScope, var viewType, val gridItemSize, val playlists, val topSize, val autoPlaylistsState, val likedPlaylist, val offlineName, val downloadPlaylist, val myTopName, val topPlaylist, val cachedName |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryScreen.kt` | 87 | fun LibraryScreen, var filterType, val availableFilters, val filterContent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibrarySongsScreen.kt` | 356 | fun LibrarySongsScreen, val context, val menuState, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val hideExplicit, val songs, var filter, val wrappedSongs, var selection, val permissionLauncher, val lazyListState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/library/LibraryVideosScreen.kt` | 156 | fun LibraryVideosScreen, val menuState, val haptic, val playerConnection, val database, val isPlaying, val mediaMetadata, val videos, val lazyListState, val artistDisplay |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/player/VideoPlayerScreen.kt` | 1114 | fun VideoPlayerScreen, val context, val activity, val clipboard, val connectivityManager, val database, val scope, val playerConnection, val lifecycleOwner, var videoItem, var playerInstance, var isLoading, var loadError, var showDownloadDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/AutoPlaylistScreen.kt` | 680 | fun AutoPlaylistScreen, val context, val coroutineScope, val menuState, val haptic, val focusManager, val playerConnection, val isPlaying, val mediaMetadata, val playlist, val songs, val mutableSongs, var isSearching, var query |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/CachePlaylistScreen.kt` | 490 | fun CachePlaylistScreen, val menuState, val playerConnection, val haptic, val focusManager, val isPlaying, val mediaMetadata, val cachedSongs, val wrappedSongs, val sortedSongs, var selection, var isSearching, var query, val focusRequester |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedContentScreen.kt` | 182 | fun DownloadedContentScreen, val musicCount, val videoCount |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/DownloadedVideosScreen.kt` | 510 | fun DownloadedVideosScreen, val menuState, val haptic, val focusManager, val playerConnection, val isPlaying, val mediaMetadata, val videos, val mutableVideos, var isSearching, var query, val focusRequester, val videoLength, val wrappedVideos |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/LocalPlaylistScreen.kt` | 1503 | fun LocalPlaylistScreen, val context, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val playlist, val songs, val mutableSongs, var locked, val coroutineScope, val snackbarHostState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/OnlinePlaylistScreen.kt` | 730 | fun OnlinePlaylistScreen, val menuState, val database, val haptic, val playerConnection, val isPlaying, val mediaMetadata, val playlist, val songs, val dbPlaylist, val isLoading, val isLoadingMore, val error, var selection |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/playlist/TopPlaylistScreen.kt` | 631 | fun TopPlaylistScreen, val coroutineScope, val context, val menuState, val haptic, val focusManager, val playerConnection, val isPlaying, val mediaMetadata, val maxSize, val songs, val mutableSongs, val likeLength, val wrappedSongs |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchResult.kt` | 441 | fun OnlineSearchResult, val menuState, val database, val playerConnection, val haptic, val isPlaying, val mediaMetadata, val coroutineScope, val lazyListState, val chipsFocusRequester, val firstResultFocusRequester, val searchFilter, val searchSummary, val isSummaryLoading |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/search/OnlineSearchScreen.kt` | 458 | fun OnlineSearchScreen, val database, val keyboardController, val menuState, val playerConnection, val scope, val haptic, val isPlaying, val mediaMetadata, val coroutineScope, val viewState, val lazyListState, val firstItemKey, fun SuggestionItem |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AboutScreen.kt` | 191 | fun AboutScreen, val uriHandler, val backFocus, val firstFocus |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AccountSettings.kt` | 453 | fun AccountSettings, val context, val uriHandler, val isLoggedIn, val hasVisitorToken, val homeViewModel, val accountSettingsViewModel, val accountName, val accountImageUrl, var showToken, var showTokenEditor, var isTestingToken, var tokenTestResult, var showLogoutDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/AppearanceSettings.kt` | 1028 | fun AppearanceSettings, val context, var showRestartDialog, var showCustomDensityDialog, val sharedPreferences, val prefDensityScale, val onDensityScaleChange, val prefBottomNavEnabled, val prefBottomNavItems, var showBottomNavCustomizationDialog, var currentSelectedItems, val isSystemInDarkTheme, val useDarkTheme, var showSliderOptionDialog |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/BackupAndRestore.kt` | 190 | fun BackupAndRestore, var importedTitle, val importedSongs, var showChoosePlaylistDialogOnline, var isProgressStarted, var progressPercentage, val context, val backupLauncher, val restoreLauncher, val importPlaylistFromCsv, val result, val importM3uLauncherOnline, val result, val backFocus |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ButtonSetupScreen.kt` | 374 | fun ButtonSetupScreen, val viewModel, val accessibilityEnabled, val context, val focusManager, val uiState, val scrollState, val currentStep, val showOverlay, fun ListeningOverlay, fun CompletedCard, fun AssignmentRow, fun DpadDirectionIcon, val drawableRes |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContentSettings.kt` | 681 | class ContentSettingsViewModel, val authManager, val webAuthManager, val syncService, val userPreferencesRepository, val authState, val syncState, val syncStatus, fun formatLastSyncTime, val sdf, fun ContentSettings, val context, val coroutineScope, val authState |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/ContributeScreen.kt` | 388 | fun ContributeScreen, val uiState, val context, val credentialManager, val scope, val scrollState, fun launchGoogleSignIn, val googleIdOption, val request, val response, val credential, val googleIdTokenCredential, fun ProgressRow, fun ProfilePrompt |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/GeneralSettings.kt` | 88 | fun GeneralSettings, val context, val intent, val intent |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PlayerSettings.kt` | 246 | fun PlayerSettings, val backFocus, val firstFocus |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/PrivacySettings.kt` | 208 | fun PrivacySettings, val database, var showClearListenHistoryDialog, var showClearSearchHistoryDialog, val backFocus, val firstFocus |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/SettingsScreen.kt` | 219 | class SettingItem, val id, val title, val description, val icon, val section, val route, fun SettingsScreen, val context, val firebaseAuth, var isLoggedIn, val listener, val baseSettings, val allSettings |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/StorageSettings.kt` | 440 | fun StorageSettings, val context, val imageDiskCache, val playerService, val playerCache, val downloadCache, val database, val coroutineScope, val resolvedDownloadPath, val downloadPickerLauncher, val flags, val onResetDownloadPath, var clearCacheDialog, var clearDownloads |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/UpdaterSettings.kt` | 278 | fun UpdaterScreen, val context, val scope, var isChecking, var showResultDialog, var updateResult, var downloadState, val backFocus, val firstFocus, val apkFile, val isDownloading, val downloadProgress, val downloadError |
| `app/src/main/kotlin/com/jtech/zemer/ui/screens/settings/integrations/IntegrationScreen.kt` | 49 | fun IntegrationScreen |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerColorExtractor.kt` | 159 | object PlayerColorExtractor, val colorCandidates, val bestSwatch, val fallbackDominant, val primaryColor, val bestColor, fun isColorVibrant, val argb, val hsv, val saturation, val brightness, fun enhanceColorVividness, val argb, val hsv |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/PlayerSliderColors.kt` | 146 | object PlayerSliderColors, fun getSliderColors, val inactiveTrackColor, fun defaultSliderColors, fun squigglySliderColors, fun slimSliderColors, val inactiveTrackColor, object Config, val DEFAULT_ACTIVE_COLOR, val DEFAULT_INACTIVE_COLOR |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Theme.kt` | 113 | val DefaultThemeColor, fun ZemerTheme, val context, val useSystemDynamicColor, val baseColorScheme, val neutralDefaults, val mergedColorScheme, val colorScheme, fun Bitmap, val colorsToPopulation, val rankedColors, fun Bitmap, val extractedColors, val orderedColors |
| `app/src/main/kotlin/com/jtech/zemer/ui/theme/Type.kt` | 123 | val AppTypography |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/AppBar.kt` | 75 | fun appBarScrollBehavior, class AppBarScrollBehavior, val canScroll |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/FadingEdge.kt` | 89 | fun Modifier, fun Modifier |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ItemWrapper.kt` | 15 | class ItemWrapper, val item, val _isSelected, var isSelected |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/KeyUtils.kt` | 50 | object KeyUtils, val counter, fun generateUniqueKey, val uniqueId, fun generateIndexedKey, val uniqueId, fun generateTimestampKey, val timestamp, val uniqueId |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/LazyGridSnapLayoutInfoProvider.kt` | 66 | fun SnapLayoutInfoProvider, val layoutInfo, val bounds, fun calculateSnappingOffsetBounds, var lowerBoundOffset, var upperBoundOffset, val offset, fun calculateDistanceToDesiredSnapPosition, val containerSize, val desiredDistance, val itemCurrentPosition, val LazyGridLayoutInfo |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/NavControllerUtils.kt` | 14 | fun NavController, val mainRoutes |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ScrollUtils.kt` | 59 | fun LazyListState, var previousIndex, var previousScrollOffset, fun LazyGridState, var previousIndex, var previousScrollOffset, fun ScrollState, var previousScrollOffset |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShapeUtils.kt` | 8 | fun CornerBasedShape |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/ShowMediaInfo.kt` | 342 | fun ShowMediaInfo, val windowInsets, var info, val database, var song, var currentFormat, val playerConnection, val context, val baseList, val extendedList, val displayText, val cm |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/StringUtils.kt` | 36 | fun formatFileSize, val prefix, var result, var suffix, fun numberFormatter |
| `app/src/main/kotlin/com/jtech/zemer/ui/utils/YouTubeUtils.kt` | 23 | fun String, var w, var h |
