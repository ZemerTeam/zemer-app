package com.jtech.zemer.statuses

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.jtech.zemer.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates saving a status to the device gallery and recording it in [StatusDownloadsStore]: fetch
 * the media bytes (image/video) or take a caller-rendered text bitmap, write it under `Zemer/Status`
 * ([StatusGallery]), then index it. Removal deletes the gallery file first, then drops the record. All
 * off the main thread; fail-soft (a failed save reports and returns a failure Result, never crashes).
 */
@Singleton
class StatusDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: StatusDownloadsStore,
) {
    private val http by lazy { OkHttpClient() }

    val downloads: Flow<List<StatusDownload>> = store.downloads
    val savedIds: Flow<Set<String>> = store.savedIds

    /**
     * Save [post] (belonging to [creator]) to the gallery and index it. For a text status the caller
     * supplies [renderTextBitmap] (a theme-colored render), which is only invoked for `kind == "text"`.
     * Idempotent-ish: re-saving overwrites the index entry; callers gate on "already saved" first.
     */
    suspend fun save(
        post: StatusPost,
        creator: StatusCreator,
        renderTextBitmap: (() -> Bitmap)? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<StatusDownload> = runCatching {
        val creatorName = creator.displayName
        val baseName = statusDownloadStamp(post.postedAt)
        val uri: Uri = when (post.kind) {
            "video" -> {
                val (bytes, mime) = fetch(statusMediaUrl(post.mediaPath), onProgress)
                StatusGallery.saveVideo(context, bytes, mime.ifBlank { "video/mp4" }, creatorName, baseName)
            }
            "image" -> {
                val (bytes, mime) = fetch(statusMediaUrl(post.mediaPath), onProgress)
                StatusGallery.saveImage(context, bytes, mime.ifBlank { "image/jpeg" }, creatorName, baseName)
            }
            "text" -> {
                val bitmap = renderTextBitmap?.invoke()
                    ?: error("text status save requires a rendered bitmap")
                StatusGallery.saveBitmap(context, bitmap, creatorName, baseName)
            }
            else -> error("unsupported status kind ${post.kind}")
        }
        StatusDownload(
            id = post.id,
            kind = post.kind,
            creatorId = creator.id,
            creatorName = creator.displayName,
            creatorAvatar = creator.avatarPath,
            postedAt = post.postedAt,
            caption = post.caption,
            textBody = post.textBody,
            mediaUri = uri.toString(),
            savedAt = System.currentTimeMillis(),
        ).also { store.add(it) }
    }.onFailure { reportException(it) }

    /** Delete the saved gallery file and drop the index record. */
    suspend fun remove(download: StatusDownload) {
        runCatching {
            StatusGallery.delete(context, download.mediaUri.toUri())
        }.onFailure { reportException(it) }
        store.remove(download.id)
    }

    private suspend fun fetch(
        url: String?,
        onProgress: (Float) -> Unit = {},
    ): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        requireNotNull(url) { "status media url was null" }
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            require(response.isSuccessful) { "status media fetch failed: HTTP ${response.code}" }
            val body = response.body ?: error("empty status media body")
            val mime = body.contentType()?.toString()?.substringBefore(';')?.trim().orEmpty()
            val total = body.contentLength() // -1 if the server didn't send Content-Length
            val out = ByteArrayOutputStream(if (total > 0) total.toInt() else 32 * 1024)
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var read = 0L
                var lastReported = 0f
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total > 0) {
                        val fraction = (read.toFloat() / total).coerceIn(0f, 1f)
                        // Throttle to ~1% steps so a multi-MB file doesn't spam the UI callback.
                        if (fraction - lastReported >= 0.01f || fraction >= 1f) {
                            lastReported = fraction
                            onProgress(fraction)
                        }
                    }
                }
            }
            out.toByteArray() to mime
        }
    }
}
