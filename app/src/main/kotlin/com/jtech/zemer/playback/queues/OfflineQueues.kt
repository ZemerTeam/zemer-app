package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.constants.SongSortType
import com.jtech.zemer.constants.VideoDownloadsInMusicKey
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.models.toMediaMetadata
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.OfflineModeState
import com.jtech.zemer.utils.dataStore
import com.jtech.zemer.utils.getSuspend
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Offline-mode queue building (manual offline mode, #366). Online behavior is untouched: every
 * helper here branches to the exact queue the surface used before when the mode is off.
 */

/**
 * A song tap on a song row: offline builds a plain [ListQueue] over the VISIBLE list starting at the
 * tapped song (skips advance through the user's own downloads — a radio fill would just fail);
 * online keeps the standard seed-first song radio ([ZemerRadioQueue.song]) on the tapped row
 * exactly as before.
 */
fun songTapQueue(
    songs: List<Song>,
    tapped: Song,
    title: String?,
    context: Context,
    playSource: String = PlaySource.OTHER,
): Queue =
    if (OfflineModeState.enabled) {
        ListQueue(
            title = title,
            items = songs.map { it.toMediaItem() },
            startIndex = offlineStartIndex(songs, tapped.id),
            playSource = playSource,
        )
    } else {
        ZemerRadioQueue.song(tapped.toMediaMetadata(), context, playSource)
    }

/**
 * Where the offline visible-list queue starts: the tapped song's position in the row, floored at 0
 * so a stale tap (song no longer in the list) still plays the row instead of crashing media3 with
 * an out-of-range index. Pure for the JVM test.
 */
internal fun offlineStartIndex(songs: List<Song>, tappedId: String): Int =
    songs.indexOfFirst { it.id == tappedId }.coerceAtLeast(0)

/**
 * The offline replacement for Home's "Radio mode" shuffle: every downloaded song, shuffled. The
 * catalog + preference reads happen inside [getInitialStatus] (the Queue contract's suspend seam),
 * never on the UI path. Finite — no continuation. Holds only the application context (MusicService
 * retains the queue for the whole session).
 */
class DownloadedSongsQueue(
    context: Context,
    private val database: MusicDatabase,
    override val playSource: String = PlaySource.OTHER,
) : Queue {
    private val context = context.applicationContext

    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val includeVideos = context.dataStore.getSuspend(VideoDownloadsInMusicKey, true)
        val songs = database.downloadedSongs(SongSortType.CREATE_DATE, descending = true, includeVideos)
            .first()
            .shuffled()
        Queue.Status(title = null, items = songs.map { it.toMediaItem() }, mediaItemIndex = 0)
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
