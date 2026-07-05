@file:Suppress("unused")

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
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    fun toggleLike() = localToggleLike().also {
        // Anonymous telemetry (spec §3.5): every artist-favorite path converges here.
        Tracker.action(if (bookmarkedAt == null) TrackingActionKind.FAVORITE else TrackingActionKind.UNFAVORITE, id)
        // Anonymous (pooled) sessions are local-only — only a personal account pushes to remote.
        if (isPersonalAccountSignedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                if (channelId == null)
                    YouTube.subscribeChannel(YouTube.getChannelId(id), bookmarkedAt == null)
                else
                    YouTube.subscribeChannel(channelId, bookmarkedAt == null)
                this.cancel()
            }
        }
    }

    companion object {
        fun generateArtistId() = "LA" + List(8) { ('A'..'Z').random() }.joinToString("")
    }
}
