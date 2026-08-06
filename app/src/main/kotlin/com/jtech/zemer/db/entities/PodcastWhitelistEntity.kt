package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "podcast_whitelist")
data class PodcastWhitelistEntity(
    @PrimaryKey val podcastId: String,
    val podcastName: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val addedAt: LocalDateTime = LocalDateTime.now(),
    val source: String = "mirror",
    val lastSyncedAt: LocalDateTime = LocalDateTime.now()
)
