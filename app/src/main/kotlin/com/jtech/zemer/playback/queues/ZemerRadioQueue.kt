package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.search.ZemerSearchRepository
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/**
 * Corpus-native radio (Zemer `/radio`): an endless, whitelist-pure continuation queue seeded by an
 * artist / album / song, or `kind=shuffle` (no seed) for the Home "Radio mode". It replaces
 * `YouTube.next()` for SELECTION only — the audio stream still comes from InnerTube + the cipher.
 *
 * The server's `continuation` is an opaque token (it encodes the seed + flags + position), so this queue
 * keeps no cursor state: [getInitialStatus] pulls the first page and stashes the token, [nextPage] echoes
 * it back for the next slice. Radio items are autoplay fill, so they report as "radio" (tracking §3.3).
 */
class ZemerRadioQueue(
    private val kind: String,
    private val seed: String?,
    private val context: Context,
    private val repository: ZemerSearchRepository,
    override val playSource: String = PlaySource.OTHER,
) : Queue {
    override val preloadItem: MediaMetadata? = null
    override val initialItemsAreContext: Boolean = false
    override val continuationIsContext: Boolean = false

    private var continuation: String? = null
    private var started = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val page = repository.radio(kind, seed, zemerSearchOptions(context))
        continuation = page.continuation
        started = true
        Queue.Status(
            title = null,
            items = page.songs.map { it.toMediaItem() },
            mediaItemIndex = 0,
        )
    }

    override fun hasNextPage(): Boolean = !started || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        val token = continuation ?: return@withContext emptyList()
        val page = repository.radioContinuation(token)
        continuation = page.continuation
        page.songs.map { it.toMediaItem() }
    }
}
