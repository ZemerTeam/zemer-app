package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import com.jtech.zemer.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Utility class for embedding metadata into downloaded M4A audio files.
 * Uses native Bento4 library for reliable DASH/fragmented file support.
 * Supports embedding: cover art, title, artist, album, year.
 * All text is stored as UTF-8 (supports Hebrew, Arabic, and all Unicode).
 */
object CoverArtEmbedder {

    private const val TAG = "CoverArt"
    private const val ARTWORK_SIZE = 500
    private const val ARTWORK_DOWNLOAD_TIMEOUT_MS = 10_000L

    // Only M4A/MP4 supported
    private val SUPPORTED_EXTENSIONS = setOf("m4a", "mp4")

    fun supportsEmbedding(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Embed metadata (cover art, title, artist, album, year) into an M4A/MP4 file.
     * All text fields support UTF-8 including Hebrew, Arabic, etc.
     */
    suspend fun embedMetadataIntoFile(
        context: Context,
        audioFile: File,
        thumbnailUrl: String?,
        httpClient: OkHttpClient,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        year: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var outputFile: File? = null
        try {
            Log.d(TAG, "Starting metadata embedding for ${audioFile.name}")
            Log.d(TAG, "Title: $title, Artist: $artist, Album: $album, Year: $year")

            // Download artwork if URL provided
            var artworkData: ByteArray? = null
            if (!thumbnailUrl.isNullOrBlank()) {
                Log.d(TAG, "Thumbnail URL: $thumbnailUrl")
                val artworkUrl = getOptimizedUrl(thumbnailUrl)
                Log.d(TAG, "Optimized URL: $artworkUrl")

                artworkData = withTimeoutOrNull(ARTWORK_DOWNLOAD_TIMEOUT_MS) {
                    downloadArtwork(artworkUrl, httpClient)
                }

                if (artworkData != null && artworkData.size >= 1000) {
                    Log.d(TAG, "Artwork downloaded: ${artworkData.size} bytes")
                } else {
                    Log.w(TAG, "Artwork download failed or too small")
                    artworkData = null
                }
            }

            // Try to defragment first (for DASH files)
            tempFile = File(context.cacheDir, "defrag_${System.currentTimeMillis()}.m4a")
            val defragmented = try {
                CoverArtNative.defragmentFile(audioFile.absolutePath, tempFile.absolutePath)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not loaded: ${e.message}")
                false
            }

            val workingFile = if (defragmented && tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "Using defragmented file")
                tempFile
            } else {
                Log.d(TAG, "Using original file (defragment not needed or failed)")
                tempFile.delete()
                tempFile = null
                audioFile
            }

            // Embed metadata using native library
            outputFile = File(context.cacheDir, "output_${System.currentTimeMillis()}.m4a")
            val success = try {
                CoverArtNative.embedMetadata(
                    inputPath = workingFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    artworkData = artworkData,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year?.toString()
                )
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not loaded: ${e.message}")
                false
            }

            // Verify output is valid - must be at least 90% of original size
            // (metadata addition shouldn't significantly reduce file size)
            val originalSize = workingFile.length()
            val outputSize = outputFile?.length() ?: 0
            val sizeRatio = if (originalSize > 0) outputSize.toDouble() / originalSize else 0.0

            if (success && outputFile != null && outputFile.exists() && outputSize > 0 && sizeRatio > 0.9) {
                // Replace original with processed file
                audioFile.delete()
                outputFile.renameTo(audioFile)
                tempFile?.delete()
                Log.d(TAG, "Successfully embedded metadata (size ratio: $sizeRatio)")
                true
            } else {
                Log.w(TAG, "Metadata embedding failed or output invalid (success=$success, size=$outputSize, ratio=$sizeRatio)")
                outputFile?.delete()
                tempFile?.delete()
                // Original file is preserved - download still works without metadata
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed metadata: ${e.message}", e)
            tempFile?.delete()
            outputFile?.delete()
            false
        }
    }

    private fun getOptimizedUrl(url: String): String {
        return url.resize(ARTWORK_SIZE, ARTWORK_SIZE).let { resized ->
            if (resized == url && url.contains("i.ytimg.com")) {
                url.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
            } else {
                resized
            }
        }
    }

    private suspend fun downloadArtwork(url: String, httpClient: OkHttpClient): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading artwork from: $url")
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed: ${response.code}")
                    response.close()
                    return@withContext null
                }

                val bytes = response.body?.bytes()
                Log.d(TAG, "Downloaded ${bytes?.size ?: 0} bytes")
                bytes
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                null
            }
        }
}
