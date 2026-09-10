package com.jtech.zemer.lyrics

import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.jtech.zemer.db.entities.LyricsEntity.Companion.PROVIDER_LEGACY
import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
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

    private class Fake(var row: LyricsEntity?, private val answer: LyricsHelper.Fetched, private val fetchDelayMs: Long = 0, private val resolved: ZemerLyricsClient.LineExtras? = null, var reply: ZemerLyricsClient.ExtrasReply = ZemerLyricsClient.ExtrasReply(null, failed = false)) {
        val asked = mutableListOf<Triple<String, List<String>, String>>()
        val persisted = mutableListOf<LyricsEntity>()
        val deleted = mutableListOf<LyricsEntity>()
        var fetches = 0
        var resolves = 0
        var clock = 1_000L
        val extras = MapExtras()
        val store = LyricsStore(
            cached = { row },
            persist = { persisted += it },
            delete = { deleted += it; row = null },
            fetch = { fetches++; if (fetchDelayMs > 0) delay(fetchDelayMs); answer },
            extras = extras,
            resolveExtras = { resolves++; resolved },
            alignedExtras = { id, lines, lang -> asked += Triple(id, lines, lang); reply },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            now = { clock },
        )
    }
    private class MapExtras : LineExtrasStorage {
        val records = HashMap<String, LineExtrasRecord>()
        val deletedIds = mutableListOf<String>()
        override fun read(videoId: String) = records[videoId]
        override fun write(videoId: String, wire: ZemerLyricsClient.LineExtras?, now: Long) { records[videoId] = LineExtrasRecord(now, wire) }
        override fun writeAligned(videoId: String, lang: String, linesHash: String, wire: ZemerLyricsClient.LineExtras?, now: Long) {
            val cur = records[videoId] ?: LineExtrasRecord(now)
            records[videoId] = cur.copy(aligned = cur.aligned + (lang to AlignedExtras(linesHash, now, wire)))
        }
        override fun delete(videoId: String) { deletedIds += videoId; records.remove(videoId) }
    }
    private val wire = ZemerLyricsClient.LineExtras(keys = listOf("abcd1234"), en = listOf("A home"), source = "machine")

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

    @Test
    fun `prefetch warms the current and next songs through the cache gate and does nothing offline`() = runBlocking {
        val next = song.copy(id = "n")
        val f = Fake(null, LyricsHelper.Fetched("words", "Zemer · jyrics"))
        assertEquals(2, f.store.prefetch(song, next, connected = true))
        assertEquals(listOf("v", "n"), f.persisted.map { it.id })
        // offline: no chain walk, no not-found rows minted
        val off = Fake(null, LyricsHelper.Fetched(LYRICS_NOT_FOUND, null))
        assertEquals(0, off.store.prefetch(song, next, connected = false))
        assertEquals(0, off.fetches)
        // a cached current song costs no fetch; the same id twice fetches once; an episode next is skipped
        val cached = Fake(LyricsEntity("v", "words", "Zemer · jyrics"), LyricsHelper.Fetched("words", "SimpMusic"))
        assertEquals(0, cached.store.prefetch(song, song, connected = true))
        assertEquals(0, cached.store.prefetch(song, episode, connected = true))
        assertEquals(0, cached.fetches)
        assertEquals(0, Fake(null, LyricsHelper.Fetched("words", "SimpMusic")).store.prefetch(null, null, connected = true))
    }

    /** A chain answer re-records the song's extras every time (with or without them); not found drops them. */
    @Test
    fun `every chain answer records the extras it carried, not found clears them`() = runBlocking {
        val f = Fake(null, LyricsHelper.Fetched("[00:01.00] a", "Zemer", wire))
        f.store.ensure(song)
        assertEquals(LineExtrasRecord(1_000, wire), f.extras.records["v"])
        val none = Fake(null, LyricsHelper.Fetched("words", "SimpMusic"))
        none.store.ensure(song)
        assertEquals(LineExtrasRecord(1_000, null), none.extras.records["v"])
        val nf = Fake(null, LyricsHelper.Fetched(LYRICS_NOT_FOUND, null))
        nf.extras.records["v"] = LineExtrasRecord(1, wire)
        nf.store.ensure(song)
        assertEquals(listOf("v"), nf.extras.deletedIds)
    }

    /** A refetch drops the old extras with the old row (a re-verification changes the keys), then records the fresh ones. */
    @Test
    fun `refetch forgets the extras with the row and records the fresh answer's`() = runBlocking {
        val f = Fake(LyricsEntity("v", "old", "Zemer"), LyricsHelper.Fetched("fresh", "Zemer", wire))
        f.extras.records["v"] = LineExtrasRecord(1, ZemerLyricsClient.LineExtras(keys = listOf("stale000"), en = listOf("old")))
        f.store.refetch(song)
        assertEquals(listOf("v"), f.extras.deletedIds)
        assertEquals(LineExtrasRecord(1_000, wire), f.extras.records["v"])
    }

    /** Extras for a pre-existing row: one resolver call, recorded either way; a "none" record is re-asked only after the TTL. */
    @Test
    fun `ensureResolveExtras resolves once, re-asks a none record after the TTL, never for episodes`() = runBlocking {
        val f = Fake(LyricsEntity("v", "words", "SimpMusic"), LyricsHelper.Fetched("words", "SimpMusic"), resolved = null)
        assertTrue(f.store.ensureResolveExtras(song))
        assertFalse(f.store.ensureResolveExtras(song))
        assertEquals(1, f.resolves)
        f.clock += LineExtrasStore.EMPTY_TTL_MS + 1
        assertTrue(f.store.ensureResolveExtras(song))
        assertEquals(2, f.resolves)
        assertFalse(f.store.ensureResolveExtras(episode))
        val with = Fake(null, LyricsHelper.Fetched("words", "Zemer"), resolved = wire)
        assertTrue(with.store.ensureResolveExtras(song))
        with.clock += LineExtrasStore.EMPTY_TTL_MS * 10
        assertFalse(with.store.ensureResolveExtras(song))
        assertEquals(0, with.fetches)
    }

    /** The aligned ask: once per song + language + displayed body; OFF never asks; transliteration rides the English request. */
    @Test
    fun `ensureExtras asks once per language and body, sends the displayed lines, OFF and episodes never ask`() = runBlocking {
        val lines = listOf("בית הו בית", "שלום עליכם")
        val f = Fake(null, LyricsHelper.Fetched("words", "SimpMusic"), reply = ZemerLyricsClient.ExtrasReply(wire, failed = false))
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.OFF, lines))
        assertTrue(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        assertEquals(Triple("v", lines, "en"), f.asked.single())
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.ROMANIZED, lines)) // same en request already recorded
        assertTrue(f.store.ensureExtras(song, LineExtrasLanguage.YIDDISH, lines))
        assertEquals("yi", f.asked.last().third)
        assertTrue(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines + "a new line")) // a different body re-asks
        assertFalse(f.store.ensureExtras(episode, LineExtrasLanguage.ENGLISH, lines))
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, emptyList()))
        assertEquals(wire, f.extras.records["v"]!!.aligned["en"]!!.extras)
    }

    /** A 404 is a dated negative re-asked after the TTL; a network failure records nothing and retries next time. */
    @Test
    fun `ensureExtras records a 404 as a negative with a TTL and never records a failed ask`() = runBlocking {
        val lines = listOf("a", "b")
        val f = Fake(null, LyricsHelper.Fetched("words", "SimpMusic"), reply = ZemerLyricsClient.ExtrasReply(null, failed = true))
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        assertTrue(f.extras.records.isEmpty())
        f.reply = ZemerLyricsClient.ExtrasReply(null, failed = false)
        assertTrue(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        assertFalse(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        f.clock += LineExtrasStore.EMPTY_TTL_MS + 1
        assertTrue(f.store.ensureExtras(song, LineExtrasLanguage.ENGLISH, lines))
        assertEquals(3, f.asked.size)
    }
}
