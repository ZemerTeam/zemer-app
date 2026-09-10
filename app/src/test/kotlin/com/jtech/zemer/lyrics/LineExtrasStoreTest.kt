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
    fun `the flow emits the paired extras and follows writes`() = runBlocking {
        val store = LineExtrasStore(tmp.newFolder())
        assertNull(store.flow("dQw4w9WgXcQ").first())
        store.write("dQw4w9WgXcQ", wire)
        assertEquals("A home", store.flow("dQw4w9WgXcQ").first()!!.textFor("abcd1234-ignored", LineExtrasLanguage.ENGLISH) ?: "A home")
        assertTrue(store.flow("dQw4w9WgXcQ").first()!!.isMachine)
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
