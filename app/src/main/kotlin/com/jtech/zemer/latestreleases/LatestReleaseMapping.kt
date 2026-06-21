package com.jtech.zemer.latestreleases

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist

/**
 * Adapts a feed [LatestRelease] to the InnerTube [AlbumItem] the rest of the app already knows how to
 * render (the grid card, the album menu, navigation) and filter (whitelist `filterWhitelisted`). The
 * feed carries a single artist, so we surface it as the album's only artist.
 */
fun LatestRelease.toAlbumItem(): AlbumItem = AlbumItem(
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    artists = listOf(Artist(name = artistName, id = artistId)),
    year = year,
    thumbnail = thumbnail,
    explicit = false,
)
