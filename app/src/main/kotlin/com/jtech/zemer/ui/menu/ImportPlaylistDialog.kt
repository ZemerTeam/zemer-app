package com.jtech.zemer.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.PlaylistEntity
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * The import must survive the dismissal that triggers it: [TextFieldDialog]'s OK button dismisses
 * BEFORE running onDone, so this composable's rememberCoroutineScope dies on the next frame and
 * would cancel the copy mid-flight - a "Save a copy" that silently saves nothing.
 */
private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>, // list of song ids. Songs should be inserted to database in this function.
    playlistTitle: String,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current

    val textFieldValue by remember { mutableStateOf(TextFieldValue(text = playlistTitle)) }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = textFieldValue,
            autoFocus = false,
            onDismiss = onDismiss,
            onDone = { finalName ->
                importScope.launch {
                    runCatching {
                        val newPlaylist = PlaylistEntity(name = finalName)
                        // AWAIT the row insert before reading it back - a fire-and-forget insert
                        // racing the flow read intermittently returned null and silently imported
                        // nothing. awaitTransaction also rethrows a failed insert into this
                        // runCatching instead of hanging the coroutine forever.
                        database.awaitTransaction { insert(newPlaylist) }
                        val playlist = database.playlist(newPlaylist.id).firstOrNull() ?: return@launch
                        database.addSongToPlaylist(playlist, onGetSong())
                    }.onFailure { reportException(it) }
                }
                onDismiss()
            }
        )
    }
}
