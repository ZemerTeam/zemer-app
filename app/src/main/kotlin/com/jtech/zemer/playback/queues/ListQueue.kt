package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource
import com.metrolist.innertube.models.EpisodeItem

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    override val playSource: String = PlaySource.OTHER,
) : Queue {
    companion object {
        /**
         * The one way to play a single tapped episode: a plain one-item queue with the surface's
         * declared source. Never ZemerRadioQueue.song — an episode must not seed music radio around
         * its videoId (two call sites drifted exactly that way once).
         */
        fun episode(item: EpisodeItem, playSource: String) = ListQueue(
            title = item.title,
            items = listOf(item.toMediaItem()),
            playSource = playSource,
        )
    }
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
