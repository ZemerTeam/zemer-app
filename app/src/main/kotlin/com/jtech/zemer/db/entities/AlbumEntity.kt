package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
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
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )

    fun toggleLike() = localToggleLike().also {
        // Anonymous telemetry (spec §3.5): every album-favorite path converges here.
        Tracker.action(if (bookmarkedAt == null) TrackingActionKind.FAVORITE else TrackingActionKind.UNFAVORITE, id)
        // Anonymous (pooled) sessions are local-only — only a personal account pushes to remote.
        if (isPersonalAccountSignedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                if (playlistId != null)
                    YouTube.likePlaylist(playlistId, bookmarkedAt == null)
                this.cancel()
            }
        }
    }
}
