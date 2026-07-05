package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    override val playSource: String = PlaySource.OTHER,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
