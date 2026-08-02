package com.jtech.zemer.statuses

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jtech.zemer.utils.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes saved statuses into the device gallery under a per-creator album tree
 * (`Pictures/Zemer/Status/<creator>` for images and text-as-image, `Movies/Zemer/Status/<creator>` for
 * videos) and deletes them again. Mirrors the MediaStore insert idiom the app already uses in
 * `ComposeToImage` / `MediaStoreHelper`; delete is delegated to [MediaStoreHelper.deleteFromMediaStore]
 * (which also handles SAF document uris). A gallery-media concern, separate from the song download system.
 */
object StatusGallery {
    private const val ZEMER = "Zemer"
    private const val STATUS = "Status"

    private fun picturesDir(creatorName: String) =
        "${Environment.DIRECTORY_PICTURES}/$ZEMER/$STATUS/${sanitizeCreatorForFile(creatorName)}"

    private fun moviesDir(creatorName: String) =
        "${Environment.DIRECTORY_MOVIES}/$ZEMER/$STATUS/${sanitizeCreatorForFile(creatorName)}"

    /** Save downloaded image bytes under the creator's images folder; [baseName] has no extension. */
    suspend fun saveImage(context: Context, bytes: ByteArray, mime: String, creatorName: String, baseName: String): Uri =
        write(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, picturesDir(creatorName), mime,
            imageExt(mime), baseName) { it.write(bytes) }

    /** Save downloaded video bytes under the creator's movies folder. */
    suspend fun saveVideo(context: Context, bytes: ByteArray, mime: String, creatorName: String, baseName: String): Uri =
        write(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, moviesDir(creatorName), mime,
            videoExt(mime), baseName) { it.write(bytes) }

    /** Save a rendered bitmap (a text status) as a PNG under the creator's images folder. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap, creatorName: String, baseName: String): Uri =
        write(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, picturesDir(creatorName), "image/png",
            "png", baseName) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Delete a previously saved gallery file. Fail-soft (returns false), reusing the shared helper. */
    suspend fun delete(context: Context, uri: Uri): Boolean =
        runCatching { MediaStoreHelper(context).deleteFromMediaStore(uri) }.getOrDefault(false)

    private suspend fun write(
        context: Context,
        collection: Uri,
        relativeDir: String,
        mime: String,
        ext: String,
        baseName: String,
        writeBody: (OutputStream) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = "$baseName.$ext"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pending = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, pending)
                ?: error("Failed to create MediaStore record for $displayName")
            resolver.openOutputStream(uri)?.use(writeBody)
                ?: error("Failed to open output stream for $displayName")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            uri
        } else {
            // Pre-Q (minSdk 26): write into the public album directory, then register it with MediaStore.
            val dir = File(Environment.getExternalStoragePublicDirectory(relativeDir.substringBefore('/')),
                relativeDir.substringAfter('/')).apply { mkdirs() }
            val file = File(dir, displayName)
            FileOutputStream(file).use(writeBody)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
            }
            resolver.insert(collection, values) ?: Uri.fromFile(file)
        }
    }

    private fun imageExt(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    private fun videoExt(mime: String): String = when (mime.lowercase()) {
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        else -> "mp4"
    }
}
