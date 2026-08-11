package com.jtech.zemer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Owns podcast-EPISODE resume: persisting where the user left off and seeking back there on the next
 * open. Extracted from [MusicService] (the giant-shrinking rule) so the whole resume policy lives in one
 * place instead of being spread across `onIsPlayingChanged` / `onMediaItemTransition` / `onDestroy`.
 * Songs are never touched, so music behaviour is unchanged; the cast receiver owns its own position, so
 * everything here no-ops while casting (`isCasting`). All pure resume-edge decisions live in the
 * unit-tested [EpisodeResume].
 *
 * Everything runs on [scope] (the service Main scope), so the tracking fields are single-thread-confined
 * and the player is only ever read on the main thread (never inside `database.query {}` — that posts to a
 * background executor and would trip "Player accessed on the wrong thread").
 */
class EpisodePositionTracker(
    private val player: Player,
    private val scope: CoroutineScope,
    private val database: MusicDatabase,
    private val isCasting: () -> Boolean,
) {
    private var positionSaverJob: Job? = null

    // The episode currently being tracked and its most recent known position. A track-to-track SWITCH
    // fires no pause, so the periodic/pause saves never capture the outgoing episode's final position -
    // [onTransition] flushes this remembered value before moving on (Metrolist's approach: "always save"
    // the episode you are leaving).
    private var previousEpisodeId: String? = null
    private var previousEpisodePosition = 0L

    /** Start/stop the 15s periodic save with playback (episode only; the pause save is the on-stop flush). */
    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startPositionSaver()
        } else {
            saveIfNeeded()
            positionSaverJob?.cancel()
        }
    }

    /**
     * On a track transition: flush the OUTGOING episode's last-known position, then, for an incoming
     * episode, start tracking + the periodic saver and schedule the resume seek. A NON-episode incoming
     * cancels the saver so it does not keep waking every 15s just to no-op for the rest of the session.
     */
    fun onTransition(incoming: MediaItem?) {
        // ALWAYS SAVE the episode we are LEAVING. A track-to-track switch fires no pause, so this flush
        // of the last-known position is the only chance to persist the outgoing episode.
        previousEpisodeId?.let { outgoing -> persistPosition(outgoing, previousEpisodePosition) }
        previousEpisodeId = null
        previousEpisodePosition = 0L

        val meta = incoming?.metadata
        if (!isCasting() && meta?.isEpisode == true) {
            val id = incoming.mediaId
            previousEpisodeId = id // start tracking the incoming episode
            // A song -> episode switch keeps isPlaying true, so onIsPlayingChanged never fires to start
            // the saver here (it is episode-gated + idempotent).
            startPositionSaver()
            // Known episode length (seconds -> ms), if the metadata carries it, for the completion check.
            val durationMs = meta.duration.takeIf { it > 0 }?.times(1000L)
            scope.launch {
                delay(100) // let playback start before seeking (Metrolist)
                val saved = database.episodePosition(id) ?: 0L
                if (EpisodeResume.shouldResume(saved, durationMs)) {
                    withContext(Dispatchers.Main) {
                        // Only seek if still on this item and still near the start (a user seek or a fast
                        // next-tap must not be overridden).
                        if (player.currentMediaItem?.mediaId == id &&
                            player.currentPosition < EpisodeResume.RESUME_EDGE_MS
                        ) {
                            player.seekTo(saved)
                        }
                    }
                }
            }
        } else {
            // Non-episode incoming: stop the periodic saver (it would just no-op every 15s otherwise).
            positionSaverJob?.cancel()
        }
    }

    /**
     * ALWAYS SAVE on teardown: flush the current episode's position before the player is released, so a
     * swipe-kill still resumes next time. Capture on the (main) caller thread, then block briefly on the
     * write since the player is about to go away.
     */
    fun onDestroyFlush() {
        player.currentMediaItem?.metadata?.takeIf { it.isEpisode && !isCasting() }?.let { meta ->
            val positionMs = player.currentPosition
            if (EpisodeResume.shouldSave(positionMs)) {
                runBlocking(Dispatchers.IO) { database.updateEpisodePosition(meta.id, positionMs) }
            }
        }
    }

    /**
     * Persist the current EPISODE's resume position while it plays (episodes only, local playback only).
     * Songs never write this, so their behaviour is unchanged. Runs on Main because it reads the player.
     */
    private fun startPositionSaver() {
        if (positionSaverJob?.isActive == true) return
        // Episodes only - never wake every 15s on the common music path (the loop would just no-op).
        if (player.currentMediaItem?.metadata?.isEpisode != true) return
        positionSaverJob = scope.launch(Dispatchers.Main) {
            while (isActive && player.isPlaying) {
                saveIfNeeded()
                delay(15.seconds)
            }
        }
    }

    /** Save the CURRENT item's position if it is an episode. Reads the player on the caller's (main)
     *  thread, remembers it for the on-switch flush, then persists off-thread. */
    private fun saveIfNeeded() {
        if (isCasting()) return
        val item = player.currentMediaItem ?: return
        val meta = item.metadata ?: return
        if (!meta.isEpisode) return
        val id = item.mediaId
        val positionMs = player.currentPosition
        previousEpisodeId = id
        previousEpisodePosition = positionMs
        persistPosition(id, positionMs, meta)
    }

    /**
     * Write an episode's resume position. Skips the "at the beginning" edge. The `song` row is normally
     * created by recoverSong at stream-resolve time, but that runs async and may not have landed for the
     * first save(s); if the UPDATE hits no row and we have the metadata, seed the row so the position is
     * never silently dropped. The position value is captured by the CALLER on the player's thread - never
     * read the player from inside database.query {} (background executor - wrong-thread crash).
     */
    private fun persistPosition(episodeId: String, positionMs: Long, meta: MediaMetadata? = null) {
        if (!EpisodeResume.shouldSave(positionMs)) return
        database.query {
            val rows = updateEpisodePosition(episodeId, positionMs)
            if (rows == 0 && meta != null) {
                insert(meta.toSongEntity().copy(lastPositionMs = positionMs))
            }
        }
    }
}
