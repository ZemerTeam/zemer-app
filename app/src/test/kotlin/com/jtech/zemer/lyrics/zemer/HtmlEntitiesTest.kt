package com.jtech.zemer.lyrics.zemer

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one entity unescape both page parsers share (it was duplicated per parser). */
class HtmlEntitiesTest {
    @Test
    fun `named and numeric entities`() {
        assertEquals("Don't \u2013 \"quote\" & \u05e9 x", HtmlEntities.unescape("Don&#8217;t &ndash; &quot;quote&quot; &amp;&nbsp;&#1513; x"))
        assertEquals("it's", HtmlEntities.unescape("it&#039;s"))
        assertEquals("plain", HtmlEntities.unescape("plain"))
    }
}
