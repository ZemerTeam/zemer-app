package com.jtech.zemer.utils

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.jtech.zemer.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Utility class for embedding cover art into downloaded audio files using FFmpeg.
 * Supports all audio formats that FFmpeg can handle.
 */
object CoverArtEmbedder {

    private const val TAG = "CoverArt"

    // Artwork dimensions for embedding
    private const val ARTWORK_SIZE = 500

    // Timeout for artwork download (10 seconds)
    private const val ARTWORK_DOWNLOAD_TIMEOUT_MS = 10_000L

    // Extensions that support embedded artwork via FFmpeg
    private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "webm")

    /**
     * Check if the given file extension supports embedded artwork
     */
    fun supportsEmbedding(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Download and embed artwork into an audio file using FFmpeg.
     * This creates a new file with artwork and replaces the original.
     *
     * @param context Android context for cache directory
     * @param audioFile The audio file to embed artwork into
     * @param thumbnailUrl The URL of the thumbnail/cover art
     * @param httpClient OkHttpClient instance to use for downloading
     * @return true if artwork was successfully embedded, false otherwise
     */
    suspend fun embedArtworkIntoFile(
        context: Context,
        audioFile: File,
        thumbnailUrl: String,
        httpClient: OkHttpClient
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting FFmpeg artwork embedding for ${audioFile.name}")

            // Get optimized thumbnail URL
            val artworkUrl = thumbnailUrl.resize(ARTWORK_SIZE, ARTWORK_SIZE).let { resized ->
                if (resized == thumbnailUrl && thumbnailUrl.contains("i.ytimg.com")) {
                    thumbnailUrl.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
                } else {
                    resized
                }
            }

            // Download artwork to temp file
            val artworkFile = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
            val artworkData = withTimeoutOrNull(ARTWORK_DOWNLOAD_TIMEOUT_MS) {
                downloadArtwork(artworkUrl, httpClient)
            }

            if (artworkData == null || artworkData.size < 1000) {
                Log.w(TAG, "Artwork download failed or too small")
                return@withContext false
            }

            // Write artwork to temp file
            artworkFile.writeBytes(artworkData)
            Log.d(TAG, "Artwork saved to ${artworkFile.absolutePath}, size: ${artworkData.size}")

            // Create output file
            val outputFile = File(context.cacheDir, "output_${System.currentTimeMillis()}.${audioFile.extension}")

            try {
                // Use FFmpeg to embed cover art
                val success = embedWithFFmpeg(audioFile, artworkFile, outputFile)

                if (success && outputFile.exists() && outputFile.length() > 0) {
                    // Replace original with output
                    audioFile.delete()
                    outputFile.renameTo(audioFile)
                    Log.d(TAG, "Successfully embedded artwork into ${audioFile.name}")
                    true
                } else {
                    Log.w(TAG, "FFmpeg embedding failed")
                    outputFile.delete()
                    false
                }
            } finally {
                // Cleanup
                artworkFile.delete()
                if (outputFile.exists()) outputFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed artwork: ${e.message}", e)
            false
        }
    }

    /**
     * Embed cover art using FFmpeg
     * Uses simple commands compatible with ffmpeg-kit-min package
     */
    private fun embedWithFFmpeg(audioFile: File, artworkFile: File, outputFile: File): Boolean {
        val inputPath = audioFile.absolutePath
        val artworkPath = artworkFile.absolutePath
        val outputPath = outputFile.absolutePath
        val extension = audioFile.extension.lowercase()

        // Build FFmpeg command based on format
        // Using simple commands compatible with min package (no external encoders)
        val command = when (extension) {
            "mp3" -> {
                // For MP3, use ID3v2 cover art with copy for image
                "-i $inputPath -i $artworkPath -map 0:a -map 1:0 -c:a copy -c:v copy -id3v2_version 3 -metadata:s:v title=Cover -metadata:s:v comment=Cover -y $outputPath"
            }
            "m4a", "mp4" -> {
                // For M4A/MP4, embed as attached picture
                "-i $inputPath -i $artworkPath -map 0:a -map 1:0 -c:a copy -c:v copy -disposition:v:0 attached_pic -y $outputPath"
            }
            "flac" -> {
                // For FLAC, embed as attached picture
                "-i $inputPath -i $artworkPath -map 0:a -map 1:0 -c:a copy -c:v copy -disposition:v:0 attached_pic -y $outputPath"
            }
            "ogg", "opus", "webm" -> {
                // For OGG/Opus/WebM - these formats have limited cover art support
                // Try generic approach, may not work with all players
                "-i $inputPath -i $artworkPath -map 0:a -map 1:0 -c:a copy -c:v copy -disposition:v:0 attached_pic -y $outputPath"
            }
            else -> {
                // Generic approach
                "-i $inputPath -i $artworkPath -map 0:a -map 1:0 -c:a copy -c:v copy -disposition:v:0 attached_pic -y $outputPath"
            }
        }

        Log.d(TAG, "FFmpeg command: ffmpeg $command")

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "FFmpeg succeeded")
            return true
        } else {
            Log.e(TAG, "FFmpeg failed with code: $returnCode")
            Log.e(TAG, "FFmpeg output: ${session.output}")
            return false
        }
    }

    /**
     * Download artwork from URL
     */
    private suspend fun downloadArtwork(
        url: String,
        httpClient: OkHttpClient
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Artwork download failed with code ${response.code}")
                response.close()
                return@withContext null
            }

            response.body?.bytes()
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading artwork: ${e.message}")
            null
        }
    }
}
