package com.jtech.zemer.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.ui.screens.artist.ArtistAlbumsScreen
import com.jtech.zemer.ui.screens.artist.ArtistScreen
import com.jtech.zemer.ui.screens.artist.ArtistSectionScreen
import com.jtech.zemer.ui.screens.artist.ArtistSongsScreen
import com.jtech.zemer.ui.screens.library.LibraryScreen
import com.jtech.zemer.ui.screens.statuses.SavedStatusScreen
import com.jtech.zemer.ui.screens.statuses.StatusDownloadsScreen
import com.jtech.zemer.ui.screens.statuses.StatusesScreen
import com.jtech.zemer.ui.screens.statuses.StoryScreen
import com.jtech.zemer.ui.screens.playlist.AutoPlaylistScreen
import com.jtech.zemer.ui.screens.playlist.CachePlaylistScreen
import com.jtech.zemer.ui.screens.playlist.DownloadedContentScreen
import com.jtech.zemer.ui.screens.playlist.DownloadedVideosScreen
import com.jtech.zemer.ui.screens.playlist.LocalPlaylistScreen
import com.jtech.zemer.ui.screens.playlist.OnlinePlaylistScreen
import com.jtech.zemer.ui.screens.playlist.TopPlaylistScreen
import com.jtech.zemer.ui.screens.playlist.ZemerCuratedPlaylistScreen
import com.jtech.zemer.ui.screens.recognition.RecognitionHistoryScreen
import com.jtech.zemer.ui.screens.search.OnlineSearchResult
import com.jtech.zemer.ui.screens.settings.AboutScreen
import com.jtech.zemer.ui.screens.settings.AndroidAutoSettings
import com.jtech.zemer.ui.screens.settings.AppearanceSettings
import com.jtech.zemer.ui.screens.settings.BackupAndRestore
import com.jtech.zemer.ui.screens.settings.ButtonSetupScreen
import com.jtech.zemer.ui.screens.settings.ContentSettings
import com.jtech.zemer.ui.screens.settings.GeneralSettings
import com.jtech.zemer.ui.screens.settings.LogViewerScreen
import com.jtech.zemer.ui.screens.settings.OfflineSearchSettings
import com.jtech.zemer.ui.screens.settings.PlayerSettings
import com.jtech.zemer.ui.screens.settings.PrivacySettings
import com.jtech.zemer.ui.screens.settings.SettingsScreen
import com.jtech.zemer.ui.screens.settings.StorageSettings
import com.jtech.zemer.ui.screens.settings.ThemeScreen
import com.jtech.zemer.ui.screens.settings.StreamSourceSettings
import com.jtech.zemer.ui.screens.settings.UpdaterScreen
import com.jtech.zemer.ui.screens.settings.integrations.IntegrationScreen
import androidx.compose.runtime.LaunchedEffect
import com.jtech.zemer.viewmodels.HomeSeeAllRow
import com.jtech.zemer.viewmodels.HomeViewModel


