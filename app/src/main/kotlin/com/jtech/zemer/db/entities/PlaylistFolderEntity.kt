package com.jtech.zemer.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_folder")
data class PlaylistFolderEntity(
    @PrimaryKey val id: String = generatePlaylistFolderId(),
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun generatePlaylistFolderId() = "PF" + List(8) { ('A'..'Z').random() }.joinToString("")
    }
}

data class PlaylistFolderWithCount(
    val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val playlistCount: Int = 0,
)
