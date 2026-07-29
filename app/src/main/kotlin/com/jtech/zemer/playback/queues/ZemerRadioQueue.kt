package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.di.ZemerSearchRepositoryEntryPoint
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
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
 *
 * Built from just a [Context] (repository resolved via [ZemerSearchRepositoryEntryPoint]) so it works from
 * ViewModels and leaf menu composables alike.
 */
class ZemerRadioQueue(
    private val kind: String,
    private val seed: String?,
    context: Context,
    override val playSource: String = PlaySource.OTHER,
    // Single-song tap: the tapped song plays immediately (preload) and heads the queue, with the
    // /radio?kind=song fill following it. Null for the endless artist/album/playlist/shuffle stations.
    private val seedSong: MediaMetadata? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = seedSong
    override val initialItemsAreContext: Boolean = false
    override val continuationIsContext: Boolean = false

    // MusicService retains currentQueue for the whole playback session; callers hand in whatever
    // Context they have (often the Activity), so only the application context may be held.
    private val context = context.applicationContext

    private val repository = EntryPointAccessors
        .fromApplication(context.applicationContext, ZemerSearchRepositoryEntryPoint::class.java)
        .zemerSearchRepository()

    private var continuation: String? = null
    private var started = false

    // The seed page completed exceptionally (playQueue surfaced the failure to the user). Lets
    // [nextPage] retry it on a later transition — without it a preloaded tap whose fill fetch
    // failed stayed a one-song queue forever (nextPage returned empty on the null continuation).
    // Set only AFTER the initial fetch completes, so a retry can never run concurrently with it.
    @Volatile
    private var initialFailed = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val page = try {
            repository.radio(kind, seed, zemerSearchOptions(context))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            initialFailed = true
            throw e
        }
        continuation = page.continuation
        started = true
        // Seed-first: the tapped song (already preloading) heads the queue at index 0 and the fill
        // follows, deduped so the seed can't appear twice. MusicService splices the fill around the
        // preloaded seed at [mediaItemIndex]. Stations (no seedSong) are pure fill.
        val items = if (seedSong != null) {
            listOf(seedSong.toMediaItem()) +
                page.songs.filterNot { it.id == seedSong.id }.map { it.toMediaItem() }
        } else {
            page.songs.map { it.toMediaItem() }
        }
        Queue.Status(
            title = null,
            items = items,
            mediaItemIndex = 0,
        )
    }

    override fun hasNextPage(): Boolean = !started || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (initialFailed) {
            // Retry the failed seed page (see [initialFailed]) so the radio can still start once the
            // network recovers; the seed is excluded — it is already playing at index 0.
            val page = repository.radio(kind, seed, zemerSearchOptions(context))
            initialFailed = false
            started = true
            continuation = page.continuation
            return@withContext page.songs.filterNot { it.id == seedSong?.id }.map { it.toMediaItem() }
        }
        val token = continuation ?: return@withContext emptyList()
        val page = repository.radioContinuation(token)
        continuation = page.continuation
        page.songs.map { it.toMediaItem() }
    }

    companion object {
        /**
         * Seed-first song radio for a single-song tap: the tapped [song] plays immediately and the
         * queue continues with `/radio?kind=song` seeded by it. Corpus-native replacement for
         * `YouTubeQueue.radio(song)`; the tapped song is the chosen play, the fill reports as radio.
         */
        fun song(
            song: MediaMetadata,
            context: Context,
            playSource: String = PlaySource.OTHER,
        ) = ZemerRadioQueue("song", song.id, context, playSource, seedSong = song)
    }
}
