package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/** A played song with its play count and listening time over a stats window. */
@Immutable
data class SongPlayStats(
    @Embedded val song: Song,
    val plays: Int,
    val timeListened: Long,
)
