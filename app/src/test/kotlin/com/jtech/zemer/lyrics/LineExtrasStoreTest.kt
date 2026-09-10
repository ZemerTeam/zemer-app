package com.jtech.zemer.lyrics

import com.jtech.zemer.lyrics.zemer.ZemerLyricsClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** One JSON file per song; a "none" record goes stale after the TTL, a record with extras never does. */
class LineExtrasStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val wire = ZemerLyricsClient.LineExtras(keys = listOf("abcd1234"), en = listOf("A home"), source = "machine")

    @Test
    fun `write then read round-trips the wire shape and its date, delete forgets it`() {
        val store = LineExtrasStore(tmp.newFolder())
        assertNull(store.read("dQw4w9WgXcQ"))
        store.write("dQw4w9WgXcQ", wire, now = 1_000)
        assertEquals(LineExtrasRecord(1_000, wire), store.read("dQw4w9WgXcQ"))
        store.delete("dQw4w9WgXcQ")
        assertNull(store.read("dQw4w9WgXcQ"))
    }

    @Test
    fun `a none record is stale after the TTL, a record with extras is not`() {
        assertFalse(LineExtrasRecord(1_000, null).isStale(1_000 + LineExtrasStore.EMPTY_TTL_MS))
        assertTrue(LineExtrasRecord(1_000, null).isStale(1_001 + LineExtrasStore.EMPTY_TTL_MS))
        assertFalse(LineExtrasRecord(1_000, wire).isStale(Long.MAX_VALUE / 2))
    }

    @Test
    fun `the flow prefers the aligned reply for the displayed body and falls back to the resolve-time extras`() = runBlocking {
        val store = LineExtrasStore(tmp.newFolder())
        val lines = listOf("בית הו בית")
        val resolveWire = ZemerLyricsClient.LineExtras(keys = listOf(com.jtech.zemer.lyrics.zemer.LineTimesLrc.lineKey(lines[0])), en = listOf("from resolve"), source = "machine")
        assertNull(store.flow("dQw4w9WgXcQ", LineExtrasLanguage.ENGLISH, lines).first())
        store.write("dQw4w9WgXcQ", resolveWire)
        assertEquals(listOf("from resolve"), store.flow("dQw4w9WgXcQ", LineExtrasLanguage.ENGLISH, lines).first()!!.forLines(lines, LineExtrasLanguage.ENGLISH))
        store.writeAligned("dQw4w9WgXcQ", "en", LineExtras.linesHash(lines), ZemerLyricsClient.LineExtras(keys = listOf("x"), en = listOf("aligned"), source = "machine"))
        assertEquals(listOf("aligned"), store.flow("dQw4w9WgXcQ", LineExtrasLanguage.ENGLISH, lines).first()!!.forLines(lines, LineExtrasLanguage.ENGLISH))
        // a different displayed body ignores that aligned entry
        assertEquals(listOf(null), store.flow("dQw4w9WgXcQ", LineExtrasLanguage.ENGLISH, listOf("other")).first()?.forLines(listOf("other"), LineExtrasLanguage.ENGLISH) ?: listOf(null))
        // a chain answer replaces the resolve-time extras but keeps the aligned reply
        store.write("dQw4w9WgXcQ", null)
        assertEquals("aligned", store.read("dQw4w9WgXcQ")!!.aligned["en"]!!.extras!!.en!![0])
        assertTrue(store.read("dQw4w9WgXcQ")!!.alignedCurrent("en", LineExtras.linesHash(lines), 0))
        assertFalse(store.read("dQw4w9WgXcQ")!!.alignedCurrent("he", LineExtras.linesHash(lines), 0))
    }

    @Test
    fun `an unsafe id never touches the filesystem and a corrupt file reads as absent`() {
        val dir = tmp.newFolder()
        val store = LineExtrasStore(dir)
        store.write("../escape", wire)
        assertTrue(dir.listFiles().isNullOrEmpty())
        dir.mkdirs()
        java.io.File(dir, "dQw4w9WgXcQ.json").writeText("{not json")
        assertNull(store.read("dQw4w9WgXcQ"))
        assertFalse(java.io.File(dir, "dQw4w9WgXcQ.json").exists())
    }
}
