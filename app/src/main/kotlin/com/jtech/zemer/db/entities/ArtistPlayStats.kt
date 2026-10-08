package com.jtech.zemer.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/** A played artist with its play count and listening time over a stats window. */
@Immutable
data class ArtistPlayStats(
    @Embedded val artist: ArtistEntity,
    val plays: Int,
    val timeListened: Long,
) {
    /** The library [Artist] the shared row and menu take. */
    fun asArtist() = Artist(artist = artist, songCount = plays)
}
