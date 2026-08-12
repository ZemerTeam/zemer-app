package com.jtech.zemer.models

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem

/**
 * The one Room-entity → YTItem mapping, shared by every surface that renders local DB rows through
 * the shared item renderers (the search screen's local artist/album results, the offline-mode Home
 * rows). Field choices mirror what the wire mappers produce so a local card renders and routes
 * exactly like a served one.
 */
fun com.jtech.zemer.db.entities.Artist.toArtistItem(): ArtistItem =
    ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnailUrl,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

fun com.jtech.zemer.db.entities.Album.toAlbumItem(): AlbumItem =
    AlbumItem(
        browseId = id,
        // The browseId fallback mirrors toAlbumPage's card rule (its only consumer is the disabled
        // automix, never persisted back).
        playlistId = album.playlistId ?: id,
        title = title,
        artists = artists.map { com.metrolist.innertube.models.Artist(name = it.name, id = it.id) },
        year = album.year,
        thumbnail = thumbnailUrl ?: "",
    )

fun com.jtech.zemer.db.entities.Song.toSongItem(): SongItem =
    SongItem(
        id = id,
        title = title,
        artists = artists.map { com.metrolist.innertube.models.Artist(name = it.name, id = it.id) },
        album = album?.let { com.metrolist.innertube.models.Album(name = it.title, id = it.id) },
        duration = song.duration,
        thumbnail = thumbnailUrl ?: "",
        explicit = song.explicit,
        isVideo = song.isVideo,
        isEpisode = song.isEpisode,
    )
