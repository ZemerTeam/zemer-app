package com.jtech.zemer.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.jtech.zemer.R
import com.jtech.zemer.constants.BlockVideosKey
import com.jtech.zemer.constants.ChipSortTypeKey
import com.jtech.zemer.constants.LibraryFilter
import com.jtech.zemer.ui.component.ChipsRow
import com.jtech.zemer.utils.rememberEnumPreference
import com.jtech.zemer.utils.rememberPreference

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val (blockVideos, _) = rememberPreference(BlockVideosKey, false)

    val availableFilters = if (blockVideos) {
        listOf(LibraryFilter.SONGS, LibraryFilter.ARTISTS, LibraryFilter.ALBUMS, LibraryFilter.PLAYLISTS, LibraryFilter.LIBRARY)
    } else {
        listOf(LibraryFilter.SONGS, LibraryFilter.VIDEOS, LibraryFilter.ARTISTS, LibraryFilter.ALBUMS, LibraryFilter.PLAYLISTS, LibraryFilter.LIBRARY)
    }

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips =
                availableFilters.associateWith { filter ->
                    when (filter) {
                        LibraryFilter.PLAYLISTS -> stringResource(R.string.filter_playlists)
                        LibraryFilter.SONGS -> stringResource(R.string.filter_songs)
                        LibraryFilter.VIDEOS -> stringResource(R.string.videos)
                        LibraryFilter.ALBUMS -> stringResource(R.string.filter_albums)
                        LibraryFilter.ARTISTS -> stringResource(R.string.filter_artists)
                        LibraryFilter.LIBRARY -> ""
                    }
                }.filterKeys { it != LibraryFilter.LIBRARY }.toList(),
                currentValue = filterType,
                onValueUpdate = {
                    filterType =
                        if (filterType == it) {
                            LibraryFilter.LIBRARY
                        } else {
                            it
                        }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(navController, filterContent)
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.VIDEOS -> if (!blockVideos) {
                LibraryVideosScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })
            } else {
                // Fallback to LIBRARY if videos are blocked
                LibraryMixScreen(navController, filterContent)
            }
        }
    }
}
