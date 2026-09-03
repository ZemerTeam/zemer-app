package com.jtech.zemer.lyrics

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.LyricsEntity
import com.jtech.zemer.models.MediaMetadata
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE fetch-and-persist path for a song's lyrics: the cache decision (`LyricsEntity.needsFetch`), the
 * provider chain, and the row policy (`LyricsEntity.resolved`) live here, so the service prefetch, the lyrics
 * screen's open-time fetch and the menu's Refetch cannot drift (they were three hand-rolled copies with three
 * different policies). Episodes have no lyrics and never store a row. Storage is injected so the class is
 * JVM-tested; the Hilt constructor binds it to Room + [LyricsHelper].
 */
@Singleton
class LyricsStore(
    private val cached: suspend (videoId: String) -> LyricsEntity?,
    private val persist: (LyricsEntity) -> Unit,
    private val fetch: suspend (MediaMetadata) -> LyricsHelper.Fetched,
) {
    @Inject
    constructor(database: MusicDatabase, helper: LyricsHelper) : this(
        cached = { database.lyrics(it).first() },
        persist = { row -> database.query { upsert(row) } },
        fetch = helper::getLyrics,
    )

    /** Run the chain only when the cache says so (nothing cached, or a legacy row re-resolved once). Returns true when it fetched. */
    suspend fun ensure(mediaMetadata: MediaMetadata): Boolean {
        if (mediaMetadata.isEpisode) return false
        val row = cached(mediaMetadata.id)
        if (!LyricsEntity.needsFetch(row)) return false
        val fetched = fetch(mediaMetadata)
        Timber.d("LyricsStore ensure %s -> %s", mediaMetadata.id, fetched.provider)
        persist(LyricsEntity.resolved(mediaMetadata.id, row, fetched.lyrics, fetched.provider))
        return true
    }

    /** The menu's Refetch: an explicit user request replaces whatever is cached (manual text included) with a fresh chain answer. */
    suspend fun refetch(mediaMetadata: MediaMetadata) {
        if (mediaMetadata.isEpisode) return
        Timber.d("LyricsStore refetch %s", mediaMetadata.id)
        val fetched = fetch(mediaMetadata)
        Timber.d("LyricsStore refetch %s -> %s", mediaMetadata.id, fetched.provider)
        persist(LyricsEntity.resolved(mediaMetadata.id, null, fetched.lyrics, fetched.provider))
    }
}
