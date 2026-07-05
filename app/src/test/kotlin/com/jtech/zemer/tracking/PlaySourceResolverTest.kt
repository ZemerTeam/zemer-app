package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the play-source rules (spec §3.3): user-chosen context items keep their queue's source,
 * autoplay/radio fill reports "radio", anything unregistered (manual queue adds, a restored
 * persisted queue) reports "other". Starting a new queue keeps the old registry ONE generation —
 * the listen the new queue interrupts ends (and resolves) after the new queue registered, so a
 * plain wipe would misattribute every queue-replacement-terminated listen.
 */
class PlaySourceResolverTest {

    @Test
    fun `context items keep the queue source, unregistered ids are other`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.zemer("acapella"), listOf("v1", "v2"))

        assertEquals("zemer:acapella", r.sourceFor("v1"))
        assertEquals("zemer:acapella", r.sourceFor("v2"))
        assertEquals(PlaySource.OTHER, r.sourceFor("manually-queued"))
    }

    @Test
    fun `radio fill reports radio but never demotes a context item`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf("tapped"))
        r.registerRadio(listOf("fill1", "tapped", "fill2"))

        assertEquals(PlaySource.SEARCH, r.sourceFor("tapped"))
        assertEquals(PlaySource.RADIO, r.sourceFor("fill1"))
        assertEquals(PlaySource.RADIO, r.sourceFor("fill2"))
    }

    @Test
    fun `late-loaded context items join the current queue's source`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.album("MPRE1"), emptyList())
        r.registerContext(PlaySource.album("MPRE1"), listOf("t1", "t2"))

        assertEquals("album:MPRE1", r.sourceFor("t1"))
    }

    @Test
    fun `the interrupted listen still resolves its source one generation after a new queue starts`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf("outgoing"))
        // User taps a new queue: the outgoing listen's stats callback fires AFTER this.
        r.onQueueStarted(PlaySource.NEW, listOf("incoming"))

        assertEquals(PlaySource.SEARCH, r.sourceFor("outgoing")) // previous generation still covers it
        assertEquals(PlaySource.NEW, r.sourceFor("incoming"))
    }

    @Test
    fun `two queue starts later the old context is finally forgotten`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf("old"))
        r.onQueueStarted(PlaySource.NEW, listOf("mid"))
        r.onQueueStarted(PlaySource.album("MPRE1"), listOf("new"))

        assertEquals(PlaySource.OTHER, r.sourceFor("old"))
        assertEquals(PlaySource.NEW, r.sourceFor("mid")) // one generation back
        assertEquals("album:MPRE1", r.sourceFor("new"))
    }

    @Test
    fun `current queue registration wins over the previous generation`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf("shared"))
        r.onQueueStarted(PlaySource.NEW, listOf("shared"))

        assertEquals(PlaySource.NEW, r.sourceFor("shared"))
    }

    @Test
    fun `blank ids are never registered`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf(""))
        r.registerRadio(listOf(""))
        assertEquals(PlaySource.OTHER, r.sourceFor(""))
    }
}
