package com.jtech.zemer.ui.menu

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jtech.zemer.constants.UserPlaylistSharedByKey
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.rememberPreference
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.jtech.zemer.R
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.search.ZemerRateLimitedException
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The share must survive the dismissal that triggers it: [TextFieldDialog]'s OK button dismisses
 * BEFORE running onDone, and the dialog's onDismiss also closes the whole menu, so a composition
 * scope dies on the next frame and would cancel the in-flight POST (and the remembered-name
 * write). Own scope, the OfflineSubsetSyncer precedent.
 */
private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * The issue-#176 share flow, callable from any playlist menu: POST the local playlist's snapshot
 * (title + videoIds + the anonymous device uuid), then open the system share sheet with the minted
 * unguessable link. `dropped > 0` (non-corpus or globally-blocked members, one server-defined
 * truth) gets an honest toast; a 429 says "try again later" and never retry-loops; other failures
 * toast + report. One request, fire-and-forget UX.
 */
suspend fun shareUserPlaylist(context: Context, title: String, videoIds: List<String>, sharedBy: String?) {
    val appContext = context.applicationContext
    if (videoIds.isEmpty()) {
        Toast.makeText(appContext, R.string.share_playlist_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val repository = EntryPointAccessors
        .fromApplication(appContext, ZemerSearchRepositoryEntryPoint::class.java)
        .zemerSearchRepository()
    runCatching { withContext(Dispatchers.IO) { repository.shareUserPlaylist(title, videoIds, sharedBy) } }
        .onSuccess { response ->
            if (response.dropped > 0) {
                Toast.makeText(
                    appContext,
                    appContext.resources.getQuantityString(R.plurals.share_playlist_dropped, response.dropped, response.dropped),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            // The SHARE action is tracked HERE, at the moment the share actually happened, with the
            // minted share id (joinable to opened links) - not on dialog-open, where a cancel or a
            // 429 would still count (chokepoint rule, docs/tracking/README.md).
            Tracker.action(TrackingActionKind.SHARE, response.id)
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, response.url)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
        .onFailure { e ->
            if (e is CancellationException) throw e
            reportException(e)
            val message = if (e is ZemerRateLimitedException) R.string.share_playlist_rate_limited else R.string.share_playlist_failed
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
}

/**
 * The share entry point: a one-field dialog for the OPTIONAL sharer display name ("shared by
 * ‹name›" on the receiver's screen and the web landing), prefilled from the device-remembered
 * preference so it is one-time typing. Done (empty allowed = share anonymously) saves the name and
 * runs [shareUserPlaylist].
 */
@Composable
fun ShareUserPlaylistDialog(
    playlistTitle: String,
    videoIds: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (savedName) = rememberPreference(UserPlaylistSharedByKey, defaultValue = "")

    TextFieldDialog(
        icon = { Icon(painterResource(R.drawable.share), contentDescription = null) },
        title = { Text(stringResource(R.string.share_playlist_as_title)) },
        initialTextFieldValue = TextFieldValue(savedName, selection = TextRange(savedName.length)),
        placeholder = { Text(stringResource(R.string.share_playlist_name_hint)) },
        autoFocus = savedName.isEmpty(),
        isInputValid = { true }, // empty = share anonymously
        onDismiss = onDismiss,
        onDone = { name ->
            val trimmed = name.trim()
            shareScope.launch {
                // The name write goes through the same surviving scope - rememberPreference's
                // setter launches into the composition scope this tap is cancelling.
                context.applicationContext.dataStore.edit { it[UserPlaylistSharedByKey] = trimmed }
                shareUserPlaylist(context, playlistTitle, videoIds, trimmed.takeIf { it.isNotBlank() })
            }
        },
    )
}
