package com.metrolist.innertube.models

sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

data class Artist(
    val name: String,
    val id: String?,
)

data class Album(
    val name: String,
    val id: String,
)

data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val historyRemoveToken: String? = null,
    // True when this item is a YouTube video (not an audio track). UI-only classification used to label
    // and route "video songs"; it deliberately does NOT flow into MediaMetadata.isVideo (playback stays
    // audio). Default false so every existing caller/parser is unaffected.
    val isVideo: Boolean = false,
    val isEpisode: Boolean = false,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.zemer.io/watch?v=$id"
}

data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.zemer.io/playlist?list=$playlistId"
}

data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.zemer.io/playlist?list=$id"
}

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.zemer.io/channel/$id"
}

data class PodcastItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val episodeCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val channelId: String? = null,
    val categories: List<String> = emptyList(),
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.zemer.io/playlist?list=$id"

    fun asPlaylistItem() = PlaylistItem(
        id = id,
        title = title,
        author = author,
        songCountText = episodeCountText,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = null,
        isEditable = false
    )
}

data class EpisodeItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val podcast: Album? = null,
    val duration: Int? = null,
    val publishDateText: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val markAsPlayedToken: String? = null,
    val markAsUnplayedToken: String? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.zemer.io/watch?v=$id"

    fun asSongItem() = SongItem(
        id = id,
        title = title,
        artists = listOfNotNull(author),
        album = podcast,
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit,
        endpoint = endpoint,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        isEpisode = true
    )
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }
