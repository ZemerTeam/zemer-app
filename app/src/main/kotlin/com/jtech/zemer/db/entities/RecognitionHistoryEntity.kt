package com.jtech.zemer.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One entry in the "Recognize music" history.
 *
 * Only ever holds a whitelist-resolved YouTube Music song (the same [songId] the user can play),
 * never raw Shazam metadata — so the history can never contain a non-whitelisted track.
 */
@Entity(tableName = "recognition_history")
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val recognizedAt: LocalDateTime = LocalDateTime.now(),
)
