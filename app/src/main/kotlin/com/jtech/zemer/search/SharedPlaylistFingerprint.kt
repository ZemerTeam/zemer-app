package com.jtech.zemer.search

import java.security.MessageDigest

/**
 * The fingerprint of a shared playlist's SERVED state (issue #176 live-updating shares): what the
 * auto-updater compares against [ShareCredentials.syncedHash] (the DataStore credential map -
 * deliberately not a DB column) to decide whether a PUT is due.
 * Applies the SAME clamps as [ZemerSearchRepository.shareUserPlaylist]/`updateUserPlaylist`, so an
 * over-limit playlist hashes to the state the server actually holds - otherwise the updater would
 * re-PUT forever. Order-sensitive by design (reordering IS an edit). The newline joiner cannot
 * appear in a videoId, and the title goes LAST so a title ending in an id-like token cannot
 * collide with a member id.
 */
fun sharedPlaylistFingerprint(title: String, videoIds: List<String>): String {
    val canonical = (videoIds.take(USER_PLAYLIST_MAX_TRACKS) + title.take(USER_PLAYLIST_TITLE_MAX))
        .joinToString("\n")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
