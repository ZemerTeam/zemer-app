package com.jtech.zemer.recognition

import com.jtech.zemer.db.MusicDatabase
import com.jtech.zemer.db.entities.RecognitionHistoryEntity
import com.jtech.zemer.utils.ContentFilterState
import com.jtech.zemer.utils.filterWhitelisted
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTube.SearchFilter
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The single, shared "recognized song → whitelisted result" bridge used by every recognition
 * surface (the in-app screen and the home-screen widget). Centralizing it here guarantees all
 * surfaces are held to the same rule: a recognized song can only ever surface if it resolves to a
 * whitelisted artist.
 *
 * Two independent gates make a non-whitelisted result impossible:
 *  1. [filterWhitelisted] with content filtering FORCED on (ignores the global content-filter
 *     toggle), so candidates are whitelisted even if the user disabled filtering app-wide.
 *  2. A hard, config-independent re-check of the chosen song against the `artist_whitelist` table
 *     ([RecognitionMatchSelector.isWhitelistedResult]) — fails closed.
 *
 * On a confirmed match it also records the (whitelisted) song to recognition history.
 */
object RecognitionResolver {
    private const val TAG = "RecognitionResolver"

    sealed interface Outcome {
        data class Resolved(val song: SongItem) : Outcome
        data object NoMatch : Outcome
        data object Error : Outcome
    }

    suspend fun resolveWhitelisted(
        database: MusicDatabase,
        title: String,
        artist: String,
    ): Outcome = withContext(Dispatchers.IO) {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.isBlank()) return@withContext Outcome.NoMatch

        val searchResult = YouTube.search(query, SearchFilter.FILTER_SONG).getOrElse { error ->
            Timber.tag(TAG).w(error, "YouTube search failed for recognized track")
            return@withContext Outcome.Error
        }

        // Gate 1: force whitelist filtering on regardless of the global content-filter toggle.
        val forcedConfig = ContentFilterState.current.copy(filtersEnabled = true)
        val candidates = searchResult.items
            .filterWhitelisted(database, forcedConfig)
            .filterIsInstance<SongItem>()

        val match = RecognitionMatchSelector.select(title, artist, candidates)
            ?: return@withContext Outcome.NoMatch

        // Gate 2: hard, config-independent re-check straight against the whitelist table.
        val confirmed = RecognitionMatchSelector.isWhitelistedResult(match) { artistId ->
            database.isArtistWhitelisted(artistId)
        }
        if (!confirmed) {
            Timber.tag(TAG).w("Discarding result whose artist is not whitelisted: songId=%s", match.id)
            return@withContext Outcome.NoMatch
        }

        recordHistory(database, match)
        Outcome.Resolved(match)
    }

    /** Records the resolved (whitelisted) song to history, most-recent first, de-duplicated by song.
     *  Never throws — a history failure must not break recognition. */
    private suspend fun recordHistory(database: MusicDatabase, song: SongItem) {
        runCatching {
            database.deleteRecognitionHistoryBySong(song.id)
            database.insertRecognitionHistory(
                RecognitionHistoryEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.thumbnail,
                    artistIds = RecognitionHistoryFilter.joinIds(song.artists.map { it.id }),
                ),
            )
        }.onFailure { Timber.tag(TAG).w(it, "Failed to record recognition history") }
    }
}
