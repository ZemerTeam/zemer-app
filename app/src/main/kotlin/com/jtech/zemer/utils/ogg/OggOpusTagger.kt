package com.jtech.zemer.utils.ogg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64

/**
 * Pure-Kotlin Ogg Opus metadata writer: rewrites the `OpusTags` comment header (Vorbis
 * comments + an embedded cover picture) of an `.ogg`/`.opus` file. No native or Android
 * dependencies - fully JVM-testable.
 *
 * An Ogg Opus stream is a sequence of pages. The 2nd packet is the `OpusTags` comment
 * header. Replacing it changes its byte length, so every page from that packet onward is
 * re-paginated: segments repacked into <=255-lacing-value pages, page sequence numbers
 * renumbered, and each page's CRC recomputed (Ogg CRC-32, poly 0x04C11DB7, non-reflected).
 * Cover art rides the standard `METADATA_BLOCK_PICTURE` comment (base64 of a FLAC picture
 * block), the de-facto tag players read for .opus art.
 */
object OggOpusTagger {

    data class Tags(
        val artworkData: ByteArray? = null,
        val artworkMime: String? = null,
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

    private const val CAPTURE = 0x4F676753 // "OggS"

    fun write(input: File, output: File, tags: Tags): Boolean {
        if (tags.isEmpty) return false
        return try {
            // STREAMED, page by page: the whole file is never on the heap. The earlier whole-file read plus a
            // per-page copy peaked at ~2x the track size, and three concurrent Opus downloads of long tracks
            // on a low-RAM device was an OutOfMemoryError site. Only the head page, the OpusTags packet and
            // ONE audio page at a time are resident.
            val ok = RandomAccessFile(input, "r").use { raf ->
                val reader = PageReader(raf)
                val headPage = reader.next() ?: return@use false
                val serial = headPage.serial

                // Packet boundaries: a packet ends on a segment whose lacing value < 255. The first page
                // holds OpusHead (packet 0); OpusTags (packet 1) starts on page 1 and may span pages.
                val tagsPacket = extractPacket(reader) ?: return@use false
                val newComment = buildOpusTags(tagsPacket, tags) ?: return@use false

                output.outputStream().buffered().use { out ->
                    out.write(headPage.raw) // OpusHead page unchanged (seq 0)
                    var seq = 1
                    for (page in repaginate(serial, seq, newComment, granule = 0, continued = false, bos = false, eos = false)) {
                        out.write(page); seq++
                    }
                    // Pages after the tags packet are audio - keep their payloads, only renumber+recrc.
                    while (true) {
                        val page = reader.next() ?: break
                        out.write(rewritePageHeader(page.raw, seq)); seq++
                    }
                }
                reader.atEnd // trailing garbage or a truncated page = not a clean Ogg stream: refuse
            }
            if (!ok) output.delete()
            ok
        } catch (e: Exception) {
            output.delete()
            false
        }
    }

    // --- page model ---

    private class Page(
        val raw: ByteArray,
        val serial: Int,
        val segTable: IntArray,
        val bodyOffset: Int,
    )

    /**
     * Sequential page reader over the file. [next] returns null at a clean end of stream OR on a malformed /
     * truncated page - [atEnd] tells the two apart (true only when every byte was consumed as a valid page).
     */
    private class PageReader(private val raf: RandomAccessFile) {
        private val length = raf.length()
        private var pos = 0L
        var atEnd = false
            private set

        fun next(): Page? {
            if (pos == length) { atEnd = true; return null }
            if (pos + 27 > length) return null
            val header = ByteArray(27)
            raf.seek(pos)
            raf.readFully(header)
            if (u32be(header, 0) != CAPTURE) return null
            val serial = u32le(header, 14)
            val segCount = header[26].toInt() and 0xFF
            if (pos + 27 + segCount > length) return null
            val segBytes = ByteArray(segCount)
            raf.readFully(segBytes)
            val segTable = IntArray(segCount) { segBytes[it].toInt() and 0xFF }
            val bodyLen = segTable.sum()
            val total = 27 + segCount + bodyLen
            if (pos + total > length) return null
            val raw = ByteArray(total)
            System.arraycopy(header, 0, raw, 0, 27)
            System.arraycopy(segBytes, 0, raw, 27, segCount)
            raf.readFully(raw, 27 + segCount, bodyLen)
            pos += total
            return Page(raw, serial, segTable, 27 + segCount)
        }
    }

    /** Concatenate the packet body that begins on the reader's next page, up to its terminating lace. */
    private fun extractPacket(reader: PageReader): ByteArray? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val page = reader.next() ?: return null
            var bodyPos = page.bodyOffset
            for (lace in page.segTable) {
                buf.write(page.raw, bodyPos, lace)
                bodyPos += lace
                if (lace < 255) return buf.toByteArray()
            }
            // packet continues onto the next page
        }
    }

    // --- OpusTags comment packet ---

    private fun buildOpusTags(original: ByteArray, tags: Tags): ByteArray? {
        // Layout: "OpusTags" | vendor_len(4 LE) | vendor | list_len(4 LE) | comments...
        if (original.size < 8 || String(original, 0, 8, Charsets.ISO_8859_1) != "OpusTags") return null
        var p = 8
        val vendorLen = u32le(original, p); p += 4
        if (p + vendorLen > original.size) return null
        val vendor = original.copyOfRange(p, p + vendorLen); p += vendorLen
        val count = u32le(original, p); p += 4

        // Preserve existing comments EXCEPT the keys we set (case-insensitive) + old pictures.
        val replaced = setOf("title", "artist", "album", "date", "year", "lyrics", "unsyncedlyrics", "albumartist", "tracknumber", "metadata_block_picture")
        val kept = ArrayList<ByteArray>()
        repeat(count) {
            if (p + 4 > original.size) return@repeat
            val len = u32le(original, p); p += 4
            if (p + len > original.size) return@repeat
            val comment = original.copyOfRange(p, p + len); p += len
            val key = String(comment, Charsets.UTF_8).substringBefore('=').lowercase()
            if (key !in replaced) kept.add(comment)
        }

        val added = ArrayList<ByteArray>()
        tags.title?.let { added.add("TITLE=$it".toByteArray(Charsets.UTF_8)) }
        tags.artist?.let { added.add("ARTIST=$it".toByteArray(Charsets.UTF_8)) }
        tags.album?.let { added.add("ALBUM=$it".toByteArray(Charsets.UTF_8)) }
        tags.year?.let {
            added.add("DATE=$it".toByteArray(Charsets.UTF_8))
            added.add("YEAR=$it".toByteArray(Charsets.UTF_8)) // some players read only YEAR
        }
        tags.lyrics?.let { added.add("LYRICS=$it".toByteArray(Charsets.UTF_8)) }
        tags.albumArtist?.let { added.add("ALBUMARTIST=$it".toByteArray(Charsets.UTF_8)) }
        tags.trackNumber?.let { added.add("TRACKNUMBER=$it".toByteArray(Charsets.UTF_8)) }
        tags.artworkData?.let {
            val block = flacPictureBlock(it, tags.artworkMime ?: sniffMime(it))
            val b64 = Base64.getEncoder().encodeToString(block)
            added.add("METADATA_BLOCK_PICTURE=$b64".toByteArray(Charsets.UTF_8))
        }

        val all = kept + added
        val out = ByteArrayOutputStream()
        out.write("OpusTags".toByteArray(Charsets.ISO_8859_1))
        out.write(u32leBytes(vendor.size)); out.write(vendor)
        out.write(u32leBytes(all.size))
        for (c in all) { out.write(u32leBytes(c.size)); out.write(c) }
        return out.toByteArray()
    }

    /** Minimal FLAC METADATA_BLOCK_PICTURE (type 3 = front cover, no dims - players tolerate 0). */
    private fun flacPictureBlock(image: ByteArray, mime: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v) }
        u32(3) // picture type: front cover
        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
        u32(mimeBytes.size); out.write(mimeBytes)
        u32(0) // description length
        u32(0); u32(0); u32(0); u32(0) // width, height, depth, colors (unknown)
        u32(image.size); out.write(image)
        return out.toByteArray()
    }

    private fun sniffMime(image: ByteArray): String =
        if (image.size >= 4 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"

    // --- pagination ---

    /** Split [packet] into Ogg pages (<=255 segments each), with fresh CRCs. */
    private fun repaginate(
        serial: Int, startSeq: Int, packet: ByteArray, granule: Long,
        continued: Boolean, bos: Boolean, eos: Boolean,
    ): List<ByteArray> {
        // Lacing: floor(len/255) 255s then len%255 (a trailing 0 when len is a 255-multiple).
        val laces = ArrayList<Int>()
        var remaining = packet.size
        while (remaining >= 255) { laces.add(255); remaining -= 255 }
        laces.add(remaining)

        val pages = ArrayList<ByteArray>()
        var seq = startSeq
        var laceIdx = 0
        var bodyPos = 0
        var first = true
        while (laceIdx < laces.size) {
            val take = minOf(255, laces.size - laceIdx)
            val pageLaces = laces.subList(laceIdx, laceIdx + take)
            val bodyLen = pageLaces.sum()
            var headerType = 0
            if (continued && first) headerType = headerType or 0x01
            if (bos && first) headerType = headerType or 0x02
            val isLastPage = laceIdx + take >= laces.size
            if (eos && isLastPage) headerType = headerType or 0x04
            pages.add(buildPage(serial, seq, headerType, if (isLastPage) granule else -1L, pageLaces, packet, bodyPos, bodyLen))
            bodyPos += bodyLen
            laceIdx += take
            seq++
            first = false
        }
        return pages
    }

    private fun buildPage(
        serial: Int, seq: Int, headerType: Int, granule: Long,
        laces: List<Int>, body: ByteArray, bodyOff: Int, bodyLen: Int,
    ): ByteArray {
        val header = ByteArray(27 + laces.size)
        putU32be(header, 0, CAPTURE)
        header[4] = 0 // version
        header[5] = headerType.toByte()
        putU64le(header, 6, granule)
        putU32le(header, 14, serial)
        putU32le(header, 18, seq)
        // CRC (22..25) left 0 for the checksum pass
        header[26] = laces.size.toByte()
        for (i in laces.indices) header[27 + i] = laces[i].toByte()
        val page = header + body.copyOfRange(bodyOff, bodyOff + bodyLen)
        val crc = oggCrc(page)
        putU32le(page, 22, crc)
        return page
    }

    /** Renumber an existing (audio) page's seq and recompute its CRC, in place (the buffer is the reader's, used once). */
    private fun rewritePageHeader(page: ByteArray, seq: Int): ByteArray {
        putU32le(page, 18, seq)
        putU32le(page, 22, 0)
        putU32le(page, 22, oggCrc(page))
        return page
    }

    // --- Ogg CRC-32 (poly 0x04C11DB7, init 0, no reflection, no final xor) ---

    private val CRC_TABLE = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var r = i shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            t[i] = r
        }
    }

    private fun oggCrc(page: ByteArray): Int {
        var crc = 0
        for (b in page) crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    // --- byte helpers ---

    private fun u32be(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun u32le(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun u32leBytes(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    private fun putU32be(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }

    private fun putU32le(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v ushr 8).toByte(); b[o + 2] = (v ushr 16).toByte(); b[o + 3] = (v ushr 24).toByte()
    }

    private fun putU64le(b: ByteArray, o: Int, v: Long) {
        for (i in 0 until 8) b[o + i] = (v ushr (i * 8)).toByte()
    }
}
