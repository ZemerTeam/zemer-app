package com.jtech.zemer.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime

/**
 * Guards the persisted-queue wire format ([MusicService]'s ObjectInputStream restore).
 *
 * persistent_queue_v37.bin is a [PersistQueue] serialized by the v37 class shape (whose
 * [MediaMetadata] still carried the since-removed `explicit` field). Deserializing it proves the
 * pinned serialVersionUIDs keep an updating user's on-disk queue restorable: an unpinned edit to
 * any class in the graph changes its computed SUID and fails this test with InvalidClassException
 * - which in production is swallowed by the restore's runCatching and silently drops the queue.
 */
class PersistQueueCompatTest {
    private fun deserialize(bytes: ByteArray): PersistQueue =
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PersistQueue }

    @Test
    fun `v37 on-disk queue blob still deserializes`() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/persistqueue/persistent_queue_v37.bin")
        ) { "fixture missing" }.use { it.readBytes() }

        val queue = deserialize(bytes)

        assertEquals("My queue", queue.title)
        assertEquals(1, queue.mediaItemIndex)
        assertEquals(42_000L, queue.position)
        assertTrue(queue.queueType is QueueType.LOCAL_ALBUM_RADIO)
        val data = queue.queueData as QueueData.LocalAlbumRadioData
        assertEquals("MPREb_album", data.albumId)
        assertEquals("OLAK5uy_x", data.playlistId)

        assertEquals(2, queue.items.size)
        val song = queue.items[0]
        assertEquals("dQw4w9WgXcQ", song.id)
        assertEquals("Song one", song.title)
        assertEquals(listOf(MediaMetadata.Artist("UCartist1", "Artist One")), song.artists)
        assertEquals(213, song.duration)
        assertEquals(MediaMetadata.Album("MPREb_abc", "Album One"), song.album)
        assertEquals("SVID", song.setVideoId)
        assertTrue(song.liked)
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), song.likedDate)
        assertEquals(LocalDateTime.of(2026, 2, 3, 4, 5, 6), song.inLibrary)
        assertEquals("addTok", song.libraryAddToken)
        assertEquals("removeTok", song.libraryRemoveToken)
        assertTrue(song.isVideo)
        assertEquals("Episode two", queue.items[1].title)
        assertTrue(queue.items[1].isEpisode)
    }

    @Test
    fun `current class round-trips`() {
        val queue = PersistQueue(
            title = null,
            items = listOf(MediaMetadata(id = "abc", title = "t", artists = emptyList(), duration = 1)),
            mediaItemIndex = 0,
            position = 0L,
            queueType = QueueType.YOUTUBE,
            queueData = QueueData.YouTubeData(endpoint = "e", continuation = "c"),
        )
        val bytes = ByteArrayOutputStream().also { bos ->
            ObjectOutputStream(bos).use { it.writeObject(queue) }
        }.toByteArray()
        val restored = deserialize(bytes)
        assertEquals(queue, restored)
        assertNotNull(restored.items.single())
    }
}
