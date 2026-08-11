package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jtech.zemer.extensions.isPersonalAccountSignedIn
import com.jtech.zemer.tracking.Tracker
import com.jtech.zemer.tracking.TrackingActionKind
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["inLibrary"]),
        Index(value = ["liked"]),
        Index(value = ["isVideo"]),
        Index(value = ["isEpisode"]),
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    // Last playback position for RESUME (podcast episodes only - long content the user leaves mid-way).
    // 0 = start from the beginning. Songs never write this, so their behavior is unchanged.
    @ColumnInfo(name = "lastPositionMs", defaultValue = "0")
    val lastPositionMs: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @ColumnInfo(defaultValue = true.toString())
    val romanizeLyrics: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "mediaStoreUri", defaultValue = "NULL")
    val mediaStoreUri: String? = null,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false,
    @ColumnInfo(name = "isVideo", defaultValue = "0")
    val isVideo: Boolean = false,
    @ColumnInfo(name = "isEpisode", defaultValue = "0")
    val isEpisode: Boolean = false,
) {
    /**
     * The player heart's "on" state. An episode is never `liked` - its saved-for-later state is
     * `inLibrary` (that is what `MusicService.toggleLike` flips for episodes) - so the heart must read
     * inLibrary for episodes and `liked` for songs, or it never reflects a saved episode.
     */
    val isSavedForPlayer: Boolean
        get() = if (isEpisode) inLibrary != null else liked

    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    ).also {
        // Anonymous telemetry (spec §3.5): every song-like path in the app converges here.
        Tracker.action(if (!liked) TrackingActionKind.FAVORITE else TrackingActionKind.UNFAVORITE, id)
        // Anonymous (pooled) sessions are local-only — only a personal account pushes to remote.
        if (isPersonalAccountSignedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                YouTube.likeVideo(id, !liked)
                this.cancel()
            }
        }
    }

    fun toggleLibrary() = copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        likedDate = if (inLibrary == null) likedDate else null
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )
}
