package com.jtech.zemer.latestreleases

import androidx.navigation.NavController
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.tracking.PlaySource

/**
 * Decides what tapping a [LatestRelease] does, shared by the Home shelf and the "See all" list so the
 * behaviour can't drift between them.
 *
 * The server tells singles from albums via [LatestRelease.trackCount]: a one-track release is a single
 * the user expects to just play, while a multi-track album opens its page. [isPlayableSingle] is the
 * pure predicate for that ("exactly one track, with a playable videoId"); [playableSingle] returns the
 * track's [MediaMetadata] when it holds, else null (a real album, or an older cached feed with no track
 * count), so the rule is unit-testable. The UI uses [isPlayableSingle] to show a centred play button on
 * a single's artwork (like the song cards on Home), and [openOrPlay] is the thin tap action.
 */
fun LatestRelease.isPlayableSingle(): Boolean = trackCount == 1 && !sampleVideoId.isNullOrEmpty()

/**
 * Whether [mediaMetadata] (the player's current track) is THIS release playing right now, so the card
 * shows its active/playing state and drops the centred play overlay. A single plays as a videoId via
 * [openOrPlay] (its [MediaMetadata] carries no album), so it matches on the track id; an album is
 * "active" when a track from it ([browseId]) is playing — the album-card convention used across Home.
 */
fun LatestRelease.isNowPlaying(mediaMetadata: MediaMetadata?): Boolean =
    if (isPlayableSingle()) mediaMetadata?.id == sampleVideoId
    else mediaMetadata?.album?.id == browseId

/**
 * The release's sample track as a [MediaMetadata], or null if the feed gave no [sampleVideoId]. This
 * is the single source of how a release becomes a playable track — used for a single's tap
 * ([playableSingle]) and for shuffle-playing the whole feed ([sampleTracks]).
 */
fun LatestRelease.sampleMediaMetadata(): MediaMetadata? {
    val videoId = sampleVideoId
    if (videoId.isNullOrEmpty()) return null
    return MediaMetadata(
        id = videoId,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = artistId, name = artistName)),
        duration = 0, // unknown until the track loads; filled in once playback starts
        thumbnailUrl = thumbnail,
    )
}

/** The track a single plays on tap: its [sampleMediaMetadata], but only for a one-track single. */
fun LatestRelease.playableSingle(): MediaMetadata? =
    if (isPlayableSingle()) sampleMediaMetadata() else null

/** Plays a single immediately (with autoplay radio, like the rest of Home); opens an album's page. */
fun LatestRelease.openOrPlay(
    navController: NavController,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
) {
    val single = playableSingle()
    if (single != null) {
        playerConnection.playQueue(YouTubeQueue.radio(single, database, PlaySource.NEW))
    } else {
        navController.navigate("album/$browseId")
    }
}

/**
 * The sample track of every release that has one — the tracks a shuffle play draws from. Pure (no
 * player/Android), so the selection is unit-testable; [shufflePlay] turns it into a queue.
 */
fun List<LatestRelease>.sampleTracks(): List<MediaMetadata> = mapNotNull { it.sampleMediaMetadata() }

/** Shuffle-plays the feed's sample tracks as a flat queue (no-op when none are playable). */
fun List<LatestRelease>.shufflePlay(playerConnection: PlayerConnection, title: String) {
    val items = sampleTracks().map { it.toMediaItem() }
    if (items.isEmpty()) return
    playerConnection.playQueue(ListQueue(title = title, items = items.shuffled(), playSource = PlaySource.NEW))
}
