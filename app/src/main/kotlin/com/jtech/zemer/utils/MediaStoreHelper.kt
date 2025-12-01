@file:Suppress("VariableNeverRead")

package com.jtech.zemer.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Helper class for MediaStore operations to save downloaded music files
 * to the public Music/Zemer folder accessible by other apps.
 *
 * Files stored via MediaStore:
 * - Survive app uninstall
 * - Are accessible to other music players
 * - Are properly indexed by the system media scanner
 * - Respect Android 10+ scoped storage requirements
 */
class MediaStoreHelper(private val context: Context) {

    companion object {
        private const val ZEMER_FOLDER = "Zemer"
        private const val MAX_FILENAME_LENGTH = 200

        // Supported audio MIME types
        private val MIME_TYPE_MAP = mapOf(
            "opus" to "audio/opus",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "webm" to "audio/webm",
            "ogg" to "audio/ogg",
            "mp3" to "audio/mpeg",
            "aac" to "audio/aac",
            "flac" to "audio/flac"
        )
    }

    /**
     * Save a downloaded audio file to MediaStore in the Music/Zemer folder
     *
     * @param inputStream The audio file data stream
     * @param fileName Desired filename (will be sanitized)
     * @param mimeType Audio MIME type (e.g., "audio/opus")
     * @param title Song title for metadata
     * @param artist Artist name for metadata
     * @param album Album name for metadata (optional)
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveToMediaStore(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sanitizedFileName = sanitizeFileName(fileName)
            val contentResolver = context.contentResolver

            // Prepare ContentValues with metadata
            // Organize files: Music/Zemer/{Artist}/{Album}/Song.mp3 or Music/Zemer/{Artist}/Song.mp3
            val sanitizedArtist = sanitizeFolderName(artist)
            val relativePath = if (!album.isNullOrBlank()) {
                val sanitizedAlbum = sanitizeFolderName(album)
                "${Environment.DIRECTORY_MUSIC}/$ZEMER_FOLDER/$sanitizedArtist/$sanitizedAlbum"
            } else {
                "${Environment.DIRECTORY_MUSIC}/$ZEMER_FOLDER/$sanitizedArtist"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                durationMs?.let { put(MediaStore.Audio.Media.DURATION, it) }

                // Set relative path for Android 10+ (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            // Insert the file entry into MediaStore
            val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val audioUri = contentResolver.insert(audioCollection, contentValues)

            if (audioUri == null) {
                return@withContext null
            }

            // Write the actual file content
            contentResolver.openOutputStream(audioUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            } ?: run {
                contentResolver.delete(audioUri, null, null)
                return@withContext null
            }

            // Mark file as ready (remove IS_PENDING flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(audioUri, contentValues, null, null)
            }

            audioUri

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a file from a temporary location to MediaStore
     *
     * @param tempFile Temporary file to move to MediaStore
     * @param fileName Desired filename
     * @param mimeType Audio MIME type
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional)
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveFileToMediaStore(
        tempFile: File,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!tempFile.exists()) {
                return@withContext null
            }

            val fileSize = tempFile.length()

            if (fileSize == 0L) {
                return@withContext null
            }

            tempFile.inputStream().use { inputStream ->
                saveToMediaStore(
                    inputStream = inputStream,
                    fileName = fileName,
                    mimeType = mimeType,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a file already exists in MediaStore
     *
     * @param title Song title to search for
     * @param artist Artist name to search for
     * @return Uri of existing file, or null if not found
     */
    suspend fun findExistingFile(title: String, artist: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST
                )

                val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
                val selectionArgs = arrayOf(title, artist)

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val id = cursor.getLong(idColumn)
                        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Audio.Media.getContentUri(
                                MediaStore.VOLUME_EXTERNAL_PRIMARY
                            )
                        } else {
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                        Uri.withAppendedPath(contentUri, id.toString())
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Delete a file from MediaStore
     *
     * @param uri Uri of the file to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteFromMediaStore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get MIME type from file extension
     *
     * @param extension File extension (e.g., "opus", "m4a")
     * @return MIME type string, or "audio/mpeg" as default
     */
    fun getMimeType(extension: String): String {
        return MIME_TYPE_MAP[extension.lowercase()] ?: "audio/mpeg"
    }

    /**
     * Sanitize a filename to be safe for filesystem use
     * Removes invalid characters and limits length
     *
     * @param fileName Original filename
     * @return Sanitized filename
     */
    private fun sanitizeFileName(fileName: String): String {
        // Remove invalid characters for filenames
        var sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // Remove leading/trailing whitespace and dots
        sanitized = sanitized.trim().trimStart('.')

        // Limit filename length
        if (sanitized.length > MAX_FILENAME_LENGTH) {
            val extension = sanitized.substringAfterLast('.', "")
            val nameWithoutExt = sanitized.substringBeforeLast('.')
            val maxNameLength = MAX_FILENAME_LENGTH - extension.length - 1
            sanitized = "${nameWithoutExt.take(maxNameLength)}.$extension"
        }

        // Ensure we have a valid filename
        if (sanitized.isBlank()) {
            sanitized = "audio_${System.currentTimeMillis()}.opus"
        }

        return sanitized
    }

    /**
     * Sanitize a folder name to be safe for filesystem use
     * Removes invalid characters and limits length
     *
     * @param name Original folder name (artist or album name)
     * @return Sanitized folder name
     */
    private fun sanitizeFolderName(name: String): String {
        if (name.isBlank()) return "Unknown"

        // Remove invalid characters for folder names
        var sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // Remove leading/trailing whitespace
        sanitized = sanitized.trim()

        // Limit folder name length (100 chars for compatibility)
        if (sanitized.length > 100) {
            sanitized = sanitized.take(100)
        }

        // Ensure we have a valid folder name
        return sanitized.ifBlank { "Unknown" }
    }

    /**
     * Get the public Music/Zemer folder path (for display purposes)
     * Note: On Android 10+, direct file access is restricted
     *
     * @return Folder path string
     */
    fun getZemerFolderPath(): String {
        return "${Environment.DIRECTORY_MUSIC}/$ZEMER_FOLDER"
    }
}
