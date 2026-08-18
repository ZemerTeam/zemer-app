package com.jtech.zemer.playback.queues

import androidx.media3.common.MediaItem
import com.jtech.zemer.extensions.metadata
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.tracking.PlaySource

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * The id of the item the user just tapped to play, for the tap-to-play loading spinner
     * (PlayerConnection.preparingMediaId + rememberIsPreparing). Defaults to [preloadItem]'s id; a queue
     * whose target is NOT the preload (ListQueue plays its startIndex item, with no preload) overrides
     * this so the tapped card shows the spinner. UI-feedback only - never affects playback.
     */
    val preparingItemId: String? get() = preloadItem?.id

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

        /**
         * Drops podcast episodes when Block Podcasts is on (the filterExplicit pattern). The start
         * index is re-clamped because dropped episodes can shift or empty the list — a stale index
         * would crash [androidx.media3.common.Player.setMediaItems].
         */
        fun filterBlockedPodcasts(blocked: Boolean) =
            if (blocked) {
                val kept = items.filterBlockedEpisodes(true)
                copy(
                    items = kept,
                    mediaItemIndex = clampStartIndex(mediaItemIndex, kept.size),
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

/**
 * A queue start index re-clamped after filtering shrank the item list: media3's setMediaItems
 * crashes on an out-of-range start index, and an emptied list must clamp to 0, not -1.
 */
fun clampStartIndex(index: Int, size: Int): Int = index.coerceIn(0, (size - 1).coerceAtLeast(0))

/** Drops podcast episodes when Block Podcasts is on — the playback-side content gate. */
fun List<MediaItem>.filterBlockedEpisodes(blocked: Boolean) =
    if (blocked) {
        filterNot {
            it.metadata?.isEpisode == true
        }
    } else {
        this
    }

/**
 * The continuation-page items safe to append to the player. YouTube-style continuations lead with
 * the already-queued current item; Zemer `/radio` pages are pure fresh tracks. Deduping against the
 * ids already in the player handles both — the old blanket `drop(1)` silently discarded the first
 * (top-ranked) track of every Zemer page.
 */
fun continuationItemsToAppend(queuedIds: Set<String>, page: List<MediaItem>): List<MediaItem> =
    page.filterNot { it.mediaId in queuedIds }
