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
import com.jtech.zemer.ui.screens.artist.ArtistAlbumsScreen
import com.jtech.zemer.ui.screens.artist.ArtistItemsScreen
import com.jtech.zemer.ui.screens.artist.ArtistScreen
import com.jtech.zemer.ui.screens.artist.ArtistSongsScreen
import com.jtech.zemer.ui.screens.library.LibraryScreen
import com.jtech.zemer.ui.screens.player.VideoPlayerScreen
import com.jtech.zemer.ui.screens.playlist.AutoPlaylistScreen
import com.jtech.zemer.ui.screens.playlist.CachePlaylistScreen
import com.jtech.zemer.ui.screens.playlist.DownloadedContentScreen
import com.jtech.zemer.ui.screens.playlist.DownloadedVideosScreen
import com.jtech.zemer.ui.screens.playlist.LocalPlaylistScreen
import com.jtech.zemer.ui.screens.playlist.OnlinePlaylistScreen
import com.jtech.zemer.ui.screens.playlist.TopPlaylistScreen
import com.jtech.zemer.ui.screens.search.OnlineSearchResult
import com.jtech.zemer.ui.screens.settings.AboutScreen
import com.jtech.zemer.ui.screens.settings.AppearanceSettings
import com.jtech.zemer.ui.screens.settings.BackupAndRestore
import com.jtech.zemer.ui.screens.settings.ButtonSetupScreen
import com.jtech.zemer.ui.screens.settings.ContentSettings
import com.jtech.zemer.ui.screens.settings.GeneralSettings
import com.jtech.zemer.ui.screens.settings.PlayerSettings
import com.jtech.zemer.ui.screens.settings.PrivacySettings
import com.jtech.zemer.ui.screens.settings.SettingsScreen
import com.jtech.zemer.ui.screens.settings.StorageSettings
import com.jtech.zemer.ui.screens.settings.UpdaterScreen
import com.jtech.zemer.ui.screens.settings.integrations.IntegrationScreen


@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    searchBarScrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    composable(Screens.Home.route) {
        HomeScreen(navController)
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
    composable("stats") {
        StatsScreen(navController)
    }
    composable("mood_and_genres") {
        MoodAndGenresScreen(navController, scrollBehavior)
    }
    composable("account") {
        AccountScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("charts_screen") {
       ChartsScreen(navController)
    }
    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
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
        route = "album/{albumId}",
        arguments =
        listOf(
            navArgument("albumId") {
                type = NavType.StringType
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
    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "video/{videoId}?title={title}&artist={artist}",
        arguments = listOf(
            navArgument("videoId") {
                type = NavType.StringType
            },
            navArgument("title") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("artist") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
        val title = backStackEntry.arguments?.getString("title")
        val artist = backStackEntry.arguments?.getString("artist")
        VideoPlayerScreen(navController, videoId, title, artist)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
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
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
        listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }
    composable("settings") {
        SettingsScreen(navController, scrollBehavior, latestVersionName)
    }
    composable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior)
    }
    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
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
