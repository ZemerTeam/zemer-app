package com.jtech.zemer.utils.mp4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Hard-data validation against a REAL YouTube m4a downloaded through the app's exact
 * resolve path (tests/download-media-samples.mjs). Skipped when ZEMER_TEST_M4A is unset
 * (CI has no cookie); run locally after downloading samples:
 *
 *   node tests/download-media-samples.mjs <dir>
 *   ZEMER_TEST_M4A=<dir>/sample-...-140.m4a ./gradlew :app:testDebugUnitTest --tests '*RealFileTest*'
 *
 * ffprobe/ffmpeg ground-truth (decode + tag readback) runs in the calling shell, not here.
 */
class Mp4MetadataWriterRealFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `tags a real downloaded m4a without disturbing the media`() {
        val path = System.getenv("ZEMER_TEST_M4A")
        assumeTrue("ZEMER_TEST_M4A not set - skipping real-file validation", !path.isNullOrBlank())
        val input = File(path!!)
        assumeTrue(input.isFile)

        val artPath = System.getenv("ZEMER_TEST_JPG")
        val art = if (!artPath.isNullOrBlank() && File(artPath).isFile) File(artPath).readBytes()
                  else ByteArray(24) { 0 }.also { it[0]=0xFF.toByte(); it[1]=0xD8.toByte(); it[2]=0xFF.toByte(); it[3]=0xE0.toByte() }
        val out = tmp.newFile("tagged.m4a")
        assertTrue(
            "writer must succeed on a real CDN m4a",
            Mp4MetadataWriter.write(
                input, out,
                Mp4MetadataWriter.Tags(
                    artworkData = art,
                    title = "כותרת בדיקה",  // Hebrew round-trip, the UTF-8 contract
                    artist = "Test Artist",
                    album = "Test Album",
                    year = "2026",
                ),
            ),
        )
        // Output grows by the tag payload and nothing shrinks (the embedder's 90% guard).
        assertTrue(out.length() > input.length())
        assertTrue(out.length() < input.length() + art.size + 4096)
        // Environment echo for the calling shell's ffprobe step.
        println("REALFILE_OUT=${out.absolutePath}")
        // Keep the output for the shell: copy to a stable sibling of the input.
        val kept = File(input.parentFile, input.nameWithoutExtension + "-tagged.m4a")
        input.parentFile?.let { out.copyTo(kept, overwrite = true) }
        assertEquals(kept.length(), out.length())
    }
}
