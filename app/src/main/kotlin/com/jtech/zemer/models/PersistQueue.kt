@file:Suppress("ClassName", "unused")

package com.jtech.zemer.models

import java.io.Serializable

// Every class in the persisted-queue graph pins serialVersionUID to the value the JVM computed
// for the shipped class shape: Java serialization otherwise derives it from fields AND generated
// members, so any edit (even removing an ignored field) silently breaks queue restore for every
// updating user (InvalidClassException inside MusicService's runCatching). See MediaMetadata.
data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 6205246283638946736L
    }
}

sealed class QueueType : Serializable {
    object LIST : QueueType() {
        private const val serialVersionUID = 4782526689169494478L
        private fun readResolve(): Any = LIST
    }

    object YOUTUBE : QueueType() {
        private const val serialVersionUID = -6371886282197963506L
        private fun readResolve(): Any = YOUTUBE
    }

    // Retained for backward-compat deserialization only: the YouTubeAlbumRadio queue was removed
    // (albums play through LocalAlbumRadio), but an on-disk queue persisted by an older build can
    // still carry this tag. QueueExt.toQueue() restores it as a plain ListQueue. Do not reuse.
    object YOUTUBE_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID = -8679184717910033773L
        private fun readResolve(): Any = YOUTUBE_ALBUM_RADIO
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID = 9157685168464094704L
        private fun readResolve(): Any = LOCAL_ALBUM_RADIO
    }

    companion object {
        private const val serialVersionUID = 970467250907872187L
    }
}

sealed class QueueData : Serializable {
    data class YouTubeData(
        val endpoint: String,
        val continuation: String? = null
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 2918968381561078805L
        }
    }

    // Retained for backward-compat deserialization only (see [QueueType.YOUTUBE_ALBUM_RADIO]); no
    // longer written. Kept so an older on-disk queue blob still deserializes instead of being dropped.
    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 2586697328544440827L
        }
    }

    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData() {
        companion object {
            private const val serialVersionUID = 8409835281065702641L
        }
    }

    companion object {
        private const val serialVersionUID = -3316899089860851L
    }
}
