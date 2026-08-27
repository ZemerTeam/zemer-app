package com.jtech.zemer.utils.mp4

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Pure-Kotlin MP4/M4A metadata writer: copies an input file to an output path while
 * replacing `moov.udta` with a fresh iTunes-style tag set (cover art + title / artist /
 * album / year). No Android or native dependencies - fully JVM-testable.
 *
 * Correctness hinges on chunk offsets: `stco`/`co64` entries are ABSOLUTE file offsets
 * into `mdat`. Rewriting `moov` changes its size, which shifts every byte after it, so
 * each offset pointing PAST the old moov end is patched by the size delta. Boxes before
 * moov (and an mdat that precedes moov, the faststart-less layout) need no patching.
 *
 * Fragmented files (any top-level `moof`) are REFUSED: their sample tables live in the
 * fragments, not in moov, so this writer cannot retag them safely - the caller must
 * flatten first (see the framework remux in CoverArtEmbedder).
 */
object Mp4MetadataWriter {

    data class Tags(
        val artworkData: ByteArray? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: String? = null,
        val lyrics: String? = null,
        val albumArtist: String? = null,
        val trackNumber: Int? = null,
    ) {
        val isEmpty: Boolean
            get() = artworkData == null && title == null && artist == null && album == null &&
                year == null && lyrics == null && albumArtist == null && trackNumber == null
    }

    /** Containers descended into when patching chunk offsets. */
    private val OFFSET_CONTAINERS = setOf("trak", "mdia", "minf", "stbl")

