package com.jtech.zemer.playback

import androidx.media3.common.MediaItem
import com.jtech.zemer.playback.queues.continuationItemsToAppend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the auto-load-more append rule: YouTube-style continuation pages lead with the
 * already-queued current item, Zemer `/radio` pages are pure fresh tracks. Deduping against the
 * queued ids handles both — the old blanket `drop(1)` silently discarded the first (top-ranked)
 * track of every Zemer page (and a 1-track page appended nothing at all).
 */
class QueueContinuationTest {

    private fun item(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    @Test
    fun `youtube-style page leading with the queued current item appends only the fresh tail`() {
        val page = listOf(item("current"), item("n1"), item("n2"))

        val appended = continuationItemsToAppend(setOf("prev", "current"), page)

        assertEquals(listOf("n1", "n2"), appended.map { it.mediaId })
    }

    @Test
    fun `zemer page of pure fresh tracks is appended whole - first track is not dropped`() {
        val page = listOf(item("z1"), item("z2"), item("z3"))

        val appended = continuationItemsToAppend(setOf("prev", "current"), page)

        assertEquals(listOf("z1", "z2", "z3"), appended.map { it.mediaId })
    }

    @Test
    fun `single fresh-track page survives - the old drop(1) appended nothing`() {
        val appended = continuationItemsToAppend(setOf("current"), listOf(item("z1")))

        assertEquals(listOf("z1"), appended.map { it.mediaId })
    }
}
