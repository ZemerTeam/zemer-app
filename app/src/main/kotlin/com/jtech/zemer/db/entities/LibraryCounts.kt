package com.jtech.zemer.db.entities

/** [com.jtech.zemer.db.DatabaseDao.libraryCounts]: songs in the library, downloaded songs, saved playlists, liked songs. */
data class LibraryCounts(
    val librarySongs: Int,
    val downloads: Int,
    val playlists: Int,
    val liked: Int,
)
