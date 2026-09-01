package com.jtech.zemer.playback.sabr

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the explicit stream lifecycle: a destroyed stream must WAKE its readers (never leave one parked
 * in SabrBuffer's wait forever — the infinite-buffering hang), the audio registry owns stream lifetime
 * across DataSource close/reopen churn (seek reopens reuse the live stream instead of re-resolving and
 * re-draining from byte 0), replace/evict/clear always destroy what they displace, and a COMPLETE drain
 * is promoted to the persistent spool replay cache (a later play = zero network) while an incomplete
 * one deletes its spool. Streams are never driven here — sessions POST to the network; lifecycle is
 * testable without them.
 */
class SabrStreamLifecycleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val client = OkHttpClient()

    private fun audioConfig(len: Long = 64, itag: Int = 251) = SabrConfig(
        sabrUrl = "https://example.invalid/sabr",
        ustreamerConfig = ByteArray(2),
        format = SabrMessages.Format(itag = itag, lastModified = 0L, contentLength = len),
        poToken = ByteArray(2),
        clientInfo = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.0"),
        userAgent = "test",
        nTransform = { it },
        mimeType = "audio/webm; codecs=\"opus\"",
        bitrate = 141000,
        streamClientLabel = "TEST (SABR)",
        durationMs = 1000,
    )

    private fun videoConfig() = SabrVideoConfig(
        sabrUrl = "https://example.invalid/sabr",
        ustreamerConfig = ByteArray(2),
        videoFormat = SabrMessages.Format(itag = 136, lastModified = 0L, contentLength = 64),
        audioFormat = SabrMessages.Format(itag = 251, lastModified = 0L, contentLength = 64),
        poToken = ByteArray(2),
        clientInfo = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.0"),
        userAgent = "test",
        nTransform = { it },
    )

    @Before
    fun reset() {
        SabrStreamRegistry.clear()
        SabrVideoRegistry.clear()
        SabrSpool.init(tmp.newFolder("cache-${System.nanoTime()}"))
    }

    private fun assertBufferErrored(buffer: SabrBuffer) {
        try {
            buffer.read(buffer.expectedLength - 1, ByteArray(4), 0, 4)
            fail("expected the destroyed stream's buffer to throw")
        } catch (expected: java.io.IOException) {
        }
    }

    @Test
    fun `destroying an INCOMPLETE audio stream errors its buffer, deletes the spool, and is unusable`() {
        val stream = SabrAudioStream("vid00000001", audioConfig(), client)
        val spool = stream.buffer.file
        assertTrue(stream.usable())
        stream.destroy()
        assertFalse(stream.usable())
        assertBufferErrored(stream.buffer)
        assertFalse("incomplete spool must be deleted", spool.exists())
    }

    @Test
    fun `destroying a COMPLETE audio stream promotes it to the spool replay cache`() {
        val config = audioConfig(len = 8)
        val stream = SabrAudioStream("vid00000002", config, client)
        stream.buffer.writeAt(0, ByteArray(8) { (it + 1).toByte() }, 0, 8)
        stream.buffer.markComplete()
        stream.destroy()
        val entry = SabrSpool.lookup("vid00000002")
        assertNotNull("complete drain must be promoted", entry)
        assertEquals(251, entry!!.itag)
        assertEquals(8L, entry.contentLength)
        // A replay stream over the cached spool serves the bytes with zero network.
        val replay = SabrAudioStream.fromSpool("vid00000002", entry)
        assertTrue(replay.usable())
        assertTrue(replay.fromSpool)
        val out = ByteArray(8)
        assertEquals(8, replay.read(0, out, 0, 8))
        assertEquals(2.toByte(), out[1])
        replay.destroy()
        assertNotNull("destroying a replay stream must not evict the cache entry", SabrSpool.lookup("vid00000002"))
    }

    @Test
    fun `destroying a video stream errors BOTH track buffers and deletes their spools`() {
        // The quality-switch race left readers parked forever: destroy() cancelled the session (whose
        // cancelled exits skip marking) but never woke the buffers' waiters.
        val stream = SabrVideoStream("vid00000003", videoConfig(), client)
        val v = stream.videoBuffer.file
        val a = stream.audioBuffer.file
        stream.destroy()
        assertBufferErrored(stream.videoBuffer)
        assertBufferErrored(stream.audioBuffer)
        assertFalse(v.exists())
        assertFalse(a.exists())
    }

    @Test
    fun `video registry put destroys the replaced stream`() {
        val old = SabrVideoStream("vid00000004", videoConfig(), client)
        val new = SabrVideoStream("vid00000004", videoConfig(), client)
        SabrVideoRegistry.put("vid00000004", old)
        SabrVideoRegistry.put("vid00000004", new)
        assertBufferErrored(old.videoBuffer)
        assertEquals(new, SabrVideoRegistry.get("vid00000004"))
        SabrVideoRegistry.clear()
        assertBufferErrored(new.videoBuffer)
        assertNull(SabrVideoRegistry.get("vid00000004"))
    }

    @Test
    fun `audio registry keeps a live stream across a close-reopen and reuses it`() {
        val config = audioConfig()
        SabrStreamRegistry.put("vid00000005", config)
        val stream = SabrAudioStream("vid00000005", config, client)
        SabrStreamRegistry.installStream("vid00000005", stream)
        // A seek's close→reopen looks the stream up again — same object, same buffer, no re-resolve.
        assertEquals(stream, SabrStreamRegistry.stream("vid00000005"))
        assertTrue(stream.usable())
    }

    @Test
    fun `audio registry replace and remove destroy the displaced stream and drop its config`() {
        val config = audioConfig()
        SabrStreamRegistry.put("vid00000006", config)
        val old = SabrAudioStream("vid00000006", config, client)
        SabrStreamRegistry.installStream("vid00000006", old)
        val new = SabrAudioStream("vid00000006", config, client)
        SabrStreamRegistry.installStream("vid00000006", new)
        assertBufferErrored(old.buffer)
        assertEquals(new, SabrStreamRegistry.stream("vid00000006"))
        SabrStreamRegistry.remove("vid00000006")
        assertBufferErrored(new.buffer)
        assertNull(SabrStreamRegistry.stream("vid00000006"))
        assertNull(SabrStreamRegistry.get("vid00000006"))
    }

    @Test
    fun `audio registry evicts the OLDEST stream past the cap, never the one just installed`() {
        val streams = (1..4).map { i ->
            val config = audioConfig()
            SabrStreamRegistry.put("vid0000001$i", config)
            SabrAudioStream("vid0000001$i", config, client).also { SabrStreamRegistry.installStream("vid0000001$i", it) }
        }
        // Cap is 3: installing the 4th evicts the 1st (spool files + handles must not accumulate per
        // unique track for the whole listening session).
        assertNull(SabrStreamRegistry.stream("vid00000011"))
        assertNull(SabrStreamRegistry.get("vid00000011"))
        assertBufferErrored(streams[0].buffer)
        assertNotNull(SabrStreamRegistry.stream("vid00000012"))
        assertNotNull(SabrStreamRegistry.stream("vid00000013"))
        assertNotNull(SabrStreamRegistry.stream("vid00000014"))
    }

    @Test
    fun `two streams for one id get DISTINCT spool files, so a replacement never unlinks the survivor`() {
        // Same id + itag used to share one part filename: the replaced stream's destroy unlinked the
        // file under the survivor's handle — playback survived (POSIX) but the survivor's completed
        // drain could never promote (rename of an unlinked path), silently starving the replay cache.
        val config = audioConfig(len = 8)
        val old = SabrAudioStream("vid00000030", config, client)
        val fresh = SabrAudioStream("vid00000030", config, client)
        assertTrue(old.buffer.file != fresh.buffer.file)
        SabrStreamRegistry.put("vid00000030", config)
        SabrStreamRegistry.installStream("vid00000030", old)
        SabrStreamRegistry.installStream("vid00000030", fresh) // destroys old, deletes ITS file only
        assertTrue("the survivor's spool must still exist", fresh.buffer.file.exists())
        fresh.buffer.writeAt(0, ByteArray(8), 0, 8)
        fresh.buffer.markComplete()
        SabrStreamRegistry.remove("vid00000030") // destroy -> promote
        assertNotNull("the survivor's complete drain must promote", SabrSpool.lookup("vid00000030"))
    }

    /** A SabrConfig whose SABR url points at [url] (a local test server). */
    private fun audioConfigAt(url: String, len: Long = 64) = SabrConfig(
        sabrUrl = url,
        ustreamerConfig = ByteArray(2),
        format = SabrMessages.Format(itag = 251, lastModified = 0L, contentLength = len),
        poToken = ByteArray(2),
        clientInfo = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.0"),
        userAgent = "test",
        nTransform = { it },
        durationMs = 1000,
    )

    @Test
    fun `a restartable session's failure does NOT poison the shared buffer while a download session's does`() {
        // Finding 1: the shared buffer is reused by the session a seek-restart starts. A dying playback
        // session must leave error decisions to the stream (MAX_SEEK_RESTARTS), or its one-way markError
        // poisons the fresh session's writes forever. A standalone download session, having no stream to
        // retry it, must still mark so its caller sees the failure.
        // A one-shot server that accepts then immediately closes — the SABR POST fails (the session's
        // failure path), no protocol replay needed.
        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val acceptor = Thread { try { while (!server.isClosed) server.accept().close() } catch (_: Exception) {} }
        acceptor.isDaemon = true
        acceptor.start()
        try {
            val url = "http://127.0.0.1:${server.localPort}/sabr"

            // Restartable (playback) session hitting the failure: no buffer poison.
            val playbackBuffer = SabrBuffer(64, tmp.newFile("restartable.spool"))
            SabrSession(audioConfigAt(url), client, playbackBuffer, restartable = true).run()
            assertFalse("a restartable session must not mark the shared buffer errored", playbackBuffer.failed())

            // Standalone (download) session hitting the same failure: buffer marked so the caller sees it.
            val downloadBuffer = SabrBuffer(64, tmp.newFile("download.spool"))
            SabrSession(audioConfigAt(url), client, downloadBuffer, restartable = false).run()
            assertTrue("a download session must mark its buffer errored", downloadBuffer.failed())
        } finally {
            server.close()
        }
    }

    @Test
    fun `spool lookup validates the file length and evict removes the entry`() {
        val config = audioConfig(len = 8)
        val stream = SabrAudioStream("vid00000020", config, client)
        stream.buffer.writeAt(0, ByteArray(8), 0, 8)
        stream.buffer.markComplete()
        stream.destroy()
        assertNotNull(SabrSpool.lookup("vid00000020"))
        SabrSpool.evict("vid00000020")
        assertNull("evicted entry must not resolve", SabrSpool.lookup("vid00000020"))
    }
}
