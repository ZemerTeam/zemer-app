package com.jtech.zemer.ui.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A small process-wide cache (bounded by bytes) so a poster decoded once - e.g. by a cube PREVIEW face -
// is reused INSTANTLY by the live face that takes over, with no null/black re-decode gap between them.
private val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * Decode a poster frame from a local video `content://` uri for previewing it as a still (the grid
 * tiles, the paused viewer). Coil has no video decoder registered in this app, so we pull one frame via
 * [MediaMetadataRetriever] off the main thread. Cached per uri: a uri already decoded returns its bitmap
 * as the initial value (no null frame), so a poster never flashes black when a second site shows the
 * same video; otherwise null until decoded (or on failure).
 */
@Composable
fun rememberVideoThumbnail(uri: String): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = thumbnailCache.get(uri), uri) {
        if (value != null) return@produceState // cache hit - already the initial value
        val bitmap = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri.toUri())
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
        if (bitmap != null) thumbnailCache.put(uri, bitmap)
        value = bitmap
    }.value
}
