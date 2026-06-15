package com.jtech.zemer.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One entry in the "Recognize music" history.
 *
 * Holds a whitelist-resolved YouTube Music song (the same [songId] the user can play) plus the
 * resolved artists' browse IDs ([artistIds]). The whitelist is mutable (Firebase sync can add or
 * remove artists, flags can flip), so the IDs are kept here to re-check membership before the entry
 * is ever shown or played again — an entry whose artists are no longer whitelisted must not surface.
 *
 * Indexed on [songId] (the de-dup delete on every resolve) and [recognizedAt] (the list ordering).
 */
@Entity(
    tableName = "recognition_history",
    indices = [Index("songId"), Index("recognizedAt")],
)
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    /** Comma-joined YouTube Music artist browse IDs, used to re-check the whitelist on replay. */
    val artistIds: String = "",
    val recognizedAt: LocalDateTime = LocalDateTime.now(),
)
