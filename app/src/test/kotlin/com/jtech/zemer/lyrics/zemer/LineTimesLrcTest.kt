package com.jtech.zemer.lyrics.zemer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The line key must be byte-identical to the server's `lineKey` (vectors computed with the server function). */
class LineTimesLrcTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `line key matches the server vectors`() {
        assertEquals("2567593e", LineTimesLrc.lineKey("אֲדוֹנִי הַמְּשׁוֹרֵר מְלוֹא כָּל הָעִיר כְּבוֹדְךָ"))   // nikud stripped
        assertEquals("3ca8e7f4", LineTimesLrc.lineKey("Hello, World! 123"))                     // case + punctuation
        assertEquals("bfcf837c", LineTimesLrc.lineKey("ס’איז באקאנט דער כלל וואס שטייט אין חז”ל")) // Yiddish, curly quotes
        assertEquals("da39a3ee", LineTimesLrc.lineKey("  "))                                    // empty after normalising
        assertEquals("d8ee42a6", LineTimesLrc.lineKey("İstanbul Ⅻ ½ ٣"))                        // Nl / No / Nd all kept
        assertEquals("b3b7fe79", LineTimesLrc.lineKey("שלום עולם"))
        assertEquals(LineTimesLrc.lineKey("שלום עולם"), LineTimesLrc.lineKey("שלום  עולם."))
    }

    private fun lt(vararg lines: Pair<Double, String>, type: String = "jyrics") =
        ZemerLyricsClient.LineTimes(type, lines.size, lines.map { it.first }, lines.map { LineTimesLrc.lineKey(it.second) })

    @Test
    fun `pairs lines by key in time order and formats LRC`() {
        val times = lt(17.45 to "a b c", 20.751 to "d e f", 61.0 to "g h i", 65.2 to "j k l")
        val lrc = LineTimesLrc.apply("A, b c!\n\nd e f\ng h i\nj k l", times)
        assertEquals("[00:17.45] A, b c!\n[00:20.75] d e f\n[01:01.00] g h i\n[01:05.20] j k l", lrc)
    }

    @Test
    fun `a repeated chorus line takes successive timed occurrences`() {
        val times = lt(1.0 to "chorus", 2.0 to "verse one", 3.0 to "chorus", 4.0 to "verse two", 5.0 to "chorus")
        val lrc = LineTimesLrc.apply("chorus\nverse one\nchorus\nverse two\nchorus", times)
        assertEquals(listOf("[00:01.00] chorus", "[00:02.00] verse one", "[00:03.00] chorus", "[00:04.00] verse two", "[00:05.00] chorus"), lrc!!.lines())
    }

    @Test
    fun `an unmatched line rides the preceding measured tag and too many unmatched lines keep the body plain`() {
        val times = lt(1.0 to "one", 2.0 to "two", 3.0 to "three", 4.0 to "four", 5.0 to "five")
        // one of five parsed lines differs (80 % matched on both sides): synced; the odd line is kept on the previous tag, never given a time of its own
        val odd = times.copy(keys = times.keys.mapIndexed { i, k -> if (i == 3) "deadbeef" else k })
        assertEquals("[00:01.00] one\n[00:02.00] two\n[00:03.00] three\n[00:03.00] FOUR!!\n[00:05.00] five", LineTimesLrc.apply("one\ntwo\nthree\nFOUR!!\nfive", odd))
        // a leading unmatched line rides the first measured tag
        assertEquals("[00:01.00] TITLE\n[00:01.00] one\n[00:02.00] two\n[00:03.00] three\n[00:04.00] four\n[00:05.00] five", LineTimesLrc.apply("TITLE\none\ntwo\nthree\nfour\nfive", times))
        // two of five timed lines never appear in the body: below the share, plain
        assertNull(LineTimesLrc.apply("one\ntwo\nthree", times))
        // the body has many lines the timings do not cover: plain
        assertNull(LineTimesLrc.apply("one\ntwo\nthree\nfour\nfive\nx1\nx2\nx3\nx4\nx5", times))
        // fewer than four timed lines is never sync
        assertNull(LineTimesLrc.apply("one\ntwo\nthree", lt(1.0 to "one", 2.0 to "two", 3.0 to "three")))
        // a keys/times length mismatch is bounded by the shorter list
        assertNull(LineTimesLrc.apply("one", times.copy(times = listOf(1.0))))
    }

    @Test
    fun `live zingmusic record syncs through the parser port under the live lineTimes`() {
        val resolved = ZemerLyricsClient.json.decodeFromString(ZemerLyricsClient.Resolved.serializer(), res("resolve-zingmusic-linetimes.json"))
        val lineTimes = resolved.lineTimes!!
        assertEquals("zingmusic", lineTimes.type)
        assertEquals(60, lineTimes.count)
        val html = ZemerLyricsClient.json.parseToJsonElement(res("zing-1340.json")).let { it.jsonObject["data"]!!.jsonObject["track"]!!.jsonObject["heLyrics"]!!.jsonPrimitive.content }
        val plain = ZingParser.toPlain(html)
        val lrc = LineTimesLrc.apply(plain, lineTimes)
        assertNotNull("the server measured this record's own lines; the port must re-key them", lrc)
        // the live record's text has drifted from what the server timed (57 lines now, 60 then): 50 match by key,
        // which is exactly why lines pair by key and not by count; every line of the body is kept
        val lines = lrc!!.lines()
        assertEquals(plain.lines().count { it.isNotBlank() }, lines.size)
        assertEquals(50, lines.map { it.substring(0, 10) }.distinct().size)
        assertEquals("[00:26.08] " + plain.lines().first { it.isNotBlank() }, lines.first())
    }

    private val kotlinx.serialization.json.JsonElement.jsonObject get() = kotlinx.serialization.json.JsonObject::class.java.cast(this)
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive get() = kotlinx.serialization.json.JsonPrimitive::class.java.cast(this)
}
