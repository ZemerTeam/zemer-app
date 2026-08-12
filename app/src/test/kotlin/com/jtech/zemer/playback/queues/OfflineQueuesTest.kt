package com.jtech.zemer.playback.queues

import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The offline visible-list queue's start-index rule (manual offline mode, #366): start at the
 * tapped song, and floor a stale tap at 0 instead of handing media3 an out-of-range index.
 */
class OfflineQueuesTest {

    private fun song(id: String) = Song(
        song = SongEntity(id = id, title = "title-$id", isDownloaded = true),
        artists = emptyList(),
    )

    @Test
    fun `starts at the tapped song's position`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        assertEquals(1, offlineStartIndex(songs, "b"))
        assertEquals(2, offlineStartIndex(songs, "c"))
        assertEquals(0, offlineStartIndex(songs, "a"))
    }

    @Test
    fun `a tap on a song no longer in the list floors at zero`() {
        assertEquals(0, offlineStartIndex(listOf(song("a"), song("b")), "gone"))
    }

    @Test
    fun `an empty list floors at zero`() {
        assertEquals(0, offlineStartIndex(emptyList(), "x"))
    }
}
