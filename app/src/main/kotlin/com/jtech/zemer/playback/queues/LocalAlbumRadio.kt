package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.AlbumWithSongs
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.filterWhitelisted
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
    private val database: MusicDatabase,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    // The album's tracks are the chosen context; the radio continuation beyond them is "radio".
    override val playSource: String = PlaySource.album(albumWithSongs.album.id)

    private lateinit var playlistId: String
    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId,
            params = "wAEB"
        )

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (!firstTimeLoaded) {
            playlistId = YouTube.album(albumWithSongs.album.id).getOrThrow().album.playlistId
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            firstTimeLoaded = true

            // Filter by whitelist before converting to MediaItems
            val filteredItems = nextResult.items.subList(
                albumWithSongs.songs.size,
                nextResult.items.size
            ).filterWhitelisted(database).filterIsInstance<SongItem>()

            return@withContext filteredItems.map { it.toMediaItem() }
        }
        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = nextResult.continuation

        // Filter by whitelist before converting to MediaItems
        val filteredItems = nextResult.items.filterWhitelisted(database).filterIsInstance<SongItem>()

        filteredItems.map { it.toMediaItem() }
    }
}
