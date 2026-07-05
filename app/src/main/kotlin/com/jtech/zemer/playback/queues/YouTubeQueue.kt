package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.filterWhitelisted
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    private val database: MusicDatabase,
    override val playSource: String = PlaySource.OTHER,
) : Queue {
    private var continuation: String? = null

    // A real playlist endpoint makes the initial items user-chosen context; a bare videoId or a
    // song-radio watch-playlist (RDAMVM<videoId>) means everything beyond the tapped song is
    // autoplay fill, which must report as "radio". Only the RDAMVM song-radio prefix is fill —
    // other RD-prefixed ids are user-CHOSEN contexts (YT Music editorial playlists "RDCLAK5uy_…",
    // artist shuffle "RDAO…") and keep their source. Captured at construction — endpoint mutates.
    override val initialItemsAreContext: Boolean =
        endpoint.playlistId?.startsWith("RDAMVM") == false

    // Page 2+ of a chosen playlist is still the chosen context; a radio queue's pages stay radio.
    override val continuationIsContext: Boolean get() = initialItemsAreContext

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation

        // Filter by whitelist before converting to MediaItems
        val filteredItems = nextResult.items.filterWhitelisted(database).filterIsInstance<SongItem>()

        return Queue.Status(
            title = nextResult.title,
            items = filteredItems.map { it.toMediaItem() },
            mediaItemIndex = nextResult.currentIndex ?: 0,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation

        // Filter by whitelist before converting to MediaItems
        val filteredItems = nextResult.items.filterWhitelisted(database).filterIsInstance<SongItem>()

        return filteredItems.map { it.toMediaItem() }
    }

    companion object {
        fun radio(
            song: MediaMetadata,
            database: MusicDatabase,
            playSource: String = PlaySource.OTHER,
        ) = YouTubeQueue(WatchEndpoint(song.id), song, database, playSource)
    }
}
