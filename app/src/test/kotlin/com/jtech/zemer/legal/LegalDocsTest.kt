package com.jtech.zemer.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalDocsTest {
    private fun doc(version: Int, title: String = "T") = LegalDoc(version, "2026-11-02", title, "body")
    private fun docs(privacy: Int, terms: Int) = LegalDocs(doc(privacy, "Privacy Policy"), doc(terms, "Terms of Service"))

    @Test
    fun `the built-in copy is valid, so the app always has text to show`() {
        val root = File(System.getProperty("user.dir")).let { if (File(it, "src/main/res").isDirectory) it else File(it, "app") }
        val builtIn = LegalDocs.parse(File(root, "src/main/res/raw/legal_builtin.json").readText())
        assertNotNull(builtIn)
        assertEquals("Privacy Policy", builtIn!!.privacy.title)
        assertEquals("Terms of Service", builtIn.terms.title)
    }

    @Test
    fun `an unusable answer is rejected, never applied`() {
        assertNull(LegalDocs.parse("not json"))
        assertNull(LegalDocs.parse("""{"privacy":{"version":1,"title":"P","body":"b"}}"""))
        assertNull(LegalDocs.parse("""{"privacy":{"version":0,"title":"P","body":"b"},"terms":{"version":1,"title":"T","body":"b"}}"""))
        assertNull(LegalDocs.parse("""{"privacy":{"version":1,"title":"P","body":" "},"terms":{"version":1,"title":"T","body":"b"}}"""))
        assertNotNull(LegalDocs.parse("""{"privacy":{"version":1,"title":"P","body":"b","extra":1},"terms":{"version":1,"title":"T","body":"b"}}"""))
    }

    @Test
    fun `only documents newer than the agreed ones are owed, and never during onboarding`() {
        val current = docs(privacy = 2, terms = 1)
        assertEquals(listOf("Privacy Policy", "Terms of Service"),
            LegalAgreement.pending(LegalAgreement.Agreed(true, 0, 0), current).map { it.title })
        assertEquals(listOf("Privacy Policy"), LegalAgreement.pending(LegalAgreement.Agreed(true, 1, 1), current).map { it.title })
        assertTrue(LegalAgreement.pending(LegalAgreement.Agreed(true, 2, 1), current).isEmpty())
        assertTrue(LegalAgreement.pending(LegalAgreement.Agreed(false, 0, 0), current).isEmpty())
    }

    @Test
    fun `the first read of a run always goes, foreground re-checks at most every 30 minutes`() {
        val now = 1_793_000_000_000L
        assertTrue(LegalRepository.fetchDue(null, now))
        assertFalse(LegalRepository.fetchDue(now - 29 * 60_000, now))
        assertTrue(LegalRepository.fetchDue(now - 30 * 60_000, now))
    }

    @Test
    fun `a copy is never traded for an older one`() {
        assertTrue(LegalRepository.newerOrSame(docs(2, 1), docs(1, 1)))
        assertTrue(LegalRepository.newerOrSame(docs(1, 1), docs(1, 1)))
        assertFalse(LegalRepository.newerOrSame(docs(1, 2), docs(2, 1)))
    }
}
