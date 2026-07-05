package com.jtech.zemer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the play-source rules (spec §3.3): user-chosen context items keep their queue's source,
 * autoplay/radio fill reports "radio", anything unregistered (manual queue adds, a restored
 * persisted queue) reports "other", and a new queue forgets the old context entirely.
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
    fun `a new queue forgets the previous context`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf("old"))
        r.onQueueStarted(PlaySource.NEW, listOf("new"))

        assertEquals(PlaySource.OTHER, r.sourceFor("old"))
        assertEquals(PlaySource.NEW, r.sourceFor("new"))
    }

    @Test
    fun `blank ids are never registered`() {
        val r = PlaySourceResolver()
        r.onQueueStarted(PlaySource.SEARCH, listOf(""))
        r.registerRadio(listOf(""))
        assertEquals(PlaySource.OTHER, r.sourceFor(""))
    }
}
