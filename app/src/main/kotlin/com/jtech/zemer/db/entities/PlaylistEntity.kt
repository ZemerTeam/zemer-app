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
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    // Issue #176 live-updating shares: the server share id + owner secret of this playlist's
    // active shared link (null = never shared or unshared), and the fingerprint of the state last
    // successfully PUT to the server (drives the auto-updater's "anything to push?" check;
    // crash-safe because it is written only AFTER a successful server update).
    val shareId: String? = null,
    val shareOwnerToken: String? = null,
    val shareSyncedHash: String? = null,
    // The sharer name THIS share was created/last re-shared with (null = anonymous). The
    // auto-updater sends this, never the device-wide name preference - a share created anonymous
    // must never be retroactively de-anonymized by a name typed for a different playlist.
    val shareSharedBy: String? = null,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + List(8) { ('A'..'Z').random() }.joinToString("")
    }

    val shareLink: String?
        get() {
            return if (browseId != null)
                "https://music.zemer.io/playlist?list=$browseId"
            else null
        }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        // Anonymous telemetry (spec §3.5): every playlist-favorite path converges here. Prefer the
        // remote id (meaningful server-side) over the local random id for online playlists.
        Tracker.action(if (bookmarkedAt == null) TrackingActionKind.FAVORITE else TrackingActionKind.UNFAVORITE, browseId ?: id)
        // Anonymous (pooled) sessions are local-only — only a personal account pushes to remote.
        if (isPersonalAccountSignedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                if (browseId != null)
                    YouTube.likePlaylist(browseId, bookmarkedAt == null)
                this.cancel()
            }
        }
    }
}
