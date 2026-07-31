@file:Suppress("ClassName", "unused")

package com.jtech.zemer.models

import java.io.Serializable

data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
) : Serializable

sealed class QueueType : Serializable {
    object LIST : QueueType() {
        private fun readResolve(): Any = LIST
    }

    object YOUTUBE : QueueType() {
        private fun readResolve(): Any = YOUTUBE
    }

    // Retained for backward-compat deserialization only: the YouTubeAlbumRadio queue was removed
    // (albums play through LocalAlbumRadio), but an on-disk queue persisted by an older build can
    // still carry this tag. QueueExt.toQueue() restores it as a plain ListQueue. Do not reuse.
    object YOUTUBE_ALBUM_RADIO : QueueType() {
        private fun readResolve(): Any = YOUTUBE_ALBUM_RADIO
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private fun readResolve(): Any = LOCAL_ALBUM_RADIO
    }
}

sealed class QueueData : Serializable {
    data class YouTubeData(
        val endpoint: String,
        val continuation: String? = null
    ) : QueueData()
    
    // Retained for backward-compat deserialization only (see [QueueType.YOUTUBE_ALBUM_RADIO]); no
    // longer written. Kept so an older on-disk queue blob still deserializes instead of being dropped.
    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData()
    
    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData()
}
