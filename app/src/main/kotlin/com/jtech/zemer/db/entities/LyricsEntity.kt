package com.jtech.zemer.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    /** Which provider answered (e.g. "Zemer · jkaraoke", "SimpMusic", "manual"); null for rows cached before this column. */
    val provider: String? = null,
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }
}
