package com.jtech.zemer.latestreleases

import android.content.Context
import androidx.navigation.NavController
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.ListQueue
import com.jtech.zemer.playback.queues.LocalAlbumRadio
import com.jtech.zemer.playback.queues.ZemerRadioQueue
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

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

/**
 * Plays a release's ALBUM directly - the corner play button on the Latest Releases card, restoring the
 * affordance the pre-carousel card had - WITHOUT reintroducing InnerTube. Prefers the locally-stored rows
 * (a downloaded album plays offline like the rest of Home); otherwise fetches the album ONCE from the
 * Zemer server (the same path the album screen opens through) and plays the fetched tracks directly, so
 * the fire-and-forget DB insert (done in the background for library consistency) can never race the queue.
 * Fail-soft: a 404 / empty / failed fetch just opens the album page rather than leaving a dead button.
 * Only meaningful for a real album - a single plays via [openOrPlay].
 */
suspend fun LatestRelease.playAlbum(
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    zemerRepository: ZemerSearchRepository,
    context: Context,
    navController: NavController,
) {
    val local = database.albumWithSongs(browseId).firstOrNull()
    if (local != null && local.songs.isNotEmpty()) {
        withContext(Dispatchers.Main) {
            playerConnection.playQueue(LocalAlbumRadio(local, context = context))
        }
        return
    }
    val page = runCatching { zemerRepository.album(browseId, null, zemerSearchOptions(context)) }.getOrNull()
    if (page == null || page.songs.isEmpty()) {
        withContext(Dispatchers.Main) { navController.navigate("album/$browseId") }
        return
    }
    val existing = database.album(browseId).first()
    database.transaction {
        if (existing == null) insert(page) else update(existing.album, page, existing.artists)
    }
    withContext(Dispatchers.Main) {
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = page.songs.map { it.toMediaItem() },
                playSource = PlaySource.NEW,
            )
        )
    }
}

/** Plays a single immediately (with autoplay radio, like the rest of Home); opens an album's page. */
fun LatestRelease.openOrPlay(
    navController: NavController,
    playerConnection: PlayerConnection,
) {
    val single = playableSingle()
    if (single != null) {
        playerConnection.playQueue(ZemerRadioQueue.song(single, playerConnection.service, PlaySource.NEW))
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
