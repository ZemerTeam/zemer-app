package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.constants.AndroidAutoSectionsOrderKey
import com.jtech.zemer.constants.AndroidAutoTargetPlaylistKey
import com.jtech.zemer.constants.AndroidAutoYouTubePlaylistsKey
import com.jtech.zemer.constants.MediaSessionConstants
import com.jtech.zemer.ui.component.PreferenceEntry
import com.jtech.zemer.ui.component.PreferenceGroupTitle
import com.jtech.zemer.ui.component.SwitchPreference
import com.jtech.zemer.utils.rememberPreference
import kotlinx.coroutines.flow.map
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

enum class AndroidAutoSection(val id: String) {
    LIKED("liked"),
    SONGS("songs"),
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
}

@Composable
fun AndroidAutoSection.label(): String = when (this) {
    AndroidAutoSection.LIKED -> stringResource(R.string.liked_songs)
    AndroidAutoSection.SONGS -> stringResource(R.string.songs)
    AndroidAutoSection.ARTISTS -> stringResource(R.string.artists)
    AndroidAutoSection.ALBUMS -> stringResource(R.string.albums)
    AndroidAutoSection.PLAYLISTS -> stringResource(R.string.playlists)
}

private fun AndroidAutoSection.iconRes(): Int = when (this) {
    AndroidAutoSection.LIKED -> R.drawable.favorite
    AndroidAutoSection.SONGS -> R.drawable.music_note
    AndroidAutoSection.ARTISTS -> R.drawable.artist
    AndroidAutoSection.ALBUMS -> R.drawable.album
    AndroidAutoSection.PLAYLISTS -> R.drawable.queue_music
}

fun serializeSections(sections: List<Pair<AndroidAutoSection, Boolean>>): String =
    sections.joinToString(",") { (section, enabled) -> "${section.id}:$enabled" }

fun deserializeSections(raw: String): List<Pair<AndroidAutoSection, Boolean>> {
    if (raw.isBlank()) return AndroidAutoSection.entries.map { it to true }
    val parsed = raw.split(",").mapNotNull { token ->
        val parts = token.split(":")
        if (parts.size != 2) return@mapNotNull null
        val section = AndroidAutoSection.entries.find { it.id == parts[0] } ?: return@mapNotNull null
        val enabled = parts[1].toBooleanStrictOrNull() ?: true
        section to enabled
    }
    // Append any sections missing from the stored value (e.g. after an app update adds one).
    val present = parsed.map { it.first }.toSet()
    val missing = AndroidAutoSection.entries.filter { it !in present }.map { it to true }
    return parsed + missing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAutoSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val haptic = LocalHapticFeedback.current
    val database = LocalDatabase.current

    val userPlaylists by remember {
        database.playlistsByCreateDateAsc().map { list -> list.map { it.playlist } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val (youtubePlaylistsEnabled, onYoutubePlaylistsChange) = rememberPreference(
        key = AndroidAutoYouTubePlaylistsKey,
        defaultValue = false,
    )
    val (sectionsRaw, onSectionsChange) = rememberPreference(
        key = AndroidAutoSectionsOrderKey,
        defaultValue = serializeSections(AndroidAutoSection.entries.map { it to true }),
    )
    val (targetPlaylist, onTargetPlaylistChange) = rememberPreference(
        key = AndroidAutoTargetPlaylistKey,
        defaultValue = MediaSessionConstants.TARGET_PLAYLIST_AUTO,
    )

    // Local working copy: initialized once from the stored value, mutated during interaction, and
    // persisted on toggle / drag-end — so a drag isn't fighting a DataStore round-trip every move.
    var sections by remember { mutableStateOf(deserializeSections(sectionsRaw)) }

    fun toggle(section: AndroidAutoSection, value: Boolean) {
        sections = sections.map { (s, e) -> if (s == section) s to value else s to e }
        onSectionsChange(serializeSections(sections))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = sections.indexOfFirst { it.first.id == from.key }
        val toIdx = sections.indexOfFirst { it.first.id == to.key }
        if (fromIdx != -1 && toIdx != -1) {
            sections = sections.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val playlistOptions = listOf(MediaSessionConstants.TARGET_PLAYLIST_AUTO) + userPlaylists.map { it.id }
    val playlistLabel: @Composable (String) -> String = { id ->
        if (id == MediaSessionConstants.TARGET_PLAYLIST_AUTO) {
            stringResource(R.string.android_auto_target_playlist_auto)
        } else {
            userPlaylists.find { it.id == id }?.name ?: id
        }
    }

    var showTargetPlaylistDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        item(key = "sections_title") {
            PreferenceGroupTitle(title = stringResource(R.string.android_auto_visible_sections))
        }
        item(key = "sections_hint") {
            Text(
                text = stringResource(R.string.android_auto_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
        items(sections, key = { (section, _) -> section.id }) { (section, enabled) ->
            ReorderableItem(reorderableState, key = section.id) {
                PreferenceEntry(
                    icon = { Icon(painterResource(section.iconRes()), contentDescription = null) },
                    title = { Text(section.label()) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            onSectionsChange(serializeSections(sections))
                                        },
                                    ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { toggle(section, it) },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            if (enabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    },
                    onClick = { toggle(section, !enabled) },
                )
            }
        }

        item(key = "playlist_title") {
            PreferenceGroupTitle(title = stringResource(R.string.android_auto_target_playlist))
        }
        item(key = "playlist_entry") {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.playlist_add), contentDescription = null) },
                title = { Text(stringResource(R.string.android_auto_target_playlist)) },
                description = playlistLabel(targetPlaylist),
                onClick = { showTargetPlaylistDialog = true },
            )
        }

        item(key = "youtube_title") {
            PreferenceGroupTitle(title = stringResource(R.string.mixes))
        }
        item(key = "youtube_switch") {
            SwitchPreference(
                icon = { Icon(painterResource(R.drawable.queue_music), contentDescription = null) },
                title = { Text(stringResource(R.string.android_auto_youtube_playlists)) },
                description = stringResource(R.string.android_auto_youtube_playlists_desc),
                checked = youtubePlaylistsEnabled,
                onCheckedChange = onYoutubePlaylistsChange,
            )
        }
    }

    if (showTargetPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showTargetPlaylistDialog = false },
            title = { Text(stringResource(R.string.android_auto_target_playlist)) },
            text = {
                LazyColumn {
                    items(playlistOptions) { value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTargetPlaylistDialog = false
                                    onTargetPlaylistChange(value)
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            RadioButton(selected = value == targetPlaylist, onClick = null)
                            Text(
                                text = playlistLabel(value),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTargetPlaylistDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.android_auto)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
