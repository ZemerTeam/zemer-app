package com.jtech.zemer.playback.sabr

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Pins the explicit stream lifecycle: a destroyed stream must WAKE its readers (never leave one parked
 * in SabrBuffer.read forever — the infinite-buffering hang), the audio registry owns stream lifetime
 * across DataSource close/reopen churn (seek reopens reuse the live stream instead of re-resolving and
 * re-draining from byte 0), and replace/evict/clear always destroy what they displace.
 * Streams are never attach()ed here — attach starts a network drain; lifecycle is testable without it.
 */
class SabrStreamLifecycleTest {

    private val client = OkHttpClient()

    private fun audioConfig(len: Long = 64) = SabrConfig(
        sabrUrl = "https://example.invalid/sabr",
        ustreamerConfig = ByteArray(2),
        format = SabrMessages.Format(itag = 251, lastModified = 0L, contentLength = len),
        poToken = ByteArray(2),
        clientInfo = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.0"),
        userAgent = "test",
        nTransform = { it },
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
    }

    private fun assertBufferErrored(buffer: SabrBuffer) {
        try {
            buffer.read(0, ByteArray(4), 0, 4)
            fail("expected the destroyed stream's buffer to throw")
        } catch (expected: java.io.IOException) {
        }
    }

    @Test
    fun `destroying an audio stream errors its buffer and makes it unusable`() {
        val stream = SabrAudioStream("id", audioConfig(), client)
        assertTrue(stream.usable())
        stream.destroy()
        assertFalse(stream.usable())
        assertBufferErrored(stream.buffer)
    }

    @Test
    fun `destroying a video stream errors BOTH track buffers`() {
        // The quality-switch race left readers parked forever: destroy() cancelled the session (whose
        // cancelled exits skip marking) but never woke the buffers' waiters.
        val stream = SabrVideoStream(videoConfig(), client)
        stream.destroy()
        assertBufferErrored(stream.videoBuffer)
        assertBufferErrored(stream.audioBuffer)
    }

    @Test
    fun `video registry put destroys the replaced stream`() {
        val old = SabrVideoStream(videoConfig(), client)
        val new = SabrVideoStream(videoConfig(), client)
        SabrVideoRegistry.put("id", old)
        SabrVideoRegistry.put("id", new)
        assertBufferErrored(old.videoBuffer)
        assertEquals(new, SabrVideoRegistry.get("id"))
        SabrVideoRegistry.clear()
        assertBufferErrored(new.videoBuffer)
        assertNull(SabrVideoRegistry.get("id"))
    }

    @Test
    fun `audio registry keeps a live stream across a close-reopen and reuses it`() {
        val config = audioConfig()
        SabrStreamRegistry.put("id", config)
        val stream = SabrAudioStream("id", config, client)
        SabrStreamRegistry.installStream("id", stream)
        // A seek's close→reopen looks the stream up again — same object, same buffer, no re-resolve.
        assertEquals(stream, SabrStreamRegistry.stream("id"))
        assertTrue(stream.usable())
    }

    @Test
    fun `audio registry replace and remove destroy the displaced stream and drop its config`() {
        val config = audioConfig()
        SabrStreamRegistry.put("id", config)
        val old = SabrAudioStream("id", config, client)
        SabrStreamRegistry.installStream("id", old)
        val new = SabrAudioStream("id", config, client)
        SabrStreamRegistry.installStream("id", new)
        assertBufferErrored(old.buffer)
        assertEquals(new, SabrStreamRegistry.stream("id"))
        SabrStreamRegistry.remove("id")
        assertBufferErrored(new.buffer)
        assertNull(SabrStreamRegistry.stream("id"))
        assertNull(SabrStreamRegistry.get("id"))
    }

    @Test
    fun `audio registry evicts the OLDEST stream past the cap, never the one just installed`() {
        val streams = (1..4).map { i ->
            val config = audioConfig()
            SabrStreamRegistry.put("id$i", config)
            SabrAudioStream("id$i", config, client).also { SabrStreamRegistry.installStream("id$i", it) }
        }
        // Cap is 3: installing the 4th evicts the 1st (whole-track buffers must not accumulate per
        // unique track for the whole listening session).
        assertNull(SabrStreamRegistry.stream("id1"))
        assertNull(SabrStreamRegistry.get("id1"))
        assertBufferErrored(streams[0].buffer)
        assertNotNull(SabrStreamRegistry.stream("id2"))
        assertNotNull(SabrStreamRegistry.stream("id3"))
        assertNotNull(SabrStreamRegistry.stream("id4"))
    }
}
