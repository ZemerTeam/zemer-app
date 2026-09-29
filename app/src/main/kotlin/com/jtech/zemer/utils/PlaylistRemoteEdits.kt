package com.jtech.zemer.utils

import com.jtech.zemer.db.entities.PlaylistSongMap
import com.jtech.zemer.models.MediaMetadata

/**
 * Pure logic for keeping remote (YouTube) playlist edits working. YouTube's remove/move playlist
 * endpoints identify the ENTRY, not the song, via `setVideoId` — so the playlist sync must persist
 * it on every [PlaylistSongMap], and edits must be skipped (never sent malformed) when it is absent.
 * Kept free of Android/DB dependencies so plain JVM tests pin both rules.
 */

/** Arguments for [com.metrolist.innertube.YouTube.removeFromPlaylist], or null to skip the remote call. */
data class RemotePlaylistRemoval(
    val browseId: String,
    val videoId: String,
    val setVideoId: String,
)

/**
 * Null unless ALL conditions for a remote removal hold: a personal account (anonymous/pooled
 * sessions are local-only), a remote-backed playlist (non-null browseId), and a known entry id.
 */
fun remotePlaylistRemovalArgs(
    isPersonalAccount: Boolean,
    browseId: String?,
    videoId: String,
    setVideoId: String?,
): RemotePlaylistRemoval? =
    if (isPersonalAccount && browseId != null && setVideoId != null) {
        RemotePlaylistRemoval(browseId, videoId, setVideoId)
    } else {
        null
    }

/**
 * The playlist-sync rows for a fetched remote playlist, in remote order. Persisting
 * [MediaMetadata.setVideoId] here is what makes later remote remove/reorder possible — omitting it
 * was the regression that silently broke remote playlist edits.
 */
fun playlistSongMaps(songs: List<MediaMetadata>, playlistId: String): List<PlaylistSongMap> =
    songs.mapIndexed { index, song ->
        PlaylistSongMap(
            songId = song.id,
            playlistId = playlistId,
            position = index,
            setVideoId = song.setVideoId,
        )
    }

/**
 * Whether a synced playlist must be rebuilt from the whitelist-filtered remote entries [filtered]
 * given its current [local] entries (both `(songId, setVideoId)` in order). Never rebuilds to
 * empty: a remote read that filtering reduced to nothing (a sparse/topic-channel renderer, issue
 * #130) keeps the local songs instead of wiping them. A de-whitelisted artist's songs are removed by
 * the whitelist sync itself, so the kosher guarantee never depends on this path clearing a playlist.
 * Pairs, not ids: an unchanged membership still rebuilds once to backfill a missing setVideoId.
 */
fun playlistRebuildNeeded(
    filtered: List<Pair<String, String?>>,
    local: List<Pair<String, String?>>,
): Boolean = filtered.isNotEmpty() && filtered != local
