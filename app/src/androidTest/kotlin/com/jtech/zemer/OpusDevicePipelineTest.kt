package com.jtech.zemer

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.jtech.zemer.utils.mp4.AudioRemux
import com.jtech.zemer.utils.ogg.OggOpusTagger
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the REAL on-device Opus download pipeline (framework MediaMuxer WebM->Ogg remux,
 * then the pure-Kotlin OpusTags writer) against a real CDN itag-251 sample bundled as a
 * test asset. Outputs land in the app's external files dir for `adb pull` + ffprobe
 * ground-truthing on the host.
 */
class OpusDevicePipelineTest {

    @Test
    fun webmToTaggedOgg() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val appContext = inst.targetContext
        val outDir = appContext.filesDir

        val webm = File(outDir, "sample-opus.webm")
        inst.context.assets.open("sample-opus.webm").use { a -> webm.outputStream().use { a.copyTo(it) } }
        val art = inst.context.assets.open("cover.jpg").use { it.readBytes() }

        assertTrue("OGG mux must be supported on this API level", AudioRemux.oggMuxSupported)

        val ogg = File(outDir, "remuxed.ogg")
        val remuxOk = AudioRemux.webmOpusToOgg(webm, ogg)
        Log.i("OpusPipeline", "remux ok=$remuxOk size=${ogg.length()}")
        assertTrue("MediaMuxer WebM->Ogg remux must succeed", remuxOk)

        val tagged = File(outDir, "tagged.ogg")
        val tagOk = OggOpusTagger.write(
            ogg, tagged,
            OggOpusTagger.Tags(
                artworkData = art,
                artworkMime = "image/jpeg",
                title = "כותרת בדיקה",
                artist = "Device Artist",
                album = "Device Album",
                year = "2026",
            ),
        )
        Log.i("OpusPipeline", "tag ok=$tagOk size=${tagged.length()} dir=$outDir")
        assertTrue("OggOpusTagger must succeed on MediaMuxer output", tagOk)
    }

    /** Field-parity proof: Android's own MediaMetadataRetriever must read back every field
     *  the old native embedder wrote (title/artist/album/year + embedded picture) from BOTH
     *  new containers. */
    @Test
    fun androidReadsAllFieldsBack() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val outDir = inst.targetContext.filesDir
        val art = inst.context.assets.open("cover.jpg").use { it.readBytes() }

        // MP4 path
        val m4a = File(outDir, "sample-flat.m4a")
        inst.context.assets.open("sample-flat.m4a").use { a -> m4a.outputStream().use { a.copyTo(it) } }
        val taggedM4a = File(outDir, "parity.m4a")
        assertTrue(com.jtech.zemer.utils.mp4.Mp4MetadataWriter.write(
            m4a, taggedM4a,
            com.jtech.zemer.utils.mp4.Mp4MetadataWriter.Tags(art, "PTitle", "PArtist", "PAlbum", "2026")))
        assertFieldParity(taggedM4a, expectPicture = true)

        // Ogg path (from the remux the first test produced, or rebuild)
        val ogg = File(outDir, "remuxed.ogg")
        if (!ogg.isFile) {
            val webm = File(outDir, "sample-opus.webm")
            inst.context.assets.open("sample-opus.webm").use { a -> webm.outputStream().use { a.copyTo(it) } }
            assertTrue(AudioRemux.webmOpusToOgg(webm, ogg))
        }
        val taggedOgg = File(outDir, "parity.ogg")
        assertTrue(OggOpusTagger.write(ogg, taggedOgg,
            OggOpusTagger.Tags(art, "image/jpeg", "PTitle", "PArtist", "PAlbum", "2026")))
        // Retriever reads Vorbis comments; embedded METADATA_BLOCK_PICTURE support varies by
        // Android version, so the picture is asserted from the file bytes instead.
        assertFieldParity(taggedOgg, expectPicture = false)
        assertTrue("picture comment must be in the ogg",
            taggedOgg.readBytes().let { String(it, Charsets.ISO_8859_1).contains("METADATA_BLOCK_PICTURE=") })
    }

    private fun assertFieldParity(file: File, expectPicture: Boolean) {
        val r = android.media.MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val title = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val year = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
            ?: r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
        val pic = r.embeddedPicture
        r.release()
        Log.i("OpusPipeline", "parity ${file.name}: title=$title artist=$artist album=$album year=$year pic=${pic?.size}")
        org.junit.Assert.assertEquals("PTitle", title)
        org.junit.Assert.assertEquals("PArtist", artist)
        org.junit.Assert.assertEquals("PAlbum", album)
        assertTrue("year must read back", year?.contains("2026") == true)
        if (expectPicture) assertTrue("embedded picture must read back", pic != null && pic.isNotEmpty())
    }
}