@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    searchBarScrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    homeViewModel: HomeViewModel? = null,
) {
    composable(Screens.Home.route) {
        HomeScreen(navController, viewModel = homeViewModel)
    }
    composable(Screens.Artists.route) {
        WhitelistedArtistsScreen(navController, searchBarScrollBehavior)
    }
    composable(Screens.KidZone.route) {
        KidZoneScreen(navController)
    }
    composable(
        Screens.Library.route,
    ) {
        LibraryScreen(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("recognition_history") {
        RecognitionHistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("latest_releases") {
        LatestReleasesScreen(navController, scrollBehavior)
    }
    composable("statuses") {
        StatusesScreen(navController, scrollBehavior)
    }
    composable("zemer_playlists") {
        ZemerPlaylistsScreen(navController, scrollBehavior)
    }
    composable("zemer_stations") {
        ZemerStationsScreen(navController, scrollBehavior)
    }
    composable("genres") {
        GenresScreen(navController, scrollBehavior)
    }
    composable(
        // {genreId} is a server genre slug ("nigunim"), never a YouTube id — its own screen, like
        // zemer_playlist. A blank slug is a broken deep link — pop back; an unknown one 404s and the
        // screen backs itself out.
        route = "genre/{genreId}",
        arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
    ) {
        val genreId = it.arguments?.getString("genreId")
        if (genreId.isNullOrBlank()) LaunchedEffect(Unit) { navController.navigateUp() }
        else GenreScreen(navController, scrollBehavior)
    }
    composable(
        // One genre's full Albums or Singles grid (see-all). {genreId} is the slug; {section} is
        // albums/singles. A blank slug is a broken deep link — pop back.
        route = "genre_section/{genreId}?section={section}",
        arguments = listOf(
            navArgument("genreId") { type = NavType.StringType },
            navArgument("section") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        val genreId = it.arguments?.getString("genreId")
        if (genreId.isNullOrBlank()) LaunchedEffect(Unit) { navController.navigateUp() }
        else GenreSectionScreen(navController, scrollBehavior)
    }
    composable(
        route = "home_see_all/{row}",
        arguments = listOf(navArgument("row") { type = NavType.StringType }),
    ) {
        // An unknown/absent row slug is a broken deep link, not a crash — pop back to Home.
        val row = HomeSeeAllRow.fromSlug(it.arguments?.getString("row"))
        if (row == null) LaunchedEffect(Unit) { navController.navigateUp() }
        else HomeSeeAllScreen(navController, scrollBehavior, row)
    }
    composable(
        route = "artist_section/{artistId}?title={title}",
        arguments = listOf(
            navArgument("artistId") { type = NavType.StringType },
            navArgument("title") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        ArtistSectionScreen(navController, scrollBehavior, it.arguments?.getString("title").orEmpty())
    }
    composable("charts_screen") {
       ChartsScreen(navController)
    }
    composable(
        route = "search/{query}?filter={filter}",
        arguments =
        listOf(
            navArgument("query") {
                type = NavType.StringType
            },
            navArgument("filter") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        // Optional args (their defaults keep every existing `album/{albumId}` link matching): `zemer`
        // routes a Zemer-search album open through the server's `/album` endpoint, and `playlistId`
        // carries the search card's OP playlist id (the server's album header doesn't return one).
        route = "album/{albumId}?zemer={zemer}&playlistId={playlistId}",
        arguments =
        listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
            navArgument("zemer") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("playlistId") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }
    // The full-screen JewishStatus story viewer, opened from the Home "Music Status" row by the tapped
    // creator's STABLE id (the creators list comes from the shared session cache; the viewer resolves
    // the id to the current index, which survives a process-death re-fetch under the recency sort).
    composable(
        route = "story/{creatorId}",
        arguments = listOf(
            navArgument("creatorId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val creatorId = backStackEntry.arguments?.getString("creatorId") ?: return@composable
        StoryScreen(navController, creatorId)
    }
    composable(
        // Optional `zemer` flag (default false) routes a Zemer-search playlist open through the
        // server's `/playlist` endpoint; `community` (default false) tags its plays `community:<id>`
        // (discovery-sourced community lists) instead of `playlist:<id>`. Plain links keep both defaults.
        route = "online_playlist/{playlistId}?zemer={zemer}&community={community}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
            navArgument("zemer") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("community") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        // A hand-curated "Zemer Playlists" entry; {playlistId} is a server slug (e.g. "shabbos"),
        // never a YouTube playlist id, so it gets its own screen instead of online_playlist.
        route = "zemer_playlist/{playlistId}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ZemerCuratedPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
        listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }
    composable(route = "downloaded_content") {
        DownloadedContentScreen(navController, scrollBehavior)
    }
    composable(route = "status_downloads") {
        StatusDownloadsScreen(navController, scrollBehavior)
    }
    composable(
        route = "saved_status/{creatorId}?start={start}",
        arguments = listOf(
            navArgument("creatorId") { type = NavType.StringType },
            navArgument("start") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        SavedStatusScreen(
            navController,
            initialCreatorId = it.arguments?.getString("creatorId").orEmpty(),
            startId = it.arguments?.getString("start")?.ifBlank { null },
        )
    }
    composable(route = "downloaded_videos") {
        DownloadedVideosScreen(navController, scrollBehavior)
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
        listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable("settings") {
        SettingsScreen(navController, scrollBehavior, latestVersionName)
    }
    composable("settings/android_auto") {
        AndroidAutoSettings(navController, scrollBehavior)
    }
    composable(
        route = "settings/appearance?scrollTo={scrollTo}",
        arguments = listOf(
            navArgument("scrollTo") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        AppearanceSettings(
            navController,
            scrollBehavior,
            scrollToStatus = it.arguments?.getString("scrollTo") == "status",
        )
    }
    composable("settings/appearance/theme") {
        ThemeScreen(navController, scrollBehavior)
    }
    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }
    composable("settings/stream_sources") {
        StreamSourceSettings(navController, scrollBehavior)
    }
    composable("settings/general") {
        GeneralSettings(navController, scrollBehavior)
    }
    composable("settings/dpad") {
        ButtonSetupScreen(navController, scrollBehavior)
    }
    composable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }
    composable("settings/offline_search") {
        OfflineSearchSettings(navController, scrollBehavior)
    }
    composable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }
    composable("settings/integrations") {
        IntegrationScreen(navController, scrollBehavior)
    }
    composable("settings/updater") {
        UpdaterScreen(navController, scrollBehavior)
    }
    if (BuildConfig.DEBUG) {
        // Developer-mode Log viewer — the route itself doesn't exist in release builds.
        composable("settings/log_viewer") {
            LogViewerScreen(navController, scrollBehavior)
        }
    }
    composable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }
    composable("login") {
        LoginScreen(navController)
    }
    composable("login_gate") {
        LoginGateScreen(navController = navController)
    }
}
