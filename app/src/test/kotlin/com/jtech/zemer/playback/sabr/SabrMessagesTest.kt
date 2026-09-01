package com.jtech.zemer.playback.sabr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the SABR request builder shape and the response part parsers against hand-built protobuf. */
class SabrMessagesTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private val fmt = SabrMessages.Format(itag = 251, lastModified = 1714585730077579L, contentLength = 5878798L)
    private val client = SabrMessages.ClientInfo(clientName = 101, clientVersion = "1.02", osName = "visionOS")

    @Test
    fun `cold-start request has clientAbrState, ustreamer, preferredAudioFormat, streamerContext, no bufferedRange`() {
        val req = SabrMessages.abrRequest(
            ustreamerConfig = bytes(0xAB, 0xCD), format = fmt, poToken = bytes(1, 2, 3), clientInfo = client,
            playerTimeMs = 0, range = null, cookie = null, sabrContexts = emptyList(), selected = false,
        )
        val m = SabrProto.read(req)
        assertTrue("clientAbrState (1)", m.containsKey(1))
        assertArrayEquals("ustreamer config (5)", bytes(0xAB, 0xCD), m.bytesAt(5))
        assertTrue("preferredAudioFormatId (16)", m.containsKey(16))
        assertTrue("streamerContext (19)", m.containsKey(19))
        assertTrue("no bufferedRange on cold start", !m.containsKey(3))
        assertTrue("no selectedFormatId on cold start", !m.containsKey(2))
        // clientAbrState carries playerTimeMs(28)=0 and enabledTrackTypes(40)=1 (audio only)
        val abr = SabrProto.read(m.bytesAt(1)!!)
        assertEquals(0L, abr.longAt(28))
        assertEquals(1L, abr.longAt(40))
    }

    @Test
    fun `follow-up request carries bufferedRange, selectedFormat, playerTime, cookie, contexts`() {
        val ctx = SabrMessages.sabrContext(2, bytes(7))
        val req = SabrMessages.abrRequest(
            ustreamerConfig = bytes(0), format = fmt, poToken = bytes(9), clientInfo = client,
            playerTimeMs = 60000, range = SabrMessages.TrackState(fmt, 60000, 6), cookie = bytes(0xC0), sabrContexts = listOf(ctx), selected = true,
        )
        val m = SabrProto.read(req)
        assertTrue("selectedFormatId (2)", m.containsKey(2))
        assertTrue("bufferedRange (3)", m.containsKey(3))
        assertEquals("playerTimeMs (4)", 60000L, m.longAt(4))
        val streamer = SabrProto.read(m.bytesAt(19)!!)
        assertArrayEquals("poToken (2)", bytes(9), streamer.bytesAt(2))
        assertArrayEquals("playbackCookie (3)", bytes(0xC0), streamer.bytesAt(3))
        assertTrue("sabr_contexts (5)", streamer.containsKey(5))
        // bufferedRange: startTimeMs=0, durationMs=60000, startSeg=1, endSeg=6
        val br = SabrProto.read(m.bytesAt(3)!!)
        assertEquals(0L, br.longAt(2))
        assertEquals(60000L, br.longAt(3))
        assertEquals(1L, br.longAt(4))
        assertEquals(6L, br.longAt(5))
    }

    @Test
    fun `a SEEKED session's bufferedRange anchors at its own first segment, never (0, 1)`() {
        // Proven live (tests/sabr-seek.mjs): a seek-restarted session echoes ranges starting at its
        // FIRST received segment; a hardcoded (0, 1) would claim bytes it never got.
        val req = SabrMessages.abrRequest(
            ustreamerConfig = bytes(0), format = fmt, poToken = bytes(9), clientInfo = client,
            playerTimeMs = 200000, range = SabrMessages.TrackState(fmt, 210000, 21, startTimeMs = 190001, startSeg = 20),
            cookie = null, sabrContexts = emptyList(), selected = true,
        )
        val br = SabrProto.read(SabrProto.read(req).bytesAt(3)!!)
        assertEquals(190001L, br.longAt(2))          // startTimeMs = first segment's start
        assertEquals(210000L - 190001L, br.longAt(3)) // durationMs = buffered end - start
        assertEquals(20L, br.longAt(4))              // startSegmentIndex = first segment
        assertEquals(21L, br.longAt(5))
    }

    @Test
    fun `parseMediaHeader reads seq, init, ranges, contentLength, timeRange`() {
        // MediaHeader: header_id=1 -> 0, itag=3 -> 251, start_range=6 -> 834, is_init=8 -> 0,
        // sequence_number=9 -> 1, content_length=14 -> 213198, time_range=15 { start_ticks=1:0, dur=2:10000, timescale=3:1000 }
        val timeRange = SabrProto.concat(SabrProto.vField(1, 0), SabrProto.vField(2, 10000), SabrProto.vField(3, 1000))
        val payload = SabrProto.concat(
            SabrProto.vField(1, 0), SabrProto.vField(3, 251), SabrProto.vField(6, 834),
            SabrProto.vField(9, 1), SabrProto.vField(14, 213198), SabrProto.bField(15, timeRange),
        )
        val h = SabrMessages.parseMediaHeader(payload)
        assertEquals(1, h.seq)
        assertEquals(251, h.itag) // routes a MEDIA to its track (video vs audio) in a dual-track session
        assertTrue(!h.isInit)
        assertEquals(834L, h.startRange)
        assertEquals(213198L, h.contentLength)
        assertEquals(0L, h.startMs)
        assertEquals(10000L, h.durMs) // 10000 ticks / 1000 timescale * 1000 = 10000 ms
    }

    @Test
    fun `parseContextUpdate builds an echoable SabrContext keyed by type`() {
        // SabrContextUpdate { type=1 -> 2, value=3 -> [0xAB] }
        val update = SabrProto.concat(SabrProto.vField(1, 2), SabrProto.bField(3, bytes(0xAB)))
        val (type, ctx) = SabrMessages.parseContextUpdate(update)
        assertEquals(2L, type)
        // ctx is a SabrContext { type=1 -> 2, value=2 -> [0xAB] }
        val m = SabrProto.read(ctx)
        assertEquals(2L, m.longAt(1))
        assertArrayEquals(bytes(0xAB), m.bytesAt(2))
    }

    @Test
    fun `dual-track video request pins both formats, bitfield 0, per-track ranges`() {
        val video = SabrMessages.Format(itag = 136, lastModified = 111L, contentLength = 26455880L)
        val audio = SabrMessages.Format(itag = 251, lastModified = 222L, contentLength = 3433755L)
        val req = SabrMessages.abrRequestVideo(
            ustreamerConfig = bytes(0xAB), videoFormat = video, audioFormat = audio,
            video = SabrMessages.TrackState(video, bufferedEndMs = 30000, bufferedEndSeg = 10),
            audio = SabrMessages.TrackState(audio, bufferedEndMs = 28000, bufferedEndSeg = 9),
            poToken = bytes(1), clientInfo = client, playerTimeMs = 28000, cookie = null, sabrContexts = emptyList(), selected = true,
        )
        val m = SabrProto.read(req)
        // enabledTrackTypesBitfield = 0 (video + audio), not 1 (audio only)
        assertEquals(0L, SabrProto.read(m.bytesAt(1)!!).longAt(40))
        // preferredAudioFormatId (16) = audio itag; preferredVideoFormatId (17) = video itag
        assertEquals(251L, SabrProto.read(m.bytesAt(16)!!).longAt(1))
        assertEquals(136L, SabrProto.read(m.bytesAt(17)!!).longAt(1))
        // both tracks locked (field 2 repeated) and both buffered ranges present (field 3 repeated)
        assertEquals(2, m[2]?.size)
        assertEquals(2, m[3]?.size)
    }

    @Test
    fun `dual-track cold start sends no ranges and no locked formats`() {
        val video = SabrMessages.Format(itag = 136, lastModified = 111L, contentLength = 1L)
        val audio = SabrMessages.Format(itag = 251, lastModified = 222L, contentLength = 1L)
        val req = SabrMessages.abrRequestVideo(
            ustreamerConfig = bytes(0), videoFormat = video, audioFormat = audio, video = null, audio = null,
            poToken = bytes(1), clientInfo = client, playerTimeMs = 0, cookie = null, sabrContexts = emptyList(), selected = false,
        )
        val m = SabrProto.read(req)
        assertTrue("no selected formats cold", !m.containsKey(2))
        assertTrue("no buffered ranges cold", !m.containsKey(3))
        assertTrue("still pins preferred video (17)", m.containsKey(17))
    }

    @Test
    fun `mediaHeaderId strips the header-id prefix from a MEDIA part`() {
        val media = SabrProto.varint(1) + bytes(0xDE, 0xAD, 0xBE)
        val (id, prefix) = SabrMessages.mediaHeaderId(media)
        assertEquals(1, id)
        assertEquals(1, prefix)
    }

    @Test
    fun `mediaHeaderId reads the UMP varint, not the protobuf one, for ids past 127`() {
        // The MEDIA header-id prefix is a UMP leading-bits varint (harness umpVar), which agrees with
        // the protobuf LEB128 only below 128. 0x82 0x02 is UMP for 130 ((0x82 & 0x3f) + 0x02*64) but
        // LEB128 for 258 — the old protobuf parse mis-identified every id past 127 (dropped segments)
        // and could mis-size the prefix (shifted media write -> container corruption).
        val (id, prefix) = SabrMessages.mediaHeaderId(bytes(0x82, 0x02, 0xDE, 0xAD))
        assertEquals(130, id)
        assertEquals(2, prefix)
    }

    @Test
    fun `mediaHeaderId consumes the UMP prefix WIDTH, which LEB128 disagrees on`() {
        // 0xC0 opens a 3-byte UMP varint ((0xC0 & 0x1f) + 0x01*32 + 0x01*8192 = 8224); LEB128 would stop
        // after 2 bytes and shift the media payload by one — silent container corruption.
        val (id, prefix) = SabrMessages.mediaHeaderId(bytes(0xC0, 0x01, 0x01, 0xFF))
        assertEquals(8224, id)
        assertEquals(3, prefix)
    }
}
