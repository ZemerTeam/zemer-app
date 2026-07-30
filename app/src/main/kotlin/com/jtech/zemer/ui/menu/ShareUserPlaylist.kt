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
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.R
import com.jtech.zemer.constants.UserPlaylistSharedByKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.di.MusicDatabaseEntryPoint
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.search.ZemerRateLimitedException
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.ZemerShareGoneException
import com.jtech.zemer.search.ZemerUserPlaylistCreateResponse
import com.jtech.zemer.search.sharedPlaylistFingerprint
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.rememberPreference
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share must survive the dismissal that triggers it: [TextFieldDialog]'s OK button dismisses
 * BEFORE running onDone, and the dialog's onDismiss also closes the whole menu, so a composition
 * scope dies on the next frame and would cancel the in-flight request (and the remembered-name
 * write). Own scope, the OfflineSubsetSyncer precedent.
 */
private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Fallback when a PUT response omits `url` - the server's fixed host + the stable share id. */
private fun userPlaylistUrl(shareId: String) = "https://search.zemer.io/user_playlist/$shareId"

private fun repositoryOf(context: Context): ZemerSearchRepository = EntryPointAccessors
    .fromApplication(context.applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
    .zemerSearchRepository()

private fun databaseOf(context: Context): MusicDatabase = EntryPointAccessors
    .fromApplication(context.applicationContext, MusicDatabaseEntryPoint::class.java)
    .musicDatabase()

/**
 * The issue-#176 share flow, callable from any playlist surface. Shares are LIVE (contract
 * 2026-07-30): the first share POSTs a new server playlist and stores the returned share id +
 * owner token on the local [com.jtech.zemer.db.entities.PlaylistEntity]; every later share of the
 * same playlist PUTs the current state to the SAME id/URL, so re-tapping Share never mints a
 * second link (and [com.jtech.zemer.search.SharedPlaylistAutoUpdater] keeps the link fresh
 * between shares). A 403/404 on update (token rejected / link taken down) clears the stored
 * credentials and falls through to minting a fresh share. `dropped > 0` (non-corpus or
 * globally-blocked members, one server-defined truth) gets an honest toast; a 429 says "try again
 * later" and never retry-loops; other failures toast + report.
 */
suspend fun shareUserPlaylist(context: Context, playlistId: String, title: String, videoIds: List<String>, sharedBy: String?) {
    val appContext = context.applicationContext
    if (videoIds.isEmpty()) {
        Toast.makeText(appContext, R.string.share_playlist_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val repository = repositoryOf(context)
    val database = databaseOf(context)
    val entity = withContext(Dispatchers.IO) { database.playlist(playlistId).first() }?.playlist

    val existingShareId = entity?.shareId
    val ownerToken = entity?.shareOwnerToken
    if (existingShareId != null && ownerToken != null) {
        val updated = runCatching {
            withContext(Dispatchers.IO) { repository.updateUserPlaylist(existingShareId, ownerToken, title, videoIds, sharedBy) }
        }
        updated.getOrNull()?.let { response ->
            database.query { updatePlaylistShareSyncedHash(playlistId, sharedPlaylistFingerprint(title, videoIds)) }
            openShareSheet(context, existingShareId, response.copy(url = response.url.ifBlank { userPlaylistUrl(existingShareId) }))
            return
        }
        val e = updated.exceptionOrNull()!!
        when {
            e is CancellationException -> throw e
            // The share is gone server-side (pre-token row, takedown, rejected token): clear the
            // dead credentials and fall through to mint a fresh link.
            e is ZemerShareGoneException -> database.query { updatePlaylistShare(playlistId, null, null, null) }
            else -> {
                reportException(e)
                Toast.makeText(appContext, R.string.share_playlist_failed, Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    runCatching { withContext(Dispatchers.IO) { repository.shareUserPlaylist(title, videoIds, sharedBy) } }
        .onSuccess { response ->
            database.query {
                updatePlaylistShare(
                    playlistId = playlistId,
                    shareId = response.id.takeIf { it.isNotBlank() },
                    ownerToken = response.ownerToken.takeIf { it.isNotBlank() },
                    syncedHash = sharedPlaylistFingerprint(title, videoIds),
                )
            }
            openShareSheet(context, response.id, response)
        }
        .onFailure { e ->
            if (e is CancellationException) throw e
            reportException(e)
            val message = if (e is ZemerRateLimitedException) R.string.share_playlist_rate_limited else R.string.share_playlist_failed
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
}

private fun openShareSheet(context: Context, shareId: String, response: ZemerUserPlaylistCreateResponse) {
    val appContext = context.applicationContext
    if (response.dropped > 0) {
        Toast.makeText(
            appContext,
            appContext.resources.getQuantityString(R.plurals.share_playlist_dropped, response.dropped, response.dropped),
            Toast.LENGTH_SHORT,
        ).show()
    }
    // The SHARE action is tracked HERE, at the moment the share actually happened, with the
    // share id (joinable to opened links) - not on dialog-open, where a cancel or a 429 would
    // still count (chokepoint rule, docs/tracking/README.md).
    Tracker.action(TrackingActionKind.SHARE, shareId)
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, response.url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/**
 * Withdraws a playlist's active share (`DELETE`, owner-token-gated): the link 404s everywhere
 * immediately, and the stored credentials are cleared so the next Share mints a fresh link. A
 * server-side "already gone" counts as done. No-op for a playlist with no active share.
 */
suspend fun unshareUserPlaylist(context: Context, playlistId: String) {
    val appContext = context.applicationContext
    val database = databaseOf(context)
    val entity = withContext(Dispatchers.IO) { database.playlist(playlistId).first() }?.playlist ?: return
    val shareId = entity.shareId ?: return
    val ownerToken = entity.shareOwnerToken ?: return
    withdrawShare(appContext, shareId, ownerToken, onDone = {
        database.query { updatePlaylistShare(playlistId, null, null, null) }
    })
}

/** Runs [unshareUserPlaylist] on the surviving share scope (for tap handlers). */
fun unshareUserPlaylistAsync(context: Context, playlistId: String) {
    shareScope.launch { unshareUserPlaylist(context, playlistId) }
}

/**
 * Fire-and-forget share withdrawal with credentials captured BEFORE the local row disappears -
 * the delete-playlist path (the row deletion and this DELETE race, so nothing here may read or
 * write the playlist row).
 */
fun withdrawShareAsync(context: Context, shareId: String, ownerToken: String) {
    val appContext = context.applicationContext
    shareScope.launch { withdrawShare(appContext, shareId, ownerToken, onDone = {}) }
}

private suspend fun withdrawShare(appContext: Context, shareId: String, ownerToken: String, onDone: () -> Unit) {
    runCatching { withContext(Dispatchers.IO) { repositoryOf(appContext).deleteUserPlaylist(shareId, ownerToken) } }
        .onSuccess {
            onDone()
            Toast.makeText(appContext, R.string.unshare_done, Toast.LENGTH_SHORT).show()
        }
        .onFailure { e ->
            when {
                e is CancellationException -> throw e
                // Already gone server-side = the goal state; clear and confirm.
                e is ZemerShareGoneException -> {
                    onDone()
                    Toast.makeText(appContext, R.string.unshare_done, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    reportException(e)
                    Toast.makeText(appContext, R.string.share_playlist_failed, Toast.LENGTH_SHORT).show()
                }
            }
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
    playlistId: String,
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
                shareUserPlaylist(context, playlistId, playlistTitle, videoIds, trimmed.takeIf { it.isNotBlank() })
            }
        },
    )
}
