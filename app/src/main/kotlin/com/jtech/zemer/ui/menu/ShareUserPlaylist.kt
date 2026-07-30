package com.jtech.zemer.ui.menu

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.edit
import com.jtech.zemer.R
import com.jtech.zemer.constants.UserPlaylistSharedByKey
import com.jtech.zemer.di.ShareCredentialStoreEntryPoint
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.search.ShareCredentials
import com.jtech.zemer.search.ShareCredentialStore
import com.jtech.zemer.search.ZemerRateLimitedException
import com.jtech.zemer.search.ZemerSearchClient
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.ZemerShareHttpException
import com.jtech.zemer.search.isZemerServerUnreachable
import com.jtech.zemer.search.ZemerShareGoneException
import com.jtech.zemer.search.ZemerUserPlaylistCreateResponse
import com.jtech.zemer.search.sharedPlaylistFingerprint
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.jtech.zemer.ui.component.TextFieldDialog
import com.jtech.zemer.utils.dataStore
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

/** Fallback when a PUT response omits `url` - the server host (single-sourced) + the stable id. */
private fun userPlaylistUrl(shareId: String) = "${ZemerSearchClient.BASE_URL}/user_playlist/$shareId"

private fun repositoryOf(context: Context): ZemerSearchRepository = EntryPointAccessors
    .fromApplication(context.applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
    .zemerSearchRepository()

private fun credentialStoreOf(context: Context): ShareCredentialStore = EntryPointAccessors
    .fromApplication(context.applicationContext, ShareCredentialStoreEntryPoint::class.java)
    .shareCredentialStore()

/**
 * The issue-#176 share flow, callable from any playlist surface. Shares are LIVE (contract
 * 2026-07-30): the first share POSTs a new server playlist and stores the returned share id +
 * owner token in the [ShareCredentialStore] credential map; every later share of the
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
    val store = credentialStoreOf(context)
    val existing = store.get(playlistId)

    var staleCredentials = false
    if (existing != null) {
        val updated = runCatching {
            withContext(Dispatchers.IO) { repository.updateUserPlaylist(existing.shareId, existing.ownerToken, title, videoIds, sharedBy) }
        }
        updated.getOrNull()?.let { response ->
            store.set(playlistId, existing.copy(syncedHash = sharedPlaylistFingerprint(title, videoIds), sharedBy = sharedBy))
            openShareSheet(context, existing.shareId, response.copy(url = response.url.ifBlank { userPlaylistUrl(existing.shareId) }))
            return
        }
        val e = updated.exceptionOrNull()!!
        when {
            e is CancellationException -> throw e
            // The share is gone server-side (pre-token row, takedown, rejected token): fall
            // through to mint a fresh link. DataStore edits are serialized, so the store.set
            // below replaces (or the failure branch removes) the entry atomically - no
            // clear-vs-store interleaving is possible.
            e is ZemerShareGoneException -> staleCredentials = true
            else -> {
                shareFailureToast(appContext, e)
                return
            }
        }
    }

    runCatching { withContext(Dispatchers.IO) { repository.shareUserPlaylist(title, videoIds, sharedBy) } }
        .onSuccess { response ->
            if (response.id.isNotBlank() && response.ownerToken.isNotBlank()) {
                store.set(
                    playlistId,
                    ShareCredentials(
                        shareId = response.id,
                        ownerToken = response.ownerToken,
                        syncedHash = sharedPlaylistFingerprint(title, videoIds),
                        sharedBy = sharedBy,
                    ),
                )
            }
            openShareSheet(context, response.id, response)
        }
        .onFailure { e ->
            if (e is CancellationException) throw e
            // The old credentials are dead: drop them so the next Share does not retry a doomed
            // PUT first.
            if (staleCredentials) store.remove(playlistId)
            shareFailureToast(appContext, e)
        }
}

/**
 * One failure-classification chokepoint for the share flows: a rate limit gets its own copy
 * (whether the typed 429 from create or an HTTP 429 verdict on PUT); a plain no-network failure
 * gets the no-network toast and is NEVER reported (an airplane-mode Share is routine, and
 * reporting it would bury the real contract errors the auto-updater deliberately surfaces); only
 * genuinely unexpected failures reach Crashlytics.
 */
private fun shareFailureToast(appContext: Context, e: Throwable) {
    val message = when {
        e is ZemerRateLimitedException -> R.string.share_playlist_rate_limited
        e is ZemerShareHttpException && e.status == 429 -> R.string.share_playlist_rate_limited
        e.isZemerServerUnreachable() -> R.string.error_no_internet
        else -> {
            reportException(e)
            R.string.share_playlist_failed
        }
    }
    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
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
    val store = credentialStoreOf(context)
    val credentials = store.get(playlistId) ?: return
    runCatching {
        withContext(Dispatchers.IO) { repositoryOf(appContext).deleteUserPlaylist(credentials.shareId, credentials.ownerToken) }
    }
        .onSuccess {
            store.remove(playlistId)
            Toast.makeText(appContext, R.string.unshare_done, Toast.LENGTH_SHORT).show()
        }
        .onFailure { e ->
            when {
                e is CancellationException -> throw e
                // Already gone server-side = the goal state; clear and confirm.
                e is ZemerShareGoneException -> {
                    store.remove(playlistId)
                    Toast.makeText(appContext, R.string.unshare_done, Toast.LENGTH_SHORT).show()
                }
                // Routine no-network: correct copy, no Crashlytics noise (the auto-updater's
                // orphan sweep backstops the delete-playlist path).
                e.isZemerServerUnreachable() -> Toast.makeText(appContext, R.string.error_no_internet, Toast.LENGTH_SHORT).show()
                else -> {
                    reportException(e)
                    Toast.makeText(appContext, R.string.unshare_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
}

/** Runs [unshareUserPlaylist] on the surviving share scope (for tap handlers). */
fun unshareUserPlaylistAsync(context: Context, playlistId: String) {
    shareScope.launch { unshareUserPlaylist(context, playlistId) }
}

/**
 * Whether [playlistId] has an active share, reactively (drives the Unshare affordance). Reads the
 * credential map - never a DB column; the share feature is deliberately schema-free.
 */
@Composable
fun rememberHasActiveShare(playlistId: String): State<Boolean> {
    val context = LocalContext.current
    val store = remember { credentialStoreOf(context) }
    return produceState(initialValue = false, playlistId) {
        store.shares.collect { value = it.containsKey(playlistId) }
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
    // The saved name is READ TO COMPLETION before the dialog renders: TextFieldDialog latches its
    // initial value in an unkeyed remember, so a collectAsState default ('' on frame 1) would
    // permanently show an empty field however fast the real value arrives.
    val savedName by produceState<String?>(initialValue = null) {
        value = context.applicationContext.dataStore.data.first()[UserPlaylistSharedByKey].orEmpty()
    }

    savedName?.let { name ->
        TextFieldDialog(
            icon = { Icon(painterResource(R.drawable.share), contentDescription = null) },
            title = { Text(stringResource(R.string.share_playlist_as_title)) },
            initialTextFieldValue = TextFieldValue(name, selection = TextRange(name.length)),
            placeholder = { Text(stringResource(R.string.share_playlist_name_hint)) },
            autoFocus = name.isEmpty(),
            isInputValid = { true }, // empty = share anonymously
            onDismiss = onDismiss,
            onDone = { input ->
                val trimmed = input.trim()
                shareScope.launch {
                    // Remember only a real name: OK on an empty field means "share this one
                    // anonymously", not "forget my name" - wiping would kill the advertised
                    // one-time-typing behavior. (The write rides the surviving scope;
                    // rememberPreference's setter would die with the dismissing composition.)
                    if (trimmed.isNotBlank()) {
                        context.applicationContext.dataStore.edit { it[UserPlaylistSharedByKey] = trimmed }
                    }
                    shareUserPlaylist(context, playlistId, playlistTitle, videoIds, trimmed.takeIf { it.isNotBlank() })
                }
            },
        )
    }
}
