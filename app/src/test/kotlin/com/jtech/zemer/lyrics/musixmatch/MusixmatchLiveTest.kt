package com.jtech.zemer.lyrics.musixmatch

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs the real on-device request path against Musixmatch with MXM_TOKEN from the environment; skipped otherwise. */
class MusixmatchLiveTest {
    @Test
    fun `live lookup of known hits`() = runBlocking {
        val token = System.getenv("MXM_TOKEN"); assumeTrue(!token.isNullOrBlank())
        for ((t, a, d) in listOf(Triple("Candles on the Sill", "Maccabeats", 250), Triple("פרדס חנה", "Hanan Ben Ari", 211), Triple("התעוררי", "Chaim Israel", 213))) {
            val out = MusixmatchLyrics.fetch(token!!, t, a, d)
            println("LIVE $a / $t -> ${out.status}" + ((out as? MusixmatchLyrics.Outcome.Hit)?.judged?.let { " :: " + it.plain.lines().first() } ?: ""))
            Thread.sleep(3000)
        }
    }
}
