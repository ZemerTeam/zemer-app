package com.jtech.zemer.recognition

import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the history-entry → MediaMetadata seed conversion the radio replay persists through
 * `MusicService.recoverSong`: the -1 unknown-duration sentinel (0 would be stored as a real length
 * and never repaired) and real artist browse ids from [RecognitionHistoryEntity.artistIds] (a null
 * id persists a bogus local artist row and dead-presses the now-playing artist tap).
 */
class RecognitionHistoryPlaybackTest {

    private fun entry(artist: String, artistIds: String) = RecognitionHistoryEntity(
        songId = "vid1",
        title = "Title",
        artist = artist,
        thumbnailUrl = "https://thumb",
        artistIds = artistIds,
    )

    @Test
    fun `duration is the -1 unknown sentinel so recoverSong fetches the real length`() {
        assertEquals(-1, entry("A", "UC1").toMediaMetadata().duration)
    }

    @Test
    fun `single artist carries its browse id`() {
        val artists = entry("Abie Rotenberg", "UC1").toMediaMetadata().artists
        assertEquals(listOf("Abie Rotenberg"), artists.map { it.name })
        assertEquals(listOf("UC1"), artists.map { it.id })
    }

    @Test
    fun `multiple artists pair names with ids in order`() {
        val artists = entry("A, B", "UC1,UC2").toMediaMetadata().artists
        assertEquals(listOf("A" to "UC1", "B" to "UC2"), artists.map { it.name to it.id })
    }

    @Test
    fun `count mismatch keeps null ids rather than mis-attribute a channel`() {
        // joinIds drops a null/blank id, so two names can sit next to one id — pairing would guess.
        val artists = entry("A, B", "UC1").toMediaMetadata().artists
        assertEquals(listOf("A", "B"), artists.map { it.name })
        assertEquals(listOf(null, null), artists.map { it.id })
    }

    @Test
    fun `no stored ids keeps the display artist with a null id`() {
        val artists = entry("A", "").toMediaMetadata().artists
        assertEquals(listOf("A"), artists.map { it.name })
        assertNull(artists.single().id)
    }

    @Test
    fun `id and thumbnail carry over`() {
        val metadata = entry("A", "UC1").toMediaMetadata()
        assertEquals("vid1", metadata.id)
        assertEquals("Title", metadata.title)
        assertEquals("https://thumb", metadata.thumbnailUrl)
    }
}
