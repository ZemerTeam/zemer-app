package com.jtech.zemer.ui.utils

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.playback.PlayerConnection
import com.jtech.zemer.playback.queues.YouTubeQueue
import com.jtech.zemer.utils.filterWhitelisted
import com.jtech.zemer.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The song behind the 5-tap Home-title easter egg (`music.zemer.io/watch?v=<this>`). */
const val HOME_EASTER_EGG_VIDEO_ID = "84mNcwGCIUE"

/** Taps required to trigger the easter egg. */
const val HOME_EASTER_EGG_TAPS = 5

/** A gap longer than this between taps resets the count (an idle tap is not a sequence). */
const val HOME_EASTER_EGG_TAP_WINDOW_MS = 1_500L

/**
 * Pure tap-sequence counter (unit-tested): the new count after a tap at [nowMs], resetting to 1
 * when the gap since [lastTapAtMs] exceeds the window. The caller fires at [HOME_EASTER_EGG_TAPS].
 */
fun easterEggTapCount(previousCount: Int, lastTapAtMs: Long, nowMs: Long): Int =
    if (nowMs - lastTapAtMs > HOME_EASTER_EGG_TAP_WINDOW_MS) 1 else previousCount + 1

/**
 * Plays the easter-egg song EXACTLY like an incoming `music.zemer.io/watch` deep link
 * (MainActivity's handler): resolve via `YouTube.queue`, run the whitelist filter (the kosher
 * guarantee applies to easter eggs too - a non-whitelisted egg is silently ignored), then start the
 * standard watch-endpoint queue. Failures are reported, never surfaced - it's an egg.
 */
suspend fun playHomeEasterEgg(playerConnection: PlayerConnection, database: MusicDatabase) {
    YouTube.queue(listOf(HOME_EASTER_EGG_VIDEO_ID)).onSuccess { queue ->
        val filtered = queue.filterWhitelisted(database).filterIsInstance<SongItem>()
        val song = filtered.firstOrNull() ?: return
        withContext(Dispatchers.Main) {
            playerConnection.playQueue(
                YouTubeQueue(
                    WatchEndpoint(videoId = song.id),
                    song.toMediaMetadata(),
                    database,
                ),
            )
        }
    }.onFailure(::reportException)
}
