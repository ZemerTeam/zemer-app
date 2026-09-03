package com.jtech.zemer.lyrics

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE fetch-and-persist path for a song's lyrics: the cache decision (`LyricsEntity.needsFetch`), the
 * provider chain, and the row policy (`LyricsEntity.resolved`) live here, so the service prefetch, the lyrics
 * screen's open-time fetch and the menu's Refetch cannot drift (they were three hand-rolled copies with three
 * different policies). Episodes have no lyrics and never store a row. Storage is injected so the class is
 * JVM-tested; the Hilt constructor binds it to Room + [LyricsHelper].
 *
 * Fetches are SINGLE-FLIGHT per videoId: [refetch] deletes the row first (the pane clears and reloads, the
 * user's feedback that the button did something), which makes the open lyrics screen call [ensure] too; both
 * join one chain walk instead of two.
 */
@Singleton
class LyricsStore(
    private val cached: suspend (videoId: String) -> LyricsEntity?,
    private val persist: (LyricsEntity) -> Unit,
    private val delete: (LyricsEntity) -> Unit,
    private val fetch: suspend (MediaMetadata) -> LyricsHelper.Fetched,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    @Inject
    constructor(database: MusicDatabase, helper: LyricsHelper) : this(
        cached = { database.lyrics(it).first() },
        persist = { row -> database.query { upsert(row) } },
        delete = { row -> database.query { delete(row) } },
        fetch = helper::getLyrics,
    )

    private val inFlightLock = Mutex()
    private val inFlight = HashMap<String, Deferred<LyricsHelper.Fetched>>()

    /** Run the chain only when the cache says so (nothing cached, or a legacy row re-resolved once). Returns true when it fetched. */
    suspend fun ensure(mediaMetadata: MediaMetadata): Boolean {
        if (mediaMetadata.isEpisode) return false
        val row = cached(mediaMetadata.id)
        if (!LyricsEntity.needsFetch(row)) return false
        val fetched = fetchShared(mediaMetadata)
        Timber.d("LyricsStore ensure %s -> %s", mediaMetadata.id, fetched.provider)
        persist(LyricsEntity.resolved(mediaMetadata.id, row, fetched.lyrics, fetched.provider))
        return true
    }

    /**
     * The menu's Refetch: an explicit user request drops whatever is cached (manual text included) and stores
     * a fresh chain answer. The delete lands first so the pane visibly reloads.
     */
    suspend fun refetch(mediaMetadata: MediaMetadata) {
        if (mediaMetadata.isEpisode) return
        cached(mediaMetadata.id)?.let(delete)
        Timber.d("LyricsStore refetch %s", mediaMetadata.id)
        val fetched = fetchShared(mediaMetadata)
        Timber.d("LyricsStore refetch %s -> %s", mediaMetadata.id, fetched.provider)
        persist(LyricsEntity.resolved(mediaMetadata.id, null, fetched.lyrics, fetched.provider))
    }

    private suspend fun fetchShared(mediaMetadata: MediaMetadata): LyricsHelper.Fetched {
        val id = mediaMetadata.id
        val deferred = inFlightLock.withLock {
            inFlight.getOrPut(id) {
                scope.async {
                    try {
                        fetch(mediaMetadata)
                    } finally {
                        inFlightLock.withLock { inFlight.remove(id) }
                    }
                }
            }
        }
        return deferred.await()
    }
}
