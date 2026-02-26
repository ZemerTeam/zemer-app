package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity for tracking user's saved/subscribed podcasts.
 * This is different from PodcastWhitelistEntity which controls which podcasts are allowed.
 */
@Immutable
@Entity(tableName = "podcast")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val bookmarkedAt: LocalDateTime? = null, // null = not saved/subscribed
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
) {
    fun toggleBookmark() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
        lastUpdateTime = LocalDateTime.now(),
    )

    val inLibrary: Boolean get() = bookmarkedAt != null
}
