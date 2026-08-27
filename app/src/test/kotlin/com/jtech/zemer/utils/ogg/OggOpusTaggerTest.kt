package com.jtech.zemer.utils.ogg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class OggOpusTaggerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- minimal synthetic Ogg Opus builder ---

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    /** One Ogg page wrapping a single packet <= 255*255 bytes, with a correct CRC. */
    private fun page(serial: Int, seq: Int, headerType: Int, granule: Long, packet: ByteArray): ByteArray {
        val laces = ArrayList<Int>()
        var rem = packet.size
        while (rem >= 255) { laces.add(255); rem -= 255 }
        laces.add(rem)
        val header = ByteArray(27 + laces.size)
        "OggS".toByteArray(Charsets.ISO_8859_1).copyInto(header)
        header[5] = headerType.toByte()
        for (i in 0 until 8) header[6 + i] = (granule ushr (i * 8)).toByte()
        le32(serial).copyInto(header, 14)
        le32(seq).copyInto(header, 18)
        header[26] = laces.size.toByte()
        for (i in laces.indices) header[27 + i] = laces[i].toByte()
        val full = header + packet
        val crc = oggCrcRef(full)
        le32(crc).copyInto(full, 22)
        return full
    }

    private fun oggCrcRef(page: ByteArray): Int {
        val tbl = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            r
        }
        var crc = 0
        for (b in page) crc = (crc shl 8) xor tbl[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    private fun opusHead(): ByteArray =
        "OpusHead".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(1, 2) + ByteArray(9)

    private fun opusTags(vendor: String, comments: List<String>): ByteArray {
        val out = ArrayList<Byte>()
        out.addAll("OpusTags".toByteArray(Charsets.ISO_8859_1).toList())
        val v = vendor.toByteArray(Charsets.UTF_8)
        out.addAll(le32(v.size).toList()); out.addAll(v.toList())
        out.addAll(le32(comments.size).toList())
        comments.forEach { val c = it.toByteArray(Charsets.UTF_8); out.addAll(le32(c.size).toList()); out.addAll(c.toList()) }
        return out.toByteArray()
    }

    private fun syntheticOpus(comments: List<String> = emptyList(), audioPages: Int = 3): File {
        val f = tmp.newFile("in.ogg")
        val parts = ArrayList<ByteArray>()
        parts.add(page(1, 0, 0x02, 0, opusHead()))              // BOS OpusHead
        parts.add(page(1, 1, 0x00, 0, opusTags("libopus", comments)))
        for (i in 0 until audioPages) {
            val eos = if (i == audioPages - 1) 0x04 else 0x00
            parts.add(page(1, 2 + i, eos, (i + 1) * 960L, ByteArray(200) { (i + it).toByte() }))
        }
        f.writeBytes(parts.reduce { a, b -> a + b })
        return f
    }

    // --- output parsing ---

    private fun pageBoundaries(b: ByteArray): List<Int> {
        val out = ArrayList<Int>()
        var pos = 0
        while (pos + 27 <= b.size) {
            out.add(pos)
            val segc = b[pos + 26].toInt() and 0xFF
            val body = (0 until segc).sumOf { b[pos + 27 + it].toInt() and 0xFF }
            pos += 27 + segc + body
        }
        return out
    }

    private fun allCrcsValid(b: ByteArray): Boolean = pageBoundaries(b).all { start ->
        val segc = b[start + 26].toInt() and 0xFF
        val body = (0 until segc).sumOf { b[start + 27 + it].toInt() and 0xFF }
        val page = b.copyOfRange(start, start + 27 + segc + body)
        val stored = (page[22].toInt() and 0xFF) or ((page[23].toInt() and 0xFF) shl 8) or
            ((page[24].toInt() and 0xFF) shl 16) or ((page[25].toInt() and 0xFF) shl 24)
        for (i in 22..25) page[i] = 0
        oggCrcRef(page) == stored
    }

    private fun seqNumbers(b: ByteArray): List<Int> = pageBoundaries(b).map { start ->
        (b[start + 18].toInt() and 0xFF) or ((b[start + 19].toInt() and 0xFF) shl 8) or
            ((b[start + 20].toInt() and 0xFF) shl 16) or ((b[start + 21].toInt() and 0xFF) shl 24)
    }

    private fun comments(b: ByteArray): List<String> {
        val bounds = pageBoundaries(b)
        val start = bounds[1] // page 1 = OpusTags
        val segc = b[start + 26].toInt() and 0xFF
        val body = b.copyOfRange(start + 27 + segc, start + 27 + segc + (0 until segc).sumOf { b[start + 27 + it].toInt() and 0xFF })
        var p = 8
        fun le() = (body[p].toInt() and 0xFF) or ((body[p + 1].toInt() and 0xFF) shl 8) or
            ((body[p + 2].toInt() and 0xFF) shl 16) or ((body[p + 3].toInt() and 0xFF) shl 24)
        val vlen = le(); p += 4 + vlen
        val n = le(); p += 4
        return (0 until n).map { val l = le(); p += 4; String(body, p, l, Charsets.UTF_8).also { p += l } }
    }

    // --- tests ---

    @Test
    fun `writes tags and keeps every page CRC valid`() {
        val out = tmp.newFile("out.ogg")
        assertTrue(OggOpusTagger.write(syntheticOpus(), out, OggOpusTagger.Tags(title = "T", artist = "A", album = "Al", year = "2026", lyrics = "some words")))
        val b = out.readBytes()
        assertTrue("all page CRCs must recompute", allCrcsValid(b))
        val c = comments(b)
        assertTrue(c.any { it == "TITLE=T" })
        assertTrue(c.any { it == "ARTIST=A" })
        assertTrue(c.any { it == "DATE=2026" })
        assertTrue(c.any { it == "YEAR=2026" })
        assertTrue(c.any { it == "LYRICS=some words" })
    }

    @Test
    fun `page sequence numbers stay contiguous from zero`() {
        val out = tmp.newFile("out.ogg")
        assertTrue(OggOpusTagger.write(syntheticOpus(audioPages = 4), out, OggOpusTagger.Tags(title = "T")))
        val seqs = seqNumbers(out.readBytes())
        assertEquals((0 until seqs.size).toList(), seqs)
    }

    @Test
    fun `replaces existing keys and drops old pictures, keeps unrelated comments`() {
        val input = syntheticOpus(comments = listOf("TITLE=Old", "COMPOSER=Keep", "METADATA_BLOCK_PICTURE=stale"))
        val out = tmp.newFile("out.ogg")
        assertTrue(OggOpusTagger.write(input, out, OggOpusTagger.Tags(title = "New")))
        val c = comments(out.readBytes())
        assertTrue(c.any { it == "TITLE=New" })
        assertFalse(c.any { it == "TITLE=Old" })
        assertTrue(c.any { it == "COMPOSER=Keep" })
        assertFalse(c.any { it.startsWith("METADATA_BLOCK_PICTURE=stale") })
    }

    @Test
    fun `embeds cover as a base64 FLAC picture block`() {
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(40)
        val out = tmp.newFile("out.ogg")
        assertTrue(OggOpusTagger.write(syntheticOpus(), out, OggOpusTagger.Tags(artworkData = jpg)))
        val pic = comments(out.readBytes()).first { it.startsWith("METADATA_BLOCK_PICTURE=") }
        val block = Base64.getDecoder().decode(pic.substringAfter('='))
        // picture type 3 (front cover) in the first 4 bytes, big-endian
        assertEquals(3, (block[3].toInt() and 0xFF))
        assertTrue(String(block, Charsets.ISO_8859_1).contains("image/jpeg"))
    }

    @Test
    fun `empty tags refused`() {
        val out = tmp.newFile("out.ogg")
        assertFalse(OggOpusTagger.write(syntheticOpus(), out, OggOpusTagger.Tags()))
    }

    @Test
    fun `tags a real remuxed opus - CRCs valid, audio pages preserved`() {
        val path = System.getenv("ZEMER_TEST_OGG")
        assumeTrue("ZEMER_TEST_OGG not set", !path.isNullOrBlank())
        val input = File(path!!)
        assumeTrue(input.isFile)
        val jpgPath = System.getenv("ZEMER_TEST_JPG")
        val art = if (!jpgPath.isNullOrBlank() && File(jpgPath).isFile) File(jpgPath).readBytes() else null
        val out = File(input.parentFile, input.nameWithoutExtension + "-tagged.ogg")
        assertTrue(
            OggOpusTagger.write(
                input, out,
                OggOpusTagger.Tags(artworkData = art, title = "כותרת", artist = "Artist", album = "Album", year = "2026"),
            ),
        )
        assertTrue("every page CRC must be valid", allCrcsValid(out.readBytes()))
        println("REALOGG_OUT=${out.absolutePath}")
    }
}