    /**
     * Write [input] to [output] with [tags] embedded. Returns false (and deletes any
     * partial output) on refusal or malformed input - the caller keeps the original.
     */
    fun write(input: File, output: File, tags: Tags): Boolean {
        if (tags.isEmpty) return false
        return try {
            RandomAccessFile(input, "r").use { raf ->
                val index = topLevelIndex(raf) ?: return false
                if (index.any { it.type == "moof" }) return false
                val moov = index.firstOrNull { it.type == "moov" } ?: return false

                val moovBytes = ByteArray(moov.size.toInt())
                raf.seek(moov.offset)
                raf.readFully(moovBytes)

                val newMoov = rebuildMoov(moovBytes, tags) ?: return false
                val delta = newMoov.size.toLong() - moov.size
                val moovEnd = moov.offset + moov.size
                patchChunkOffsets(newMoov, moovEnd, delta)

                output.outputStream().buffered().use { out ->
                    val copyBuf = ByteArray(1 shl 16)
                    for (box in index) {
                        if (box.type == "moov") {
                            out.write(newMoov)
                        } else {
                            raf.seek(box.offset)
                            var remaining = box.size
                            while (remaining > 0) {
                                val n = raf.read(copyBuf, 0, minOf(copyBuf.size.toLong(), remaining).toInt())
                                if (n <= 0) return false
                                out.write(copyBuf, 0, n)
                                remaining -= n
                            }
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            output.delete()
            false
        }
    }

    /** True when the file has a top-level `moof` (fragmented/DASH layout). */
    fun isFragmented(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            topLevelIndex(raf)?.any { it.type == "moof" } == true
        }
    } catch (e: Exception) {
        false
    }

    // --- box index ---

    internal data class BoxRef(val type: String, val offset: Long, val size: Long)

    private fun topLevelIndex(raf: RandomAccessFile): List<BoxRef>? {
        val fileLen = raf.length()
        val out = mutableListOf<BoxRef>()
        var pos = 0L
        val head = ByteArray(16)
        while (pos + 8 <= fileLen) {
            raf.seek(pos)
            raf.readFully(head, 0, 8)
            var size = u32(head, 0)
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8L
            when (size) {
                1L -> { // 64-bit largesize follows the type
                    if (pos + 16 > fileLen) return null
                    raf.readFully(head, 8, 8)
                    size = u64(head, 8)
                    headerLen = 16L
                }
                0L -> size = fileLen - pos // box extends to end of file
            }
            if (size < headerLen || pos + size > fileLen) return null
            out.add(BoxRef(type, pos, size))
            pos += size
        }
        return out.takeIf { it.isNotEmpty() && pos == fileLen }
    }

    // --- moov rebuild ---

    /** New moov: every child except `udta` preserved byte-for-byte, our udta appended. */
    private fun rebuildMoov(moov: ByteArray, tags: Tags): ByteArray? {
        val children = childBoxes(moov, 8, moov.size) ?: return null
        val kept = children.filter { boxType(moov, it.first) != "udta" }
        val udta = buildUdta(tags)
        var newSize = 8
        kept.forEach { newSize += it.second }
        newSize += udta.size
        val out = ByteArray(newSize)
        putU32(out, 0, newSize.toLong())
        System.arraycopy(moov, 4, out, 4, 4) // "moov"
        var w = 8
        for ((off, len) in kept) {
            System.arraycopy(moov, off, out, w, len)
            w += len
        }
        System.arraycopy(udta, 0, out, w, udta.size)
        return out
    }

    /** (offset, length) of each child box inside [from, to) - null on malformed sizes. */
    private fun childBoxes(buf: ByteArray, from: Int, to: Int): List<Pair<Int, Int>>? {
        val out = mutableListOf<Pair<Int, Int>>()
        var pos = from
        while (pos + 8 <= to) {
            var size = u32(buf, pos)
            if (size == 1L) {
                if (pos + 16 > to) return null
                size = u64(buf, pos + 8)
            } else if (size == 0L) {
                size = (to - pos).toLong()
            }
            if (size < 8 || pos + size > to) return null
            out.add(pos to size.toInt())
            pos += size.toInt()
        }
        return out.takeIf { pos == to }
    }

    private fun boxType(buf: ByteArray, offset: Int) =
        String(buf, offset + 4, 4, Charsets.ISO_8859_1)

    // --- chunk-offset patching ---

    /**
     * Patch every stco/co64 entry in [moov] that points at or past [oldMoovEnd] by
     * [delta]. Entries below oldMoovEnd reference data ahead of moov and are unmoved.
     */
    private fun patchChunkOffsets(moov: ByteArray, oldMoovEnd: Long, delta: Long) {
        if (delta == 0L) return
        patchOffsetsIn(moov, 8, moov.size, oldMoovEnd, delta)
    }

    private fun patchOffsetsIn(buf: ByteArray, from: Int, to: Int, floor: Long, delta: Long) {
        val children = childBoxes(buf, from, to) ?: return
        for ((off, len) in children) {
            when (boxType(buf, off)) {
                in OFFSET_CONTAINERS -> patchOffsetsIn(buf, off + 8, off + len, floor, delta)
                "stco" -> {
                    val count = u32(buf, off + 12).toInt()
                    var p = off + 16
                    repeat(count) {
                        if (p + 4 > off + len) return
                        val v = u32(buf, p)
                        if (v >= floor) putU32(buf, p, v + delta)
                        p += 4
                    }
                }
                "co64" -> {
                    val count = u32(buf, off + 12).toInt()
                    var p = off + 16
                    repeat(count) {
                        if (p + 8 > off + len) return
                        val v = u64(buf, p)
                        if (v >= floor) putU64(buf, p, v + delta)
                        p += 8
                    }
                }
            }
        }
    }

    // --- udta construction ---

    private fun buildUdta(tags: Tags): ByteArray {
        val ilst = box("ilst") {
            tags.title?.let { add(textAtom("©nam", it)) }
            tags.artist?.let { add(textAtom("©ART", it)) }
            tags.album?.let { add(textAtom("©alb", it)) }
            tags.year?.let { add(textAtom("©day", it)) }
            tags.lyrics?.let { add(textAtom("©lyr", it)) }
            tags.albumArtist?.let { add(textAtom("aART", it)) }
            tags.trackNumber?.let { add(trknAtom(it)) }
            tags.artworkData?.let { add(coverAtom(it)) }
        }
        val hdlr = ByteBuffer.allocate(33).apply {
            putInt(33); put("hdlr".toByteArray(Charsets.ISO_8859_1))
            putInt(0)                                    // version/flags
            putInt(0)                                    // pre_defined
            put("mdir".toByteArray(Charsets.ISO_8859_1)) // handler type
            put("appl".toByteArray(Charsets.ISO_8859_1)) // reserved[0]
            putInt(0); putInt(0)                         // reserved[1..2]
            put(0)                                       // empty name
        }.array()
        // meta is a FullBox: 4 version/flags bytes precede its children.
        val meta = ByteBuffer.allocate(12 + hdlr.size + ilst.size).apply {
            putInt(12 + hdlr.size + ilst.size); put("meta".toByteArray(Charsets.ISO_8859_1))
            putInt(0)
            put(hdlr); put(ilst)
        }.array()
        return ByteBuffer.allocate(8 + meta.size).apply {
            putInt(8 + meta.size); put("udta".toByteArray(Charsets.ISO_8859_1))
            put(meta)
        }.array()
    }

    private fun box(type: String, fill: MutableList<ByteArray>.() -> Unit): ByteArray {
        val parts = mutableListOf<ByteArray>().apply(fill)
        val size = 8 + parts.sumOf { it.size }
        return ByteBuffer.allocate(size).apply {
            putInt(size); put(type.toByteArray(Charsets.ISO_8859_1))
            parts.forEach { put(it) }
        }.array()
    }

    private fun dataAtom(name: String, typeFlag: Int, payload: ByteArray): ByteArray {
        val dataSize = 16 + payload.size
        val total = 8 + dataSize
        return ByteBuffer.allocate(total).apply {
            putInt(total); put(name.toByteArray(Charsets.ISO_8859_1))
            putInt(dataSize); put("data".toByteArray(Charsets.ISO_8859_1))
            putInt(typeFlag)  // well-known type
            putInt(0)         // locale
            put(payload)
        }.array()
    }

    private fun textAtom(name: String, value: String) =
        dataAtom(name, 1, value.toByteArray(Charsets.UTF_8))

    /** iTunes trkn: type 0 (binary), payload 0 0 / track(2 BE) / total(2, 0=unknown) / 0 0. */
    private fun trknAtom(track: Int): ByteArray {
        val payload = ByteArray(8)
        payload[2] = (track ushr 8).toByte(); payload[3] = track.toByte()
        return dataAtom("trkn", 0, payload)
    }

    private fun coverAtom(image: ByteArray): ByteArray {
        val isPng = image.size >= 8 &&
            image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte() &&
            image[2] == 'N'.code.toByte() && image[3] == 'G'.code.toByte()
        return dataAtom("covr", if (isPng) 14 else 13, image)
    }

    // --- byte helpers ---

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = (v ushr 24).toByte(); b[off + 1] = (v ushr 16).toByte()
        b[off + 2] = (v ushr 8).toByte(); b[off + 3] = v.toByte()
    }

    private fun putU64(b: ByteArray, off: Int, v: Long) {
        for (i in 0 until 8) b[off + i] = (v ushr (56 - i * 8)).toByte()
    }
}
