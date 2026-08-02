package com.jtech.zemer.ui.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decode a poster frame from a local video `content://` uri for previewing it as a still (the grid
 * tiles, the paused viewer). Coil has no video decoder registered in this app, so we pull one frame via
 * [MediaMetadataRetriever] off the main thread; null until decoded (or on failure). Keyed on the uri so
 * each distinct video decodes once.
 */
@Composable
fun rememberVideoThumbnail(uri: String): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
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
    }.value
}
