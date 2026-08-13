package com.jtech.zemer.playback

import androidx.media3.common.MediaItem
import com.jtech.zemer.playback.queues.clampStartIndex
import com.jtech.zemer.playback.queues.filterBlockedEpisodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Guards the playback-side Block Podcasts gate (the v37 leak fix): with the flag OFF the filter is
 * a strict identity (no regression risk for every other user), and the start-index re-clamp keeps a
 * filtered/emptied queue from handing media3 an out-of-range index.
 *
 * The blocked=true drop itself reads `metadata.isEpisode` off the MediaItem tag, which needs a Uri
 * (Android runtime) to attach — the pure pieces are pinned here; the tag read is one line of glue.
 */
class BlockedPodcastsQueueTest {

    private fun item(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    @Test
    fun `filter with the flag off is a strict identity - same list instance`() {
        val items = listOf(item("a"), item("b"))

        assertSame(items, items.filterBlockedEpisodes(false))
    }

    @Test
    fun `tagless items survive the filter - only a positive isEpisode is dropped`() {
        val items = listOf(item("a"), item("b"))

        assertEquals(listOf("a", "b"), items.filterBlockedEpisodes(true).map { it.mediaId })
    }

    @Test
    fun `start index inside the shrunk list is kept`() {
        assertEquals(2, clampStartIndex(2, 5))
    }

    @Test
    fun `start index past the shrunk list clamps to the last item`() {
        assertEquals(3, clampStartIndex(7, 4))
    }

    @Test
    fun `emptied list clamps to zero - never -1`() {
        assertEquals(0, clampStartIndex(3, 0))
    }

    @Test
    fun `negative index clamps to zero`() {
        assertEquals(0, clampStartIndex(-1, 4))
    }
}
