package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import com.jtech.zemer.ui.utils.resize
import com.jtech.zemer.utils.mp4.AudioRemux
import com.jtech.zemer.utils.mp4.Mp4MetadataWriter
import com.jtech.zemer.utils.ogg.OggOpusTagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Embeds metadata (cover art + title/artist/album/year, all UTF-8) into a downloaded
 * audio file. Container-routed through pure-Kotlin writers - no native dependency:
 *
 * - MP4/M4A -> flatten fragmented input via [AudioRemux.flattenMp4], then
 *   [Mp4MetadataWriter].
 * - WebM/Ogg Opus -> rewrap WebM to Ogg via [AudioRemux.webmOpusToOgg] (API 29+), then
 *   [OggOpusTagger]. An already-Ogg file is tagged directly.
 *
 * Fully fail-soft: any failure leaves the ORIGINAL file untouched (a download without
 * embedded art still plays). Text supports Hebrew/Arabic/all Unicode.
 */
object CoverArtEmbedder {

    private const val TAG = "CoverArt"
    private const val ARTWORK_SIZE = 500
    private const val ARTWORK_DOWNLOAD_TIMEOUT_MS = 10_000L

    private val MP4_EXTENSIONS = setOf("m4a", "mp4")
    private val OPUS_EXTENSIONS = setOf("opus", "ogg", "webm")

    /** True when we can embed metadata into a file of this extension on this device. */
    fun supportsEmbedding(extension: String): Boolean {
        val ext = extension.lowercase()
        return ext in MP4_EXTENSIONS || (ext in OPUS_EXTENSIONS && AudioRemux.oggMuxSupported)
    }

    /**
     * Embed metadata into [audioFile] in place. Returns true on success. For a WebM/Opus
     * input the on-disk file is REPLACED with a tagged `.ogg`; [onContainerChanged] is
     * invoked with the new extension so the caller can update the MediaStore entry.
     */
    suspend fun embedMetadataIntoFile(
        context: Context,
        audioFile: File,
        thumbnailUrl: String?,
        httpClient: OkHttpClient,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null,
        lyrics: String? = null,
        onContainerChanged: ((newExtension: String) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val scratch = mutableListOf<File>()
        try {
            val artwork = downloadArtworkOrNull(thumbnailUrl, httpClient)
            val ext = audioFile.extension.lowercase()
            when {
                ext in MP4_EXTENSIONS ->
                    embedMp4(context, audioFile, artwork, title, artist, album, year, lyrics, scratch)
                ext in OPUS_EXTENSIONS ->
                    embedOpus(context, audioFile, artwork, title, artist, album, year, lyrics, scratch, onContainerChanged)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed metadata: ${e.message}", e)
            false
        } finally {
            scratch.forEach { it.delete() }
        }
    }

    private fun embedMp4(
        context: Context,
        audioFile: File,
        artwork: ByteArray?,
        title: String?, artist: String?, album: String?, year: Int?, lyrics: String?,
        scratch: MutableList<File>,
    ): Boolean {
        // Flatten a fragmented/DASH m4a first (the writer only tags a flat moov+mdat file).
        val flat = if (Mp4MetadataWriter.isFragmented(audioFile)) {
            File(context.cacheDir, "flat_${System.currentTimeMillis()}.m4a").also { scratch.add(it) }
                .takeIf { AudioRemux.flattenMp4(audioFile, it) } ?: run {
                Log.w(TAG, "MP4 flatten failed; leaving original untagged")
                return false
            }
        } else {
            audioFile
        }
        val out = File(context.cacheDir, "tagged_${System.currentTimeMillis()}.m4a").also { scratch.add(it) }
        val tags = Mp4MetadataWriter.Tags(artwork, title, artist, album, year?.toString(), lyrics)
        if (!Mp4MetadataWriter.write(flat, out, tags)) return false
        return replaceValidated(audioFile, out, flat.length())
    }

    private fun embedOpus(
        context: Context,
        audioFile: File,
        artwork: ByteArray?,
        title: String?, artist: String?, album: String?, year: Int?, lyrics: String?,
        scratch: MutableList<File>,
        onContainerChanged: ((String) -> Unit)?,
    ): Boolean {
        if (!AudioRemux.oggMuxSupported) return false
        val ext = audioFile.extension.lowercase()
        // A real Ogg is tagged directly; WebM/Opus is rewrapped to Ogg first.
        val ogg = if (ext == "ogg") audioFile else
            File(context.cacheDir, "ogg_${System.currentTimeMillis()}.ogg").also { scratch.add(it) }
                .takeIf { AudioRemux.webmOpusToOgg(audioFile, it) } ?: run {
                Log.w(TAG, "WebM->Ogg remux failed; leaving original untagged")
                return false
            }
        val out = File(context.cacheDir, "taggedogg_${System.currentTimeMillis()}.ogg").also { scratch.add(it) }
        val tags = OggOpusTagger.Tags(artwork, sniffMime(artwork), title, artist, album, year?.toString(), lyrics)
        if (!OggOpusTagger.write(ogg, out, tags)) return false
        // The tagged Ogg bytes overwrite the temp file IN PLACE (its path is unchanged), so the
        // caller's MediaStore save still reads the right file; only the entry's extension/MIME
        // changes to .ogg, signalled here.
        if (!replaceValidated(audioFile, out, ogg.length())) return false
        if (ext != "ogg") onContainerChanged?.invoke("ogg")
        return true
    }

    /** Adopt [processed] as [audioFile] only when it is a plausible result (>= 90% of ref size). */
    private fun replaceValidated(audioFile: File, processed: File, referenceSize: Long): Boolean {
        val ratio = if (referenceSize > 0) processed.length().toDouble() / referenceSize else 0.0
        if (!processed.exists() || processed.length() == 0L || ratio <= 0.9) {
            Log.w(TAG, "Embed output invalid (size=${processed.length()}, ratio=$ratio)")
            return false
        }
        audioFile.delete()
        return processed.renameTo(audioFile).also {
            if (!it) Log.w(TAG, "Failed to move tagged file over the original")
        }
    }

    private suspend fun downloadArtworkOrNull(thumbnailUrl: String?, httpClient: OkHttpClient): ByteArray? {
        if (thumbnailUrl.isNullOrBlank()) return null
        val artwork = withTimeoutOrNull(ARTWORK_DOWNLOAD_TIMEOUT_MS) {
            downloadArtwork(getOptimizedUrl(thumbnailUrl), httpClient)
        }
        return artwork?.takeIf { it.size >= 1000 }
    }

    private fun sniffMime(image: ByteArray?): String =
        if (image != null && image.size >= 2 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte())
            "image/png" else "image/jpeg"

    private fun getOptimizedUrl(url: String): String =
        url.resize(ARTWORK_SIZE, ARTWORK_SIZE).let { resized ->
            if (resized == url && url.contains("i.ytimg.com")) {
                url.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
            } else {
                resized
            }
        }

    private suspend fun downloadArtwork(url: String, httpClient: OkHttpClient): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    response.body?.bytes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Artwork download error: ${e.message}", e)
                null
            }
        }
}
