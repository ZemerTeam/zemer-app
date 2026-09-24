package com.jtech.zemer.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the extracted session/visitor-token helpers in ContextExt. */
class ContextExtLogicTest {
    @Test
    fun `isValidVisitorData requires the Cg prefix and length over 20`() {
        assertFalse(isValidVisitorData(null))
        assertFalse(isValidVisitorData(""))
        assertFalse(isValidVisitorData("Cg")) // too short
        assertFalse(isValidVisitorData("Cg" + "x".repeat(18))) // length exactly 20, not > 20
        assertFalse(isValidVisitorData("xy" + "x".repeat(30))) // wrong prefix
        assertTrue(isValidVisitorData("Cg" + "x".repeat(30))) // Cg-prefixed and long enough
    }

    @Test
    fun `cookieHasSession is true only when a SAPISID cookie is present`() {
        assertFalse((null as String?).cookieHasSession())
        assertFalse("".cookieHasSession())
        assertFalse("FOO=bar; BAZ=qux".cookieHasSession())
        assertTrue("SAPISID=abc123; OTHER=1".cookieHasSession())
        assertTrue("OTHER=1; SAPISID=abc123".cookieHasSession()) // order-independent
    }
}
