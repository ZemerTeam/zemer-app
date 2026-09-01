package com.jtech.zemer.models

import androidx.compose.runtime.Immutable
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.SongItem
import com.jtech.zemer.db.entities.Song
import com.jtech.zemer.db.entities.SongEntity
import com.jtech.zemer.playback.VideoSongIds
import com.jtech.zemer.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val isVideo: Boolean = false,
    val isEpisode: Boolean = false,
) : Serializable {
    data class Artist(
        val id: String?,
        val name: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = -355198349731679509L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = -3879000833009517336L
        }
    }

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
            libraryAddToken = libraryAddToken,
            libraryRemoveToken = libraryRemoveToken,
            isVideo = isVideo,
            isEpisode = isEpisode
        )

    companion object {
        // Pinned to the value computed for the v37 class (which still carried the removed
        // `explicit` field) so a persisted queue written by an older build keeps deserializing;
        // the stream's extra field is ignored. Java serialization derives this from the class
        // shape when undeclared, so an undeclared value here breaks queue restore on any edit.
        private const val serialVersionUID = 3273021534433957495L
    }
}

/**
 * Fill the NAVIGATION ids the wire item lacked from the played song's DB row, so the player's
 * title/artist taps (and the player menu's View artist / View album rows) work on every surface.
 * Several Zemer track payloads are name-only (curated playlists, community playlists, search rows) —
 * their queue items carry no artist id / album ref, which left those taps as silent no-ops. Every
 * play inserts the song resolving id-less credits via artistByName (whitelisted row preferred), so
 * by the time the player is open [song] carries the resolved ids; take an artist id only from a
 * name-matched row with a REAL channel id ([ArtistEntity.isYouTubeArtist]) — navigating a generated
 * local id opens a dead "not available" page, worse than the no-op. Display fields are untouched.
 */
fun MediaMetadata.withResolvedNavIds(song: Song?): MediaMetadata {
    song ?: return this
    val resolvedArtists = artists.map { artist ->
        if (!artist.id.isNullOrBlank()) artist
        else song.artists.firstOrNull { it.name == artist.name && it.isYouTubeArtist }
            ?.let { artist.copy(id = it.id) } ?: artist
    }
    val resolvedAlbum = album ?: song.song.albumId?.let {
        MediaMetadata.Album(id = it, title = song.song.albumName.orEmpty())
    }
    return if (resolvedArtists == artists && resolvedAlbum == album) this
    else copy(artists = resolvedArtists, album = resolvedAlbum)
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
        isVideo = song.isVideo,
        isEpisode = song.isEpisode,
    )

fun SongItem.toMediaMetadata(): MediaMetadata {
    // The corpus's video classification deliberately does NOT enter playback metadata (a video-song
    // plays/downloads/persists as ordinary audio) — but the Song/Video toggle is entitled to it, so
    // it rides the process-wide registry instead: marked here, at the one SongItem→playback boundary.
    if (isVideo) VideoSongIds.mark(id)
    return MediaMetadata(
        id = id,
        title = title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = duration ?: -1,
        thumbnailUrl = thumbnail.resize(544, 544),
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        setVideoId = setVideoId,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        isEpisode = isEpisode
    )
}

fun EpisodeItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists = listOfNotNull(author).map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = duration ?: -1,
        thumbnailUrl = thumbnail.resize(544, 544),
        album = podcast?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        isEpisode = true
    )
