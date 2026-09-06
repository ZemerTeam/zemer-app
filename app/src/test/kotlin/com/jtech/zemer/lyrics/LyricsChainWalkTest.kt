package com.jtech.zemer.lyrics

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.jtech.zemer.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** The walk's schedule must never change the answer a sequential user-order walk gives, only how long it takes. */
class LyricsChainWalkTest {
    private fun provider(name: String, low: Boolean = false) = object : LyricsProvider {
        override val name = name
        override val enabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("enable$name")
        override val lowTrust = low
        override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String?): Result<String> = Result.failure(IllegalStateException("unused"))
    }
    private val zemer = provider("Zemer")
    private val simp = provider("SimpMusic")
    private val mxm = provider("Musixmatch")
    private val lrclib = provider("LrcLib")
    private val ytSub = provider("YouTube Subtitle", low = true)
    private val ytTab = provider("YouTube Music", low = true)
    private val plain = "line one\nline two"
    private val synced = "[00:01.00] line one\n[00:05.00] line two"

    private class Fake(private val answers: Map<String, String?>, private val delayMs: Long = 0) {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        val fetch: suspend (LyricsProvider) -> LabeledLyrics? = { p ->
            calls += p.name
            if (delayMs > 0) delay(delayMs)
            answers[p.name]?.let { LabeledLyrics(p.name, it) }
        }
    }

    @Test
    fun `a synced primary answer ends the walk with no other provider asked`() = runBlocking {
        val f = Fake(mapOf("Zemer" to synced, "SimpMusic" to synced))
        assertEquals(LyricsHelper.Fetched(synced, "Zemer"), LyricsChainWalk.run(listOf(zemer, simp, mxm, lrclib, ytSub, ytTab), f.fetch))
        assertEquals(listOf("Zemer"), f.calls)
    }

    @Test
    fun `after a plain primary the other trusted providers run concurrently and the best by priority wins`() = runBlocking {
        val f = Fake(mapOf("Zemer" to plain, "Musixmatch" to synced, "LrcLib" to synced), delayMs = 150)
        val startedAt = System.currentTimeMillis()
        val got = LyricsChainWalk.run(listOf(zemer, simp, mxm, lrclib, ytSub, ytTab), f.fetch)
        val elapsed = System.currentTimeMillis() - startedAt
        assertEquals("priority order, not arrival order", LyricsHelper.Fetched(synced, "Musixmatch"), got)
        assertEquals(setOf("Zemer", "SimpMusic", "Musixmatch", "LrcLib"), f.calls.toSet())
        assertTrue("three 150 ms providers ran concurrently, not serially (took $elapsed ms)", elapsed < 150 * 3)
    }

    @Test
    fun `a plain trusted answer is served without ever asking the low-trust providers`() = runBlocking {
        val f = Fake(mapOf("LrcLib" to plain, "YouTube Subtitle" to synced))
        assertEquals(LyricsHelper.Fetched(plain, "LrcLib"), LyricsChainWalk.run(listOf(zemer, simp, mxm, lrclib, ytSub, ytTab), f.fetch))
        assertTrue(f.calls.none { it.startsWith("YouTube") })
    }

    @Test
    fun `low-trust providers are deferred to the end even when the user order lists them first`() = runBlocking {
        val f = Fake(mapOf("Zemer" to plain, "YouTube Subtitle" to synced))
        val got = LyricsChainWalk.run(listOf(ytSub, ytTab, zemer, simp, mxm, lrclib), f.fetch)
        assertEquals(LyricsHelper.Fetched(plain, "Zemer"), got)
        assertEquals("Zemer", f.calls.first())
        assertTrue(f.calls.none { it.startsWith("YouTube") })
    }

    @Test
    fun `with no trusted answer the low-trust providers run and the first by order is served`() = runBlocking {
        val f = Fake(mapOf("YouTube Music" to plain, "YouTube Subtitle" to synced))
        assertEquals(LyricsHelper.Fetched(synced, "YouTube Subtitle"), LyricsChainWalk.run(listOf(zemer, simp, ytSub, ytTab), f.fetch))
        assertEquals(setOf("Zemer", "SimpMusic", "YouTube Subtitle", "YouTube Music"), f.calls.toSet())
        assertEquals(LyricsHelper.Fetched(LYRICS_NOT_FOUND, null), LyricsChainWalk.run(listOf(zemer, ytSub), Fake(emptyMap()).fetch))
        assertEquals(LyricsHelper.Fetched(LYRICS_NOT_FOUND, null), LyricsChainWalk.run(emptyList(), Fake(emptyMap()).fetch))
    }
}
