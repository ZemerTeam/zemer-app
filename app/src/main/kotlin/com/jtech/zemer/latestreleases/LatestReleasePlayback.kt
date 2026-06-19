package com.jtech.zemer.latestreleases

import androidx.navigation.NavController
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.YouTubeQueue

/**
 * Decides what tapping a [LatestRelease] does, shared by the Home shelf and the "See all" list so the
 * behaviour can't drift between them.
 *
 * The server tells singles from albums via [LatestRelease.trackCount]: a one-track release is a single
 * the user expects to just play, while a multi-track album opens its page. [playableSingle] is the pure
 * (Android-free) heart of that decision — it returns the track's [MediaMetadata] only when the release
 * is a playable single, and null otherwise (a real album, or an older cached feed with no track count),
 * so the rule is unit-testable. [openOrPlay] is the thin UI action built on top of it.
 */
fun LatestRelease.playableSingle(): MediaMetadata? {
    val videoId = sampleVideoId
    if (trackCount != 1 || videoId.isNullOrEmpty()) return null
    return MediaMetadata(
        id = videoId,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = artistId, name = artistName)),
        duration = 0, // unknown until the track loads; filled in once playback starts
        thumbnailUrl = thumbnail,
    )
}

/** Plays a single immediately (with autoplay radio, like the rest of Home); opens an album's page. */
fun LatestRelease.openOrPlay(
    navController: NavController,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
) {
    val single = playableSingle()
    if (single != null) {
        playerConnection.playQueue(YouTubeQueue.radio(single, database))
    } else {
        navController.navigate("album/$browseId")
    }
}
