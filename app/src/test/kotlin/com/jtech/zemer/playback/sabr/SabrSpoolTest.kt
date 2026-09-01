package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the replay-cache spool bookkeeping: a promote writes a `<id>.<itag>.done` + `<id>.meta`, a
 * lookup validates the file length, and — the review finding — a RE-drain at a different itag evicts the
 * stale sibling `.done` so its later prune can never delete this id's live meta and strand the newer file.
 */
class SabrSpoolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var spoolDir: File

    @Before
    fun setup() {
        val cache = tmp.newFolder("cache-${System.nanoTime()}")
        SabrSpool.init(cache)
        spoolDir = File(cache, "sabr-spool")
    }

    private fun cfg(itag: Int, len: Long) = SabrConfig(
        sabrUrl = "https://example.invalid/sabr",
        ustreamerConfig = ByteArray(2),
        format = SabrMessages.Format(itag = itag, lastModified = 0L, contentLength = len),
        poToken = ByteArray(2),
        clientInfo = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.0"),
        userAgent = "test",
        nTransform = { it },
        mimeType = "audio/webm; codecs=\"opus\"",
        bitrate = 141_000,
        durationMs = 1_000,
    )

    private fun promote(id: String, itag: Int, len: Long) {
        val part = SabrSpool.downloadPart(id, "t").apply { writeBytes(ByteArray(len.toInt())) }
        SabrSpool.promote(id, cfg(itag, len), part)
    }

    @Test
    fun `promote then lookup round-trips the entry`() {
        promote("vidAAAAAAAA1", 251, 8)
        val e = SabrSpool.lookup("vidAAAAAAAA1")
        assertNotNull(e)
        assertEquals(251, e!!.itag)
        assertEquals(8L, e.contentLength)
        assertTrue(File(spoolDir, "vidAAAAAAAA1.251.done").exists())
    }

    @Test
    fun `re-draining an id at a different itag evicts the stale sibling and keeps only the live entry`() {
        promote("vidAAAAAAAA1", 251, 8)
        assertTrue(File(spoolDir, "vidAAAAAAAA1.251.done").exists())

        // A quality-pref change re-drains the same id at itag 250: the meta now points at 250, so the
        // 251 file is stale. Before the fix it lingered, and pruning it later deleted this id's live meta.
        promote("vidAAAAAAAA1", 250, 8)

        assertFalse("the stale 251 .done must be evicted on re-promote", File(spoolDir, "vidAAAAAAAA1.251.done").exists())
        assertTrue(File(spoolDir, "vidAAAAAAAA1.250.done").exists())
        val e = SabrSpool.lookup("vidAAAAAAAA1")
        assertNotNull(e)
        assertEquals(250, e!!.itag)
    }

    @Test
    fun `lookup rejects a meta whose done file length no longer matches`() {
        promote("vidAAAAAAAA2", 140, 16)
        // Corrupt the .done length: the meta says 16, the file is now 8 -> lookup must reject.
        File(spoolDir, "vidAAAAAAAA2.140.done").writeBytes(ByteArray(8))
        assertNull(SabrSpool.lookup("vidAAAAAAAA2"))
    }

    @Test
    fun `evict drops both the done and the meta`() {
        promote("vidAAAAAAAA3", 251, 8)
        SabrSpool.evict("vidAAAAAAAA3")
        assertNull(SabrSpool.lookup("vidAAAAAAAA3"))
        assertFalse(File(spoolDir, "vidAAAAAAAA3.251.done").exists())
        assertFalse(File(spoolDir, "vidAAAAAAAA3.meta").exists())
    }
}
