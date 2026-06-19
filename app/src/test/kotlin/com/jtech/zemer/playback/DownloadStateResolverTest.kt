package com.jtech.zemer.playback

import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState
import com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the one true download-state rule used by every badge/menu/header. The bug this guards
 * against: surfaces that read only the live in-session map show "not downloaded" after a restart even
 * though the persisted flag says the file is on disk — so [forSong] must treat the persisted flag as
 * authoritative for DOWNLOADED.
 */
class DownloadStateResolverTest {

    private fun live(status: Status, progress: Float = 0f) =
        DownloadState(songId = "x", status = status, progress = progress)

    private fun song(id: String, isDownloaded: Boolean) =
        Song(song = SongEntity(id = id, title = id, isDownloaded = isDownloaded), artists = emptyList())

    @Test
    fun persistedFlag_aloneMeansDownloaded_evenWithNoLiveState() {
        // The restart case: in-memory map is empty, but the DB says downloaded.
        assertEquals(DownloadStatus.DOWNLOADED, DownloadStateResolver.forSong(isDownloaded = true, live = null))
    }

    @Test
    fun liveCompleted_meansDownloaded_evenIfFlagNotYetPersisted() {
        assertEquals(
            DownloadStatus.DOWNLOADED,
            DownloadStateResolver.forSong(isDownloaded = false, live = live(Status.COMPLETED)),
        )
    }

