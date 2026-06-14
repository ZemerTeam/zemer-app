package com.jtech.zemer.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.R
import com.jtech.zemer.constants.ListThumbnailSize
import com.jtech.zemer.db.entities.Playlist
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.ui.component.CreatePlaylistDialog
import com.jtech.zemer.ui.component.ListDialog
import com.jtech.zemer.ui.component.ListItem
import com.jtech.zemer.ui.component.PlaylistListItem
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AddToPlaylistDialogOnline(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    songs: SnapshotStateList<Song>, // list of song ids. Songs should be inserted to database in this function.
    onDismiss: () -> Unit,
    onProgressStart: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember {
        mutableStateOf(emptyList<Playlist>())
    }


    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    suspend fun findFirstSong(song: Song): SongItem? {
        val allArtists = song.artists.joinToString(" ") { artist ->
            URLDecoder.decode(artist.name, StandardCharsets.UTF_8.toString())
        }.trim()
        val query = if (allArtists.isBlank()) song.title else "${song.title} - $allArtists"

        return runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.firstOrNull() as? SongItem
        }.onFailure { reportException(it) }
            .getOrNull()
    }

    suspend fun processSongs(onSongFound: suspend (SongItem) -> Unit) {
        val songList = songs.toList()
        if (songList.isEmpty()) return

        withContext(Dispatchers.Main) {
            onProgressStart(true)
            onPercentageChange(0)
        }

        try {
            withContext(Dispatchers.IO) {
                songList.asReversed().forEachIndexed { index, song ->
                    val firstSong = findFirstSong(song)
                    if (firstSong != null) {
                        runCatching { onSongFound(firstSong) }
                            .onFailure {
                                Timber.e(it, "Failed to process song %s", firstSong.id)
                            }
                    }

                    val progress = (((index + 1) * 100) / songList.size).coerceIn(0, 100)
                    withContext(Dispatchers.Main) { onPercentageChange(progress) }
                }
            }
        } finally {
            withContext(Dispatchers.Main) { onProgressStart(false) }
        }
    }

    LaunchedEffect(Unit) {
        database.editablePlaylistsByCreateDateAsc().collect {
            playlists = it.asReversed()
        }
    }

    if (isVisible) {
        ListDialog(
            onDismiss = onDismiss
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.playlist_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreatePlaylistDialog = true
                    }
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            onDismiss()
                            processSongs { firstSong ->
                                val firstSongMedia = firstSong.toMediaMetadata()
                                try {
                                    database.insert(firstSongMedia)
                                } catch (e: Exception) {
                                    Timber.tag("Exception inserting song in database:").e(e.toString())
                                }
                                database.addSongToPlaylist(playlist, listOf(firstSong.id))
                            }
                        }
                    }
                )
            }

            item {
                ListItem(
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            onDismiss()
                            processSongs { firstSong ->
                                val firstSongMedia = firstSong.toMediaMetadata()
                                val firstSongEnt = firstSong.toMediaMetadata().toSongEntity()
                                try {
                                    database.insert(firstSongMedia)
                                    database.query {
                                        update(firstSongEnt.toggleLike())
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("Exception inserting song in database:").e(e.toString())
                                }
                            }
                        }
                    },
                    title = stringResource(R.string.liked_songs),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.favorite), // The XML image
                            contentDescription = null,
                            modifier = Modifier.size(40.dp), // Adjust size as needed
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground) // Optional tinting
                        )
                    },
                    trailingContent = {}
                )
            }

            item {
                Text(
                    text = stringResource(R.string.playlist_add_local_to_synced_note),
                    fontSize = TextUnit(12F, TextUnitType.Sp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing
        )
    }
}
