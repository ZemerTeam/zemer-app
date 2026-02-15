@file:Suppress("VariableNeverRead")

package com.jtech.zemer.utils

import android.content.ContentUris
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
        // Note: audio/webm is not supported by MediaStore on many devices
        // Use audio/ogg for webm/opus files which is more widely accepted
        private val MIME_TYPE_MAP = mapOf(
            "opus" to "audio/ogg",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "webm" to "audio/ogg",  // MediaStore doesn't accept audio/webm on many devices
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

            // Get MediaStore-compatible MIME type based on extension
            // MediaStore rejects some MIME types like audio/webm, so we map them
            val extension = sanitizedFileName.substringAfterLast('.', "mp3")
            val mediaStoreMimeType = getMimeType(extension)

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mediaStoreMimeType)
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

            Timber.tag("MediaStore").d("Inserting into MediaStore: fileName=$sanitizedFileName, mimeType=$mediaStoreMimeType (from $mimeType), relativePath=$relativePath")
            val audioUri = contentResolver.insert(audioCollection, contentValues)

            if (audioUri == null) {
                Timber.tag("MediaStore").e("MediaStore insert returned null for $sanitizedFileName (mimeType=$mediaStoreMimeType from $mimeType)")
                return@withContext null
            }
            Timber.tag("MediaStore").d("Insert succeeded: $audioUri")

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
            Timber.tag("MediaStore").e(e, "saveToMediaStore failed: ${e.message}")
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
            Timber.tag("MediaStore").d("saveFileToMediaStore: fileName=$sanitizedFileName, mimeType=$mimeType, hasCustomPath=$hasCustomPath, fileSize=$fileSize")

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
            Timber.tag("MediaStore").e(e, "saveFileToMediaStore failed: ${e.message}")
            null
        }
    }

    /**
     * Save cover art image to the same folder as audio files.
     * Used for formats that don't support embedded artwork (e.g., WebM/OPUS).
     *
     * @param tempFile The temporary image file
     * @param fileName Desired filename (e.g., "Song Title.jpg")
     * @param mimeType Image MIME type (image/jpeg or image/png)
     * @param artist Artist name (used for folder organization)
     * @param album Album name (optional, used for folder organization)
     * @return Uri of saved file, or null on failure
     */
    suspend fun saveCoverArtToMediaStore(
        tempFile: File,
        fileName: String,
        mimeType: String,
        artist: String,
        album: String?
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext null
            }

            val sanitizedFileName = sanitizeFileName(fileName)
            val sanitizedArtist = sanitizeFolderName(artist)
            val sanitizedAlbum = album?.takeIf { it.isNotBlank() }?.let { sanitizeFolderName(it) }

            // Check for custom download path
            val hasCustomPath = context.dataStore[customDownloadPathKey]?.isNotBlank() == true

            if (hasCustomPath) {
                // Save to custom path alongside audio
                saveCoverToCustomPath(tempFile, sanitizedFileName, sanitizedArtist, sanitizedAlbum)
            } else {
                // Save to Pictures/Zemer/{Artist}/{Album}/ for better organization
                saveCoverToMediaStorePictures(tempFile, sanitizedFileName, mimeType, sanitizedArtist, sanitizedAlbum)
            }
        } catch (e: Exception) {
            Timber.tag("MediaStore").e(e, "saveCoverArtToMediaStore failed: ${e.message}")
            null
        }
    }

    private suspend fun saveCoverToMediaStorePictures(
        tempFile: File,
        fileName: String,
        mimeType: String,
        artist: String,
        album: String?
    ): Uri? = withContext(Dispatchers.IO) {
        // Save cover art in the same folder as audio files (Music/Zemer/Artist/Album)
        // Use song title as filename so each track has its own cover
        val baseDownloadPath = getBaseDownloadPath()
        val relativePath = buildRelativePath(baseDownloadPath, artist, album)

        // Use sanitized song title for cover filename (e.g., "SongTitle.jpg")
        val sanitizedFileName = sanitizeFileName(fileName)

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sanitizedFileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageUri = context.contentResolver.insert(imageCollection, contentValues)
            ?: return@withContext null

        context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
            tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(imageUri, contentValues, null, null)
        }

        Timber.tag("MediaStore").d("Cover art saved: $imageUri ($sanitizedFileName) in $relativePath")
        imageUri
    }

    private fun saveCoverToCustomPath(
        tempFile: File,
        fileName: String,
        artist: String,
        album: String?
    ): Uri? {
        val customDownloadUri = context.dataStore[customDownloadPathKey]
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: return null

        val rootDocument = DocumentFile.fromTreeUri(context, customDownloadUri) ?: return null

        // Navigate to or create artist folder
        val artistFolder = rootDocument.findFile(artist)
            ?: rootDocument.createDirectory(artist)
            ?: return null

        // Navigate to or create album folder if specified
        val targetFolder = if (album != null) {
            artistFolder.findFile(album) ?: artistFolder.createDirectory(album) ?: artistFolder
        } else {
            artistFolder
        }

        // Delete existing file with same name
        targetFolder.findFile(fileName)?.delete()

        // Create new file
        val mimeType = if (fileName.endsWith(".jpg", true)) "image/jpeg" else "image/png"
        val newFile = targetFolder.createFile(mimeType, fileName) ?: return null

        context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
            tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        Timber.tag("MediaStore").d("Cover art saved to custom path: ${newFile.uri}")
        return newFile.uri
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
     * Find and delete ALL audio files matching a title in the artist folder (any extension).
     * This handles the case where a song was downloaded with different formats over time
     * (e.g., first as .opus, then re-downloaded as .m4a).
     *
     * @param title The song title (without extension)
     * @param artist The artist name (used for folder path)
     * @param album Optional album name (used for folder path if present)
     * @return Number of files deleted
     */
    suspend fun deleteAllVariantsByTitle(title: String, artist: String, album: String? = null): Int = withContext(Dispatchers.IO) {
        var totalDeleted = 0

        try {
            val sanitizedTitle = sanitizeFileName(title)
            val sanitizedArtist = sanitizeFolderName(artist)

            Timber.tag("MediaStoreDelete").i("=== DELETE VARIANTS START: title='$title' -> sanitized='$sanitizedTitle' ===")
            Timber.tag("MediaStoreDelete").d("Artist: '$artist' -> '$sanitizedArtist', Album: $album")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Build the expected relative path
                val baseDownloadPath = getBaseDownloadPath()
                val relativePathBase = buildRelativePath(
                    baseDownloadPath = baseDownloadPath,
                    artist = artist,
                    album = album
                )
                // CRITICAL: MediaStore stores RELATIVE_PATH with trailing slash!
                val relativePath = if (relativePathBase.endsWith("/")) relativePathBase else "$relativePathBase/"

                // Find all files in this folder that start with the title
                // Use LIKE query to match "Title.%" (any extension)
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH
                )
                // Match: exact path AND filename starts with title followed by a dot
                val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf(relativePath, "$sanitizedTitle.%")

                Timber.tag("MediaStoreDelete").i("Query: RELATIVE_PATH='$relativePath' AND DISPLAY_NAME LIKE '$sanitizedTitle.%'")

                // First, try exact path match
                var foundAny = false
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    Timber.tag("MediaStoreDelete").d("Exact path query returned ${cursor.count} results")
                    while (cursor.moveToNext()) {
                        foundAny = true
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                        val actualPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))

                        Timber.tag("MediaStoreDelete").d("Found file: '$displayName' at path='$actualPath'")

                        val uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                            id.toString()
                        )

                        try {
                            val deleted = context.contentResolver.delete(uri, null, null)
                            if (deleted > 0) {
                                totalDeleted++
                                Timber.tag("MediaStoreDelete").i("Deleted variant: $displayName (uri=$uri)")
                            } else {
                                Timber.tag("MediaStoreDelete").w("Failed to delete variant: $displayName")
                            }
                        } catch (e: Exception) {
                            Timber.tag("MediaStoreDelete").e("Error deleting variant $displayName: ${e.message}")
                        }
                    }
                }

                // If exact path didn't find anything, try broader search within Zemer folder
                if (!foundAny) {
                    Timber.tag("MediaStoreDelete").w("No files found with exact path, trying broader search in Zemer folder")

                    // Search anywhere in the Zemer folder structure
                    val broadSelection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
                    val broadSelectionArgs = arrayOf("%$ZEMER_FOLDER%$sanitizedArtist%", "$sanitizedTitle.%")

                    Timber.tag("MediaStoreDelete").d("Broad query: RELATIVE_PATH LIKE '%$ZEMER_FOLDER%$sanitizedArtist%' AND DISPLAY_NAME LIKE '$sanitizedTitle.%'")

                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        broadSelection,
                        broadSelectionArgs,
                        null
                    )?.use { cursor ->
                        Timber.tag("MediaStoreDelete").d("Broad query returned ${cursor.count} results")
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                            val actualPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))

                            Timber.tag("MediaStoreDelete").d("Broad search found: '$displayName' at path='$actualPath'")

                            val uri = Uri.withAppendedPath(
                                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                                id.toString()
                            )

                            try {
                                val deleted = context.contentResolver.delete(uri, null, null)
                                if (deleted > 0) {
                                    totalDeleted++
                                    Timber.tag("MediaStoreDelete").i("Deleted variant (broad): $displayName (uri=$uri)")
                                } else {
                                    Timber.tag("MediaStoreDelete").w("Failed to delete variant (broad): $displayName")
                                }
                            } catch (e: Exception) {
                                Timber.tag("MediaStoreDelete").e("Error deleting variant $displayName: ${e.message}")
                            }
                        }
                    }
                }

                // Also check for cover art files (use same relativePath with trailing slash)
                val coverPattern = "$sanitizedTitle.%"  // Cover files might be title.jpg, title.png, etc.
                Timber.tag("MediaStoreDelete").d("Searching for cover art: path='$relativePath', pattern='$coverPattern'")

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH),
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                    arrayOf(relativePath, coverPattern),
                    null
                )?.use { cursor ->
                    Timber.tag("MediaStoreDelete").d("Cover art query returned ${cursor.count} results")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))

                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                            id.toString()
                        )

                        try {
                            val deleted = context.contentResolver.delete(uri, null, null)
                            if (deleted > 0) {
                                totalDeleted++
                                Timber.tag("MediaStoreDelete").i("Deleted cover art: $displayName")
                            }
                        } catch (e: Exception) {
                            Timber.tag("MediaStoreDelete").e("Error deleting cover art $displayName: ${e.message}")
                        }
                    }
                }
            } else {
                // Legacy path for Android 9 and below
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val zemerDir = File(baseDir, ZEMER_FOLDER)
                val artistDir = File(zemerDir, sanitizedArtist)
                val targetDir = if (album != null) File(artistDir, sanitizeFolderName(album)) else artistDir

                if (targetDir.exists()) {
                    targetDir.listFiles()?.forEach { file ->
                        if (file.nameWithoutExtension == sanitizedTitle) {
                            if (file.delete()) {
                                totalDeleted++
                                Timber.tag("MediaStoreDelete").i("Deleted legacy file: ${file.name}")
                            }
                        }
                    }
                }
            }

            Timber.tag("MediaStoreDelete").i("=== DELETE VARIANTS END: deleted $totalDeleted files for '$sanitizedTitle' by '$sanitizedArtist' ===")
        } catch (e: Exception) {
            Timber.tag("MediaStoreDelete").e(e, "Error in deleteAllVariantsByTitle: ${e.message}")
        }

        totalDeleted
    }

    /**
     * Delete a file from MediaStore
     *
     * @param uri Uri of the file to delete
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteFromMediaStore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.tag("MediaStoreDelete").d("Attempting to delete: $uri")

            // First check if the file exists
            val exists = try {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                    it.moveToFirst()
                } ?: false
            } catch (e: Exception) {
                Timber.tag("MediaStoreDelete").w("URI query failed: ${e.message}")
                false
            }

            if (!exists) {
                Timber.tag("MediaStoreDelete").w("File does not exist at $uri, nothing to delete")
                return@withContext true // Consider it successfully deleted if it doesn't exist
            }

            val deleted = context.contentResolver.delete(uri, null, null)
            Timber.tag("MediaStoreDelete").i("Delete result for $uri: rowsDeleted=$deleted")

            if (deleted == 0) {
                // On Android 10+, we may not have permission to delete files we didn't create
                // Try to get more info about why the delete failed
                Timber.tag("MediaStoreDelete").w("Delete returned 0 rows - file may be owned by another app or protected")
            }

            deleted > 0
        } catch (e: android.app.RecoverableSecurityException) {
            // On Android 10+, this exception indicates we need user permission to delete
            Timber.tag("MediaStoreDelete").e("RecoverableSecurityException - need user permission to delete: ${e.message}")
            false
        } catch (e: SecurityException) {
            Timber.tag("MediaStoreDelete").e("SecurityException deleting file: ${e.message}")
            false
        } catch (e: Exception) {
            Timber.tag("MediaStoreDelete").e(e, "Exception deleting file: ${e.message}")
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
