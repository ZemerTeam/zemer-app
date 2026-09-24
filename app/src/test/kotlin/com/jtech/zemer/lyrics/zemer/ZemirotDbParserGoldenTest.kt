package com.jtech.zemer.lyrics.zemer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Kotlin port must produce the text the server stored from the same page (`extractHebrew`): one comma-joined line here, by design. */
class ZemirotDbParserGoldenTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("lyrics/$name")!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun `matches the server output on the fixture page`() {
        assertEquals(res("zemirotdb-186.expected.txt").trimEnd(), ZemirotDbParser.parse(res("zemirotdb-186.html")))
    }

    @Test
    fun `br lines kept, a one-block piyut is broken on sentence ends, non-Hebrew lines dropped, thin pages yield nothing`() {
        val lines = (1..6).joinToString("<br>") { "שורה מספר $it" }
        assertEquals((1..6).joinToString("\n") { "שורה מספר $it" }, ZemirotDbParser.parse("<div id='hebrew' class='font'>$lines<br>English only</div>"))
        assertEquals("אחת שתיים שלוש.\nארבע חמש שש:\nשבע שמונה תשע עשר!\nאחת עשרה שתים עשרה", ZemirotDbParser.parse("<div id='hebrew'>אחת שתיים שלוש. ארבע חמש שש: <b>שבע</b> שמונה תשע עשר! אחת&nbsp;עשרה שתים עשרה</div>"))
        assertNull(ZemirotDbParser.parse("<div id='hebrew'>שלוש מילים בלבד</div>"))
        assertNull(ZemirotDbParser.parse("<div id='english'>nothing</div>"))
    }
}
