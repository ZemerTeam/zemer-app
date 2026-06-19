package com.jtech.zemer.playback

import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState
import com.jtech.zemer.playback.MediaStoreDownloadManager.DownloadState.Status

/**
 * The three states the whole UI cares about for a song or a collection of songs.
 */
enum class DownloadStatus { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED }

/**
 * THE single source of truth for "is this downloaded / downloading", used by every download badge,
 * menu and header in the app. Read this — never the raw flows — so the answer can never drift
 * between surfaces again.
 *
 * Downloads go exclusively through [MediaStoreDownloadManager]. There are two facts to combine:
 * - the PERSISTED [com.jtech.zemer.db.entities.SongEntity.isDownloaded] flag, which is the only thing
 *   that survives a process restart, and
 * - the LIVE in-session [MediaStoreDownloadManager.downloadStates] map, which carries progress and the
 *   transient QUEUED/DOWNLOADING/FAILED states but is empty on a fresh launch.
 *
 * The legacy ExoPlayer download map ([DownloadUtil.downloads] / [DownloadUtil.getDownload]) is
 * deliberately NOT consulted: nothing writes to it anymore, so reading it always reports "not
 * downloaded". `scripts/ui-audit.sh` bans new reads of it so this mistake can't come back.
 */
object DownloadStateResolver {

    /** Per-song status from the persisted flag + this song's live download state (if any). */
    fun forSong(isDownloaded: Boolean, live: DownloadState?): DownloadStatus = when {
        isDownloaded || live?.status == Status.COMPLETED -> DownloadStatus.DOWNLOADED
        live?.status == Status.QUEUED || live?.status == Status.DOWNLOADING -> DownloadStatus.DOWNLOADING
        else -> DownloadStatus.NOT_DOWNLOADED
    }

    fun forSong(song: Song, live: Map<String, DownloadState>): DownloadStatus =
        forSong(song.song.isDownloaded, live[song.id])

    /** Per-song progress fraction (0..1): 1 once downloaded, else the active download's live progress. */
    fun songProgress(isDownloaded: Boolean, live: DownloadState?): Float = when {
        isDownloaded || live?.status == Status.COMPLETED -> 1f
        else -> (live?.progress ?: 0f).coerceIn(0f, 1f)
    }

    /**
     * Aggregate state for a collection (album / playlist header, multi-select):
     * - DOWNLOADED only when every song is downloaded,
     * - NOT_DOWNLOADED if any song is neither downloaded nor in progress,
     * - DOWNLOADING otherwise (everything is downloaded-or-in-progress, with at least one pending).
     * An empty collection is NOT_DOWNLOADED.
     */
    fun aggregate(statuses: List<DownloadStatus>): DownloadStatus = when {
        statuses.isEmpty() -> DownloadStatus.NOT_DOWNLOADED
        statuses.all { it == DownloadStatus.DOWNLOADED } -> DownloadStatus.DOWNLOADED
        statuses.any { it == DownloadStatus.NOT_DOWNLOADED } -> DownloadStatus.NOT_DOWNLOADED
        else -> DownloadStatus.DOWNLOADING
    }

    fun aggregateSongs(songs: List<Song>, live: Map<String, DownloadState>): DownloadStatus =
        aggregate(songs.map { forSong(it, live) })

    /**
     * Status of a song known only by id (online items not necessarily in the DB): persisted-downloaded
     * comes from [persistedDownloaded], live progress/queued from [live]. Lets online album/playlist
     * menus show live download progress keyed by videoId without holding Room entities.
     */
    fun statusForId(id: String, live: Map<String, DownloadState>, persistedDownloaded: Set<String>): DownloadStatus =
        forSong(id in persistedDownloaded, live[id])

    fun aggregateByIds(
        ids: List<String>,
        live: Map<String, DownloadState>,
        persistedDownloaded: Set<String>,
    ): DownloadStatus = aggregate(ids.map { statusForId(it, live, persistedDownloaded) })

    fun aggregateProgressByIds(
        ids: List<String>,
        live: Map<String, DownloadState>,
        persistedDownloaded: Set<String>,
    ): Float {
        if (ids.isEmpty()) return 0f
        return ids.map { id ->
            when (statusForId(id, live, persistedDownloaded)) {
                DownloadStatus.DOWNLOADED -> 1f
                DownloadStatus.DOWNLOADING -> live[id]?.progress ?: 0f
                DownloadStatus.NOT_DOWNLOADED -> 0f
            }
        }.average().toFloat().coerceIn(0f, 1f)
    }

    /**
     * Fraction (0..1) of a collection that is on disk, for an aggregate progress bar: a downloaded
     * song counts as 1, an in-progress song as its live [DownloadState.progress], everything else 0.
     * Returns 0 for an empty collection.
     */
    fun aggregateProgress(songs: List<Song>, live: Map<String, DownloadState>): Float {
        if (songs.isEmpty()) return 0f
        return songs.map { song ->
            when (forSong(song, live)) {
                DownloadStatus.DOWNLOADED -> 1f
                DownloadStatus.DOWNLOADING -> live[song.id]?.progress ?: 0f
                DownloadStatus.NOT_DOWNLOADED -> 0f
            }
        }.average().toFloat().coerceIn(0f, 1f)
    }
}
