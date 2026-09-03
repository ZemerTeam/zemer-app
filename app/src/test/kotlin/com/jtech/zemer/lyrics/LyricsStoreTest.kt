package com.jtech.zemer.lyrics

import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.db.entities.LyricsEntity.Companion.PROVIDER_LEGACY
import com.jtech.zemer.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one fetch-and-persist path: cache decision, chain, row policy, episode skip; Refetch replaces in place. */
class LyricsStoreTest {
    private val song = MediaMetadata(id = "v", title = "t", artists = emptyList(), duration = 100)
    private val episode = song.copy(id = "e", isEpisode = true)

    private class Fake(var row: LyricsEntity?, private val answer: LyricsHelper.Fetched, private val fetchDelayMs: Long = 0) {
        val persisted = mutableListOf<LyricsEntity>()
        val deleted = mutableListOf<LyricsEntity>()
        var fetches = 0
        val store = LyricsStore(
            cached = { row },
            persist = { persisted += it },
            delete = { deleted += it; row = null },
            fetch = { fetches++; if (fetchDelayMs > 0) delay(fetchDelayMs); answer },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    @Test
    fun `nothing cached fetches and persists the answer with its provenance`() = runBlocking {
        val f = Fake(null, LyricsHelper.Fetched("[00:01.00] a", "SimpMusic"))
        assertTrue(f.store.ensure(song))
        assertEquals(listOf(LyricsEntity("v", "[00:01.00] a", "SimpMusic")), f.persisted)
    }

    @Test
    fun `a row with known provenance or a negative cache is never re-fetched`() = runBlocking {
        for (row in listOf(LyricsEntity("v", "words", "Zemer · jyrics"), LyricsEntity("v", LYRICS_NOT_FOUND, null), LyricsEntity("v", "words", PROVIDER_LEGACY))) {
            val f = Fake(row, LyricsHelper.Fetched("new", "SimpMusic"))
            assertFalse(f.store.ensure(song))
            assertEquals(0, f.fetches)
            assertTrue(f.persisted.isEmpty())
        }
    }

    @Test
    fun `a legacy plain row is re-resolved once and kept as legacy`() = runBlocking {
        val f = Fake(LyricsEntity("v", "typed by the user", null), LyricsHelper.Fetched("[00:01.00] a", "SimpMusic"))
        assertTrue(f.store.ensure(song))
        assertEquals(listOf(LyricsEntity("v", "typed by the user", PROVIDER_LEGACY)), f.persisted)
    }

    @Test
    fun `episodes never fetch or store`() = runBlocking {
        val f = Fake(null, LyricsHelper.Fetched("words", "SimpMusic"))
        assertFalse(f.store.ensure(episode))
        f.store.refetch(episode)
        assertEquals(0, f.fetches)
        assertTrue(f.persisted.isEmpty())
    }

    /** Regression: an in-place replace gave the user no feedback when the chain answered the same body; the delete lands first so the pane reloads. */
    @Test
    fun `refetch deletes the cached row first, then stores the fresh answer, manual text included`() = runBlocking {
        val manual = LyricsEntity("v", "typed by the user", "manual")
        val f = Fake(manual, LyricsHelper.Fetched("fresh", "LrcLib"))
        f.store.refetch(song)
        assertEquals(listOf(manual), f.deleted)
        assertEquals(listOf(LyricsEntity("v", "fresh", "LrcLib")), f.persisted)
    }

    /** The screen's own fetch (triggered by the deleted row) and the refetch share ONE chain walk. */
    @Test
    fun `concurrent refetch and ensure fetch once`() = runBlocking {
        val f = Fake(LyricsEntity("v", "old", "SimpMusic"), LyricsHelper.Fetched("fresh", "LrcLib"), fetchDelayMs = 200)
        val a = async { f.store.refetch(song) }
        delay(50)
        val b = async { f.store.ensure(song) }
        a.await(); assertTrue(b.await())
        assertEquals(1, f.fetches)
        assertTrue(f.persisted.all { it == LyricsEntity("v", "fresh", "LrcLib") })
    }
}
