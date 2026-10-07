package com.jtech.zemer.stats

import com.jtech.zemer.db.entities.Artist
import com.jtech.zemer.db.entities.SongPlayStats

/** The window's headline numbers. */
data class StatsSummary(val timeListenedMs: Long, val plays: Int, val songs: Int, val artists: Int)

/** Everything the Stats screen shows for one period, ranked and whitelist-scoped by the DAO queries. */
data class ListeningStats(
    val summary: StatsSummary,
    val songs: List<SongPlayStats>,
    val artists: List<Artist>,
)

fun buildListeningStats(songs: List<SongPlayStats>, artists: List<Artist>) = ListeningStats(
    summary = StatsSummary(
        timeListenedMs = songs.sumOf { it.timeListened },
        plays = songs.sumOf { it.plays },
        songs = songs.size,
        artists = artists.size,
    ),
    songs = songs,
    artists = artists,
)
