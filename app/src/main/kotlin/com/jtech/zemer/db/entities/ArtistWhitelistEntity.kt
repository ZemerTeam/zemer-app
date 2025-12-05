package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "artist_whitelist")
data class ArtistWhitelistEntity(
    @PrimaryKey val artistId: String,
    val artistName: String,
    val addedAt: LocalDateTime = LocalDateTime.now(),
    val source: String = "firestore",
    val lastSyncedAt: LocalDateTime = LocalDateTime.now(),
    val isFemale: Boolean = false,
    val isChasid: Boolean = false,
    val isGenZ: Boolean = false,
    val isKids: Boolean = false
)
