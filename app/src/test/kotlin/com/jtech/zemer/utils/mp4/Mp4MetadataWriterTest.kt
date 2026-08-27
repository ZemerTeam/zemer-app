package com.jtech.zemer.utils.mp4

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer

class Mp4MetadataWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // --- synthetic mp4 building ---

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val size = 8 + parts.sumOf { it.size }
        return ByteBuffer.allocate(size).apply {
            putInt(size); put(type.toByteArray(Charsets.ISO_8859_1))
            parts.forEach { put(it) }
        }.array()
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun u32(v: Long) = ByteBuffer.allocate(4).putInt(v.toInt()).array()

    private fun stco(vararg offsets: Long) = box(
        "stco",
        u32(0), // version/flags
        u32(offsets.size.toLong()),
        *offsets.map { u32(it) }.toTypedArray(),
    )

    private fun co64(vararg offsets: Long) = box(
        "co64",
        u32(0),
        u32(offsets.size.toLong()),
        *offsets.map { ByteBuffer.allocate(8).putLong(it).array() }.toTypedArray(),
    )

    private fun moovWith(offsetTable: ByteArray) = box(
        "moov",
        box("mvhd", ByteArray(20)),
        box("trak", box("mdia", box("minf", box("stbl", offsetTable)))),
    )

    /** Assemble a file from boxes; returns (file, mdat payload). */
    private fun fileOf(vararg boxes: ByteArray): File {
        val f = tmp.newFile()
        f.writeBytes(boxes.reduce { a, b -> a + b })
        return f
    }

    private val ftyp = box("ftyp", "M4A ".toByteArray(), u32(0))
    private val mdatPayload = bytes(1, 2, 3, 4, 5, 6, 7, 8)
    private fun mdat() = box("mdat", mdatPayload)

    // --- output parsing helpers ---

    private fun topBoxes(f: File): List<Triple<String, Long, Long>> {
        val data = f.readBytes()
        val out = mutableListOf<Triple<String, Long, Long>>()
        var pos = 0
        while (pos + 8 <= data.size) {
            val size = ByteBuffer.wrap(data, pos, 4).int.toLong()
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            out.add(Triple(type, pos.toLong(), size))
            pos += size.toInt()
        }
        return out
    }

    private fun find(f: File, path: List<String>): Pair<Long, Long>? {
        val data = f.readBytes()
        var from = 0L
        var to = data.size.toLong()
        for ((depth, want) in path.withIndex()) {
            var pos = from
            var found = false
            while (pos + 8 <= to) {
                val size = ByteBuffer.wrap(data, pos.toInt(), 4).int.toLong()
                val type = String(data, pos.toInt() + 4, 4, Charsets.ISO_8859_1)
                if (type == want) {
                    // meta is a FullBox - skip its 4 version/flags bytes when descending.
                    val headerExtra = if (want == "meta" && depth < path.size - 1) 4 else 0
                    from = pos + 8 + headerExtra
                    to = pos + size
                    found = true
                    break
                }
                pos += size
            }
            if (!found) return null
            if (depth == path.size - 1) return (from - 8 - (if (want == "meta") 0 else 0)) to (to - from + 8)
        }
        return null
    }

    private fun stcoEntries(f: File): List<Long> {
        val data = f.readBytes()
        val idx = indexOfBox(data, "stco") ?: indexOfBox(data, "co64")!!
        val type = String(data, idx + 4, 4, Charsets.ISO_8859_1)
        val count = ByteBuffer.wrap(data, idx + 12, 4).int
        return (0 until count).map { i ->
            if (type == "stco") ByteBuffer.wrap(data, idx + 16 + i * 4, 4).int.toLong()
            else ByteBuffer.wrap(data, idx + 16 + i * 8, 8).long
        }
    }

    private fun indexOfBox(data: ByteArray, type: String): Int? {
        val needle = type.toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..data.size - 8) {
            for (j in 0 until 4) if (data[i + 4 + j] != needle[j]) continue@outer
            return i
        }
        return null
    }

    private fun containsAtom(f: File, name: String): Boolean {
        val marker = name.toByteArray(Charsets.ISO_8859_1)
        val data = f.readBytes()
        outer@ for (i in 0..data.size - marker.size) {
            for (j in marker.indices) if (data[i + j] != marker[j]) continue@outer
            return true
        }
        return false
    }

    // --- tests ---

    @Test
    fun `moov before mdat - offsets patched by the moov growth`() {
        val moov = moovWith(stco(120, 200)) // both point past moov end (real files always do)
        val input = fileOf(ftyp, moov, mdat())
        val out = tmp.newFile()
        assertTrue(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags(title = "T", artist = "A", lyrics = "line one\nline two")))

        val boxes = topBoxes(out)
        assertEquals(listOf("ftyp", "moov", "mdat"), boxes.map { it.first })
        val delta = boxes[1].third - moov.size
        assertTrue(delta > 0)
        assertEquals(listOf(120 + delta, 200 + delta), stcoEntries(out))
        // mdat payload byte-identical
        val mdatOff = boxes[2].second.toInt()
        assertArrayEquals(mdatPayload, out.readBytes().copyOfRange(mdatOff + 8, mdatOff + 8 + mdatPayload.size))
        assertTrue(containsAtom(out, "©nam"))
        assertTrue(containsAtom(out, "©ART"))
        assertTrue(containsAtom(out, "©lyr"))
    }

    @Test
    fun `mdat before moov - offsets untouched`() {
        val input = fileOf(ftyp, mdat(), moovWith(stco(16, 20)))
        val out = tmp.newFile()
        assertTrue(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags(title = "T")))
        assertEquals(listOf(16L, 20L), stcoEntries(out))
    }

    @Test
    fun `co64 offsets patch too`() {
        val moov = moovWith(co64(1000, 2000))
        val input = fileOf(ftyp, moov, mdat())
        val out = tmp.newFile()
        assertTrue(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags(album = "Al")))
        val delta = topBoxes(out)[1].third - moov.size
        assertEquals(listOf(1000 + delta, 2000 + delta), stcoEntries(out))
    }

    @Test
    fun `existing udta is replaced, not duplicated`() {
        val input = fileOf(ftyp, moovWith(stco(50)), mdat())
        val mid = tmp.newFile()
        val out = tmp.newFile()
        assertTrue(Mp4MetadataWriter.write(input, mid, Mp4MetadataWriter.Tags(title = "First")))
        assertTrue(Mp4MetadataWriter.write(mid, out, Mp4MetadataWriter.Tags(title = "Second")))
        val data = out.readBytes()
        var count = 0
        val needle = "udta".toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..data.size - 4) {
            for (j in 0 until 4) if (data[i + j] != needle[j]) continue@outer
            count++
        }
        assertEquals(1, count)
        assertFalse(String(data, Charsets.ISO_8859_1).contains("First"))
        assertTrue(String(data, Charsets.ISO_8859_1).contains("Second"))
    }

    @Test
    fun `fragmented input is refused`() {
        val input = fileOf(ftyp, moovWith(stco(50)), box("moof", ByteArray(8)), mdat())
        val out = tmp.newFile()
        assertFalse(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags(title = "T")))
        assertTrue(Mp4MetadataWriter.isFragmented(input))
    }

    @Test
    fun `cover art type flag follows the image magic`() {
        val png = bytes(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16)
        val jpg = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16)
        for ((image, expectedFlag) in listOf(png to 14, jpg to 13)) {
            val input = fileOf(ftyp, moovWith(stco(50)), mdat())
            val out = tmp.newFile()
            assertTrue(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags(artworkData = image)))
            val data = out.readBytes()
            val covr = indexOfBox(data, "covr")!!
            // covr -> data atom: flag is 4 bytes after the inner "data" header start
            val flag = ByteBuffer.wrap(data, covr + 16, 4).int
            assertEquals(expectedFlag, flag)
        }
    }

    @Test
    fun `empty tags and malformed files are refused`() {
        val input = fileOf(ftyp, moovWith(stco(50)), mdat())
        val out = tmp.newFile()
        assertFalse(Mp4MetadataWriter.write(input, out, Mp4MetadataWriter.Tags()))
        val garbage = tmp.newFile().apply { writeBytes(ByteArray(64) { 7 }) }
        assertFalse(Mp4MetadataWriter.write(garbage, out, Mp4MetadataWriter.Tags(title = "T")))
    }
}
