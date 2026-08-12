package com.jtech.zemer.playback.queues

import android.content.Context
import androidx.media3.common.MediaItem
import com.jtech.zemer.di.zemerSearchRepository
import com.jtech.zemer.extensions.toMediaItem
import com.jtech.zemer.models.MediaMetadata
import com.jtech.zemer.search.zemerSearchOptions
import com.jtech.zemer.tracking.PlaySource
import com.jtech.zemer.utils.OfflineModeState
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

    private val repository = context.zemerSearchRepository()

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
         *
         * Manual offline mode: the radio fill is a network fetch that can only fail, so the SAME
         * factory returns a one-item [ListQueue] instead — the tapped (downloaded) song plays from
         * disk and nothing parks on the network wait. One branch here covers every tap site.
         */
        fun song(
            song: MediaMetadata,
            context: Context,
            playSource: String = PlaySource.OTHER,
        ): Queue =
            if (OfflineModeState.enabled) {
                ListQueue(title = song.title, items = listOf(song.toMediaItem()), playSource = playSource)
            } else {
                ZemerRadioQueue("song", song.id, context, playSource, seedSong = song)
            }

        /**
         * Endless genre radio (`/radio?kind=genre&seed=<slug>`) — what a genre page's Play button
         * starts (the browse tracklist is never played directly, per the genres handoff). No seed
         * song: the server opens on one of the genre's popular songs, varying per session — so
         * there are NO user-chosen context items and every play correctly reports as `radio`
         * (like Home's shuffle Radio mode). [PlaySource.genre] is deliberately not declared here —
         * with zero context items it would never be reported and only mislead readers; it belongs
         * to the tracklist row taps, which seed a song and carry it.
         */
        fun genre(
            slug: String,
            context: Context,
        ) = ZemerRadioQueue("genre", slug, context)
    }
}
