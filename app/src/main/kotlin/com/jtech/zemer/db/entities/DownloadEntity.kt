package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDateTime

/**
 * One downloaded file for a song, per rendition [kind]. A single video-capable item can have BOTH an
 * AUDIO and a VIDEO download at once (independent, user-chosen) — which the single
 * `mediaStoreUri`/`isDownloaded`/`isVideo` columns on [SongEntity] cannot represent (one row, one file).
 *
 * This table is the per-kind source of truth: the Downloaded Songs section is the songs with an AUDIO
 * download, Downloaded Videos the songs with a VIDEO download, and playback resolves the AUDIO file in
 * song mode / the VIDEO file for the video-mode LOCAL rendition. `song.isDownloaded` is kept as a
 * denormalized "has ANY download" flag so the many download-badge call sites need no change.
 */
@Immutable
@Entity(
    tableName = "download",
    primaryKeys = ["songId", "kind"],
    indices = [Index(value = ["songId"])],
)
data class DownloadEntity(
    val songId: String,
    /** [DownloadKind.AUDIO] or [DownloadKind.VIDEO]. */
    val kind: String,
    val mediaStoreUri: String,
    val dateDownload: LocalDateTime,
)

/** The two download renditions a song can hold, stored as [DownloadEntity.kind]. */
object DownloadKind {
    const val AUDIO = "AUDIO"
    const val VIDEO = "VIDEO"
}
