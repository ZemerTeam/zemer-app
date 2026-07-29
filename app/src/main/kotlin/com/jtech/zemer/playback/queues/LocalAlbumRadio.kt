package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.db.entities.AlbumWithSongs
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * Plays a local album's tracks, then continues with corpus-native radio seeded by that album — Zemer
 * `/radio?kind=album` (whitelist-pure, opaque-token paging) instead of `YouTube.next()`. The album's own
 * tracks are the chosen context; the continuation beyond them reports as "radio". Selection only — the
 * audio stream stays InnerTube + cipher.
 */
class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
    context: Context,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override val playSource: String = PlaySource.album(albumWithSongs.album.id)

    // MusicService retains currentQueue for the whole playback session; callers hand in whatever
    // Context they have (often the Activity), so only the application context may be held.
    private val context = context.applicationContext

    // Resolved from the application context — LocalAlbumRadio is built in leaf composables with no VM.
    private val repository = EntryPointAccessors
        .fromApplication(context.applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
        .zemerSearchRepository()

    private var continuation: String? = null
    private var firstTimeLoaded = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex,
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        val page = if (!firstTimeLoaded) {
            // Flip only after the fetch succeeds: a transient /radio failure must leave
            // hasNextPage() true so a later transition retries instead of ending the radio forever.
            repository.radio("album", albumWithSongs.album.id, zemerSearchOptions(context))
                .also { firstTimeLoaded = true }
        } else {
            val token = continuation ?: return@withContext emptyList()
            repository.radioContinuation(token)
        }
        continuation = page.continuation
        page.songs.map { it.toMediaItem() }
    }
}
