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
 * Utility class for embedding cover art into downloaded M4A audio files.
 * Uses native Bento4 library for reliable DASH/fragmented file support.
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

    suspend fun embedArtworkIntoFile(
        context: Context,
        audioFile: File,
        thumbnailUrl: String,
        httpClient: OkHttpClient
    ): Boolean = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        var outputFile: File? = null
        try {
            Log.d(TAG, "Starting artwork embedding for ${audioFile.name}")
            Log.d(TAG, "Thumbnail URL: $thumbnailUrl")

            // Get optimized thumbnail URL
            val artworkUrl = getOptimizedUrl(thumbnailUrl)
            Log.d(TAG, "Optimized URL: $artworkUrl")

            // Download artwork
            val artworkData = withTimeoutOrNull(ARTWORK_DOWNLOAD_TIMEOUT_MS) {
                downloadArtwork(artworkUrl, httpClient)
            }

            if (artworkData == null) {
                Log.w(TAG, "Artwork download returned null")
                return@withContext false
            }

            if (artworkData.size < 1000) {
                Log.w(TAG, "Artwork too small: ${artworkData.size} bytes")
                return@withContext false
            }

            Log.d(TAG, "Artwork downloaded: ${artworkData.size} bytes")

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

            // Embed cover art using native library
            outputFile = File(context.cacheDir, "output_${System.currentTimeMillis()}.m4a")
            val success = try {
                CoverArtNative.embedCoverArt(
                    workingFile.absolutePath,
                    outputFile.absolutePath,
                    artworkData
                )
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not loaded: ${e.message}")
                false
            }

            if (success && outputFile.exists() && outputFile.length() > 0) {
                // Replace original with processed file
                audioFile.delete()
                outputFile.renameTo(audioFile)
                tempFile?.delete()
                Log.d(TAG, "Successfully embedded artwork")
                true
            } else {
                Log.w(TAG, "Cover art embedding failed")
                outputFile?.delete()
                tempFile?.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed artwork: ${e.message}", e)
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
