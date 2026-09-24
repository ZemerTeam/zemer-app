package com.jtech.zemer.lyrics.zemer

import com.jtech.zemer.lyrics.LyricsUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bodies the Zemer provider emits must drive the app's LINE sync (LRC) and WORD sync (enhanced LRC) parsers. */
class SyncIntegrationTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `jkaraoke LRC from the provider parses into timed lines in order`() {
        val synced = Json.parseToJsonElement(res("jkaraoke-1971-golden.json")).jsonObject["synced"]!!.jsonPrimitive.content
        assertTrue("the app's synced detection keys on a leading '['", synced.startsWith("["))
        val lines = LyricsUtils.parseLyrics(synced)
        assertEquals(65, lines.size)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.time <= b.time })
        assertTrue(lines.all { it.words.isEmpty() })            // line sync only, no word tags
        assertTrue(lines.first().time >= 0 && lines.last().time < 10 * 60 * 1000)
        // current-line lookup at a real position lands inside the list
        val idx = LyricsUtils.findCurrentLineIndex(lines, lines[10].time + 50)
        assertEquals(10, idx)
    }

    @Test
    fun `enhanced LRC (word sync) yields per-word timings`() {
        val rich = "[00:18.59] <00:18.59> We're <00:18.81>   <00:18.86> no <00:18.95>   <00:19.01> strangers <00:19.73>\n[00:22.64] <00:22.64>You <00:22.84>know"
        assertTrue(LyricsUtils.isWordSynced(rich))
        val lines = LyricsUtils.parseLyrics(rich)
        assertEquals(listOf("We're no strangers", "You know"), lines.map { it.text })
        assertEquals(listOf(18590L, 18860L, 19010L), lines[0].words.map { it.time })
        assertEquals(2, LyricsUtils.sungWordCount(lines[0].words, 18900L))
    }
}
