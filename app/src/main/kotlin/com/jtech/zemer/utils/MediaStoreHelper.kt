@file:Suppress("VariableNeverRead")

package com.jtech.zemer.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.jtech.zemer.constants.CustomDownloadPathKey
import com.jtech.zemer.utils.EnvironmentPaths.DEFAULT_RELATIVE_DOWNLOAD_PATH
import com.jtech.zemer.utils.EnvironmentPaths.toRelativePath
import com.jtech.zemer.utils.EnvironmentPaths.toStorageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
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

        // Supported video MIME types
        private val VIDEO_MIME_TYPE_MAP = mapOf(
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "3gp" to "video/3gpp"
        )
    }

    private val customDownloadPathKey = CustomDownloadPathKey

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
            val baseDownloadPath = getBaseDownloadPath()

            val relativePath = buildRelativePath(
                baseDownloadPath = baseDownloadPath,
                artist = artist,
                album = album,
            )

            // Check if file already exists and delete it to prevent duplicates
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val existingUri = findFileByPath(relativePath, sanitizedFileName)
                if (existingUri != null) {
                    contentResolver.delete(existingUri, null, null)
                }
            }

            // Prepare ContentValues with metadata
            // Organize files: Music/Zemer/{Artist}/{Album}/Song.mp3 or Music/Zemer/{Artist}/Song.mp3
            val targetFile = buildLegacyFile(relativePath, sanitizedFileName)

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
                } else {
                    // Ensure the custom directory exists for legacy devices
                    targetFile?.let { file ->
                        put(MediaStore.Audio.Media.DATA, file.absolutePath)
                        file.parentFile?.takeUnless { it.exists() }?.mkdirs()
                    }
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
     * Save a downloaded video file to MediaStore in the Movies/Zemer folder
     *
     * @param inputStream The video file data stream
     * @param fileName Desired filename (will be sanitized)
     * @param mimeType Video MIME type (e.g., "video/mp4")
     * @param title Video title for metadata
     * @param artist Artist name for metadata
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveVideoToMediaStore(
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        durationMs: Long? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sanitizedFileName = sanitizeFileName(fileName)
            val contentResolver = context.contentResolver

            // Videos go to Movies/Zemer/{Artist}/ folder
            val relativePath = "${Environment.DIRECTORY_MOVIES}/$ZEMER_FOLDER/${sanitizeFolderName(artist)}"
            Timber.d("saveVideoToMediaStore: relativePath=$relativePath, fileName=$sanitizedFileName, mimeType=$mimeType")

            // Check if file already exists and delete it to prevent duplicates
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val existingUri = findVideoByPath(relativePath, sanitizedFileName)
                if (existingUri != null) {
                    contentResolver.delete(existingUri, null, null)
                }
            }

            // Prepare ContentValues with metadata
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.TITLE, title)
                put(MediaStore.Video.Media.ARTIST, artist)
                durationMs?.let { put(MediaStore.Video.Media.DURATION, it) }

                // Set relative path for Android 10+ (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    // Legacy path for older Android versions
                    val targetDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "$ZEMER_FOLDER/${sanitizeFolderName(artist)}"
                    )
                    targetDir.mkdirs()
                    val targetFile = File(targetDir, sanitizedFileName)
                    put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                }
            }

            // Insert the file entry into MediaStore
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val videoUri = contentResolver.insert(videoCollection, contentValues)

            if (videoUri == null) {
                return@withContext null
            }

            // Write the actual file content
            contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            } ?: run {
                contentResolver.delete(videoUri, null, null)
                return@withContext null
            }

            // Mark file as ready (remove IS_PENDING flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(videoUri, contentValues, null, null)
            }

            videoUri

        } catch (e: Exception) {
            Timber.e(e, "saveVideoToMediaStore failed: ${e.message}")
            null
        }
    }

    /**
     * Find a video file in MediaStore by relative path and filename
     */
    private fun findVideoByPath(relativePath: String, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(relativePath, fileName)

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    Uri.withAppendedPath(
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        id.toString()
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save a video file from a temporary location to MediaStore
     *
     * @param tempFile Temporary file to move to MediaStore
     * @param fileName Desired filename
     * @param mimeType Video MIME type
     * @param title Video title
     * @param artist Artist name
     * @param durationMs Duration in milliseconds (optional)
     * @return Uri of the saved file, or null if save failed
     */
    suspend fun saveVideoFileToMediaStore(
        tempFile: File,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        durationMs: Long? = null
    ): Uri? = withContext(Dispatchers.IO) {
        Timber.d("saveVideoFileToMediaStore: fileName=$fileName, mimeType=$mimeType, tempFile=${tempFile.absolutePath}, size=${tempFile.length()}")
        try {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Timber.e("saveVideoFileToMediaStore: temp file doesn't exist or is empty")
                return@withContext null
            }

            tempFile.inputStream().use { inputStream ->
                saveVideoToMediaStore(
                    inputStream = inputStream,
                    fileName = fileName,
                    mimeType = mimeType,
                    title = title,
                    artist = artist,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "saveVideoFileToMediaStore failed: ${e.message}")
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

            val sanitizedFileName = sanitizeFileName(fileName)
            val sanitizedArtist = sanitizeFolderName(artist)
            val sanitizedAlbum = album?.takeIf { it.isNotBlank() }?.let { sanitizeFolderName(it) }

            // Only save to custom path if one is configured, otherwise use MediaStore
            val hasCustomPath = context.dataStore[customDownloadPathKey]?.isNotBlank() == true

            if (hasCustomPath) {
                // Save to custom path only
                saveToCustomPath(
                    tempFile = tempFile,
                    mimeType = mimeType,
                    sanitizedFileName = sanitizedFileName,
                    sanitizedArtist = sanitizedArtist,
                    sanitizedAlbum = sanitizedAlbum
                )
            } else {
                // Save to MediaStore (default Music/Zemer folder)
                tempFile.inputStream().use { inputStream ->
                    saveToMediaStore(
                        inputStream = inputStream,
                        fileName = sanitizedFileName,
                        mimeType = mimeType,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs
                    )
                }
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
                // Only check for files in Zemer folder to avoid false positives from other apps
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val projection = arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.RELATIVE_PATH
                    )

                    val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(title, artist, "%$ZEMER_FOLDER%")

                    context.contentResolver.query(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            // Verify the file has actual content (not an orphaned entry)
                            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                            val fileSize = cursor.getLong(sizeColumn)
                            if (fileSize <= 0) {
                                return@withContext null
                            }

                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                            val id = cursor.getLong(idColumn)
                            Uri.withAppendedPath(
                                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                                id.toString()
                            )
                        } else {
                            null
                        }
                    }
                } else {
                    // For older Android, check if file exists in Zemer folder
                    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val zemerDir = File(baseDir, ZEMER_FOLDER)
                    val artistDir = File(zemerDir, sanitizeFolderName(artist))

                    // Check common extensions
                    listOf("m4a", "opus", "mp3", "webm", "ogg").forEach { ext ->
                        val file = File(artistDir, "${sanitizeFileName("$artist - $title.$ext")}")
                        if (file.exists() && file.length() > 0) {
                            return@withContext Uri.fromFile(file)
                        }
                    }
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error finding existing file: $title by $artist")
                null
            }
        }

    /**
     * Find a file in MediaStore by relative path and filename
     */
    private fun findFileByPath(relativePath: String, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(relativePath, fileName)

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    Uri.withAppendedPath(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        id.toString()
                    )
                } else null
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
     * Get audio MIME type from file extension
     *
     * @param extension File extension (e.g., "opus", "m4a")
     * @return MIME type string, or "audio/mpeg" as default
     */
    fun getMimeType(extension: String): String {
        return MIME_TYPE_MAP[extension.lowercase()] ?: "audio/mpeg"
    }

    /**
     * Get video MIME type from file extension
     *
     * @param extension File extension (e.g., "mp4", "webm")
     * @return MIME type string, or "video/mp4" as default
     */
    fun getVideoMimeType(extension: String): String {
        return VIDEO_MIME_TYPE_MAP[extension.lowercase()] ?: "video/mp4"
    }

    private fun saveToCustomPath(
        tempFile: File,
        mimeType: String,
        sanitizedFileName: String,
        sanitizedArtist: String,
        sanitizedAlbum: String?,
    ): Uri? {
        val customDownloadUri = context.dataStore[customDownloadPathKey]
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: return null

        val rootDocument = DocumentFile.fromTreeUri(context, customDownloadUri) ?: return null

        val artistDir = ensureDirectory(rootDocument, sanitizedArtist) ?: return null
        val targetDir = sanitizedAlbum?.let { ensureDirectory(artistDir, it) } ?: artistDir
        targetDir.findFile(sanitizedFileName)?.delete()

        val targetFile = targetDir.createFile(mimeType, sanitizedFileName) ?: return null

        return try {
            context.contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
                targetFile.uri
            }
        } catch (e: Exception) {
            targetFile.delete()
            null
        }
    }

    private fun ensureDirectory(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.let { existing ->
            if (existing.isDirectory) return existing
            existing.delete()
        }

        return parent.createDirectory(name)
    }

    private fun getBaseDownloadPath(): String {
        return context.dataStore[customDownloadPathKey]
            ?.toRelativePath()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RELATIVE_DOWNLOAD_PATH
    }

    private fun buildRelativePath(
        baseDownloadPath: String,
        artist: String,
        album: String?,
    ): String {
        val sanitizedArtist = sanitizeFolderName(artist)
        val sanitizedAlbum = album?.takeIf { it.isNotBlank() }?.let { sanitizeFolderName(it) }
        val base = baseDownloadPath.trim('/').ifEmpty { DEFAULT_RELATIVE_DOWNLOAD_PATH }

        return if (sanitizedAlbum != null) {
            "$base/$sanitizedArtist/$sanitizedAlbum"
        } else {
            "$base/$sanitizedArtist"
        }
    }

    private fun buildLegacyFile(relativePath: String, fileName: String): File? {
        if (relativePath.isBlank()) return null
        val storageRoot = relativePath.toStorageRoot()
        return File(storageRoot, fileName)
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
        return DEFAULT_RELATIVE_DOWNLOAD_PATH
    }
}