    @Test
    fun queuedOrDownloading_meansDownloading() {
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStateResolver.forSong(false, live(Status.QUEUED)))
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStateResolver.forSong(false, live(Status.DOWNLOADING)))
    }

    @Test
    fun failedOrCancelledOrNone_meansNotDownloaded() {
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.forSong(false, null))
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.forSong(false, live(Status.FAILED)))
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.forSong(false, live(Status.CANCELLED)))
    }

    @Test
    fun aggregate_allDownloaded_isDownloaded() {
        assertEquals(
            DownloadStatus.DOWNLOADED,
            DownloadStateResolver.aggregate(listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADED)),
        )
    }

    @Test
    fun aggregate_anyNotDownloaded_isNotDownloaded() {
        assertEquals(
            DownloadStatus.NOT_DOWNLOADED,
            DownloadStateResolver.aggregate(
                listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.NOT_DOWNLOADED),
            ),
        )
    }

    @Test
    fun aggregate_mixOfDownloadedAndInProgress_isDownloading() {
        assertEquals(
            DownloadStatus.DOWNLOADING,
            DownloadStateResolver.aggregate(listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING)),
        )
    }

    @Test
    fun aggregate_empty_isNotDownloaded() {
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.aggregate(emptyList()))
    }

    @Test
    fun aggregateSongs_persistedAlbumStaysDownloadedAfterRestart() {
        // Two songs flagged downloaded in DB, live map empty (fresh launch) -> album reads DOWNLOADED.
        val songs = listOf(song("a", true), song("b", true))
        assertEquals(DownloadStatus.DOWNLOADED, DownloadStateResolver.aggregateSongs(songs, emptyMap()))
    }

    @Test
    fun aggregateProgress_averagesDownloadedAsOneAndInProgressByFraction() {
        val songs = listOf(song("a", true), song("b", false))
        val live = mapOf("b" to live(Status.DOWNLOADING, progress = 0.5f))
        // (1.0 + 0.5) / 2
        assertEquals(0.75f, DownloadStateResolver.aggregateProgress(songs, live), 0.0001f)
    }

    @Test
    fun aggregateProgress_empty_isZero() {
        assertEquals(0f, DownloadStateResolver.aggregateProgress(emptyList(), emptyMap()), 0.0001f)
    }

    @Test
    fun aggregateProgress_allDownloaded_isOne() {
        val songs = listOf(song("a", true), song("b", true))
        assertEquals(1f, DownloadStateResolver.aggregateProgress(songs, emptyMap()), 0.0001f)
    }

    @Test
    fun aggregateProgress_noneDownloaded_isZero() {
        val songs = listOf(song("a", false), song("b", false))
        assertEquals(0f, DownloadStateResolver.aggregateProgress(songs, emptyMap()), 0.0001f)
    }

    @Test
    fun aggregateSongs_failedMemberMakesCollectionNotDownloaded() {
        // A FAILED song counts as NOT_DOWNLOADED for the aggregate (resolver maps FAILED -> not).
        val songs = listOf(song("a", true), song("b", false))
        val live = mapOf("b" to live(Status.FAILED))
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.aggregateSongs(songs, live))
    }

    @Test
    fun aggregateSongs_downloadedPlusDownloading_isDownloading() {
        val songs = listOf(song("a", true), song("b", false))
        val live = mapOf("b" to live(Status.DOWNLOADING, progress = 0.3f))
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStateResolver.aggregateSongs(songs, live))
    }

    @Test
    fun forSong_failedButPersistedDownloaded_staysDownloaded() {
        // Persisted flag wins over a stale live FAILED (the file is on disk).
        assertEquals(DownloadStatus.DOWNLOADED, DownloadStateResolver.forSong(isDownloaded = true, live = live(Status.FAILED)))
    }

    // ---- id-based (online album/playlist menus) ----

    @Test
    fun aggregateByIds_showsDownloadingFromLiveMapWithoutDbEntities() {
        // The bug: online playlist progress was computed over an empty/stale dbSongs snapshot, so it
        // never moved. By id off the live map it must reflect in-progress downloads.
        val ids = listOf("a", "b")
        val liveMap = mapOf(
            "a" to live(Status.DOWNLOADING, progress = 0.4f),
            "b" to live(Status.DOWNLOADING, progress = 0.6f),
        )
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStateResolver.aggregateByIds(ids, liveMap, emptySet()))
        assertEquals(0.5f, DownloadStateResolver.aggregateProgressByIds(ids, liveMap, emptySet()), 0.0001f)
    }

    @Test
    fun aggregateByIds_persistedDownloadedCountsAsDownloaded() {
        val ids = listOf("a", "b")
        assertEquals(
            DownloadStatus.DOWNLOADED,
            DownloadStateResolver.aggregateByIds(ids, emptyMap(), persistedDownloaded = setOf("a", "b")),
        )
        assertEquals(1f, DownloadStateResolver.aggregateProgressByIds(ids, emptyMap(), setOf("a", "b")), 0.0001f)
    }

    @Test
    fun aggregateByIds_empty_isNotDownloaded() {
        assertEquals(DownloadStatus.NOT_DOWNLOADED, DownloadStateResolver.aggregateByIds(emptyList(), emptyMap(), emptySet()))
        assertEquals(0f, DownloadStateResolver.aggregateProgressByIds(emptyList(), emptyMap(), emptySet()), 0.0001f)
    }

    // ---- per-song progress ----

    @Test fun songProgress_downloaded_isOne() {
        assertEquals(1f, DownloadStateResolver.songProgress(isDownloaded = true, live = null), 0.0001f)
        assertEquals(1f, DownloadStateResolver.songProgress(false, live(Status.COMPLETED)), 0.0001f)
    }

    @Test fun songProgress_downloading_isLiveFraction() {
        assertEquals(0.42f, DownloadStateResolver.songProgress(false, live(Status.DOWNLOADING, 0.42f)), 0.0001f)
    }

    @Test fun songProgress_notDownloaded_isZero() {
        assertEquals(0f, DownloadStateResolver.songProgress(false, null), 0.0001f)
        assertEquals(0f, DownloadStateResolver.songProgress(false, live(Status.QUEUED)), 0.0001f)
    }

    @Test fun songProgress_clampsOutOfRange() {
        assertEquals(1f, DownloadStateResolver.songProgress(false, live(Status.DOWNLOADING, 1.5f)), 0.0001f)
        assertEquals(0f, DownloadStateResolver.songProgress(false, live(Status.DOWNLOADING, -0.3f)), 0.0001f)
    }
}
