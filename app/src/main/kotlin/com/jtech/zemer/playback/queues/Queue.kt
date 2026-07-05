package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * Tracking (docs/tracking/README.md): where plays of this queue's user-chosen items report as
     * starting. Surfaces with a spec taxonomy value (search, album:…, zemer:…) pass it at
     * construction; everything else defaults to "other".
     */
    val playSource: String get() = PlaySource.OTHER

    /**
     * Tracking: whether [getInitialStatus] items are the user-chosen context (an album/playlist's
     * tracks) or autoplay fill beyond [preloadItem] (a radio watch playlist) — the latter reports
     * as "radio".
     */
    val initialItemsAreContext: Boolean get() = true

    /**
     * Tracking: whether [nextPage] items STILL belong to the chosen context. Spec §3.3: tracks that
     * continue from an originally-chosen context KEEP its source — page 2+ of a chosen playlist is
     * context; an album radio's continuation beyond the album is autoplay ("radio").
     */
    val continuationIsContext: Boolean get() = false

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }
