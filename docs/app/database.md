# App database documentation

## Room database facts

| Fact | Value |
| --- | --- |
| Database class | `MusicDatabase.InternalDatabase` |
| Wrapper class | `MusicDatabase` delegates `DatabaseDao` to `delegate.dao` |
| Schema version | `36` |
| Identity hash | `e6157b9dfb31273b8dad37b1ca5af7d0` |
| Entity count in schema 36 | `19` |
| Schema files tracked | `36` |
| DAO file | `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` |
| DAO methods found by regex | `220` |

## DAO annotation counts

| Annotation | Count |
| --- | ---: |
| `@Query` | 157 |
| `@Transaction` | 110 |
| `@Insert` | 16 |
| `@Delete` | 11 |
| `@Upsert` | 7 |
| `@RewriteQueriesToDropUnusedColumns` | 1 |
| `@RawQuery` | 1 |

## Auto migrations declared in `MusicDatabase.kt`

| From | To | Spec |
| ---: | ---: | --- |
| 2 | 3 | `none` |
| 3 | 4 | `none` |
| 4 | 5 | `none` |
| 5 | 6 | `Migration5To6` |
| 6 | 7 | `Migration6To7` |
| 7 | 8 | `Migration7To8` |
| 8 | 9 | `none` |
| 9 | 10 | `Migration9To10` |
| 10 | 11 | `Migration10To11` |
| 11 | 12 | `Migration11To12` |
| 12 | 13 | `Migration12To13` |
| 13 | 14 | `Migration13To14` |
| 14 | 15 | `none` |
| 15 | 16 | `none` |
| 16 | 17 | `Migration16To17` |
| 17 | 18 | `none` |
| 18 | 19 | `Migration18To19` |
| 19 | 20 | `Migration19To20` |
| 20 | 21 | `Migration20To21` |
| 21 | 22 | `Migration21To22` |
| 22 | 23 | `Migration22To23` |
| 23 | 24 | `none` |
| 24 | 25 | `none` |
| 25 | 26 | `none` |
| 32 | 33 | `none` |
| 35 | 36 | `none` |

## Manual migrations declared in `MusicDatabase.kt`

| Name | From | To |
| --- | ---: | ---: |
| `MIGRATION_1_2` | 1 | 2 |
| `MIGRATION_26_27` | 26 | 27 |
| `MIGRATION_27_28` | 27 | 28 |
| `MIGRATION_28_29` | 28 | 29 |
| `MIGRATION_29_30` | 29 | 30 |
| `MIGRATION_30_31` | 30 | 31 |
| `MIGRATION_31_32` | 31 | 32 |
| `MIGRATION_33_34` | 33 | 34 |
| `MIGRATION_34_35` | 34 | 35 |

## Schema 36 entities

### `song`

- Primary key columns: `id`
- Field count: `25`
- Indices: `5`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `title` | `title` | `TEXT` | `True` | `None` |
| `duration` | `duration` | `INTEGER` | `True` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `albumId` | `albumId` | `TEXT` | `False` | `None` |
| `albumName` | `albumName` | `TEXT` | `False` | `None` |
| `explicit` | `explicit` | `INTEGER` | `True` | `0` |
| `year` | `year` | `INTEGER` | `False` | `None` |
| `date` | `date` | `INTEGER` | `False` | `None` |
| `dateModified` | `dateModified` | `INTEGER` | `False` | `None` |
| `liked` | `liked` | `INTEGER` | `True` | `None` |
| `likedDate` | `likedDate` | `INTEGER` | `False` | `None` |
| `totalPlayTime` | `totalPlayTime` | `INTEGER` | `True` | `None` |
| `lastPositionMs` | `lastPositionMs` | `INTEGER` | `True` | `0` |
| `inLibrary` | `inLibrary` | `INTEGER` | `False` | `None` |
| `dateDownload` | `dateDownload` | `INTEGER` | `False` | `None` |
| `isLocal` | `isLocal` | `INTEGER` | `True` | `false` |
| `libraryAddToken` | `libraryAddToken` | `TEXT` | `False` | `None` |
| `libraryRemoveToken` | `libraryRemoveToken` | `TEXT` | `False` | `None` |
| `romanizeLyrics` | `romanizeLyrics` | `INTEGER` | `True` | `true` |
| `isDownloaded` | `isDownloaded` | `INTEGER` | `True` | `0` |
| `mediaStoreUri` | `mediaStoreUri` | `TEXT` | `False` | `NULL` |
| `isUploaded` | `isUploaded` | `INTEGER` | `True` | `false` |
| `isVideo` | `isVideo` | `INTEGER` | `True` | `0` |
| `isEpisode` | `isEpisode` | `INTEGER` | `True` | `0` |

### `artist`

- Primary key columns: `id`
- Field count: `8`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `name` | `name` | `TEXT` | `True` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `channelId` | `channelId` | `TEXT` | `False` | `None` |
| `lastUpdateTime` | `lastUpdateTime` | `INTEGER` | `True` | `None` |
| `bookmarkedAt` | `bookmarkedAt` | `INTEGER` | `False` | `None` |
| `isLocal` | `isLocal` | `INTEGER` | `True` | `false` |
| `isPodcastChannel` | `isPodcastChannel` | `INTEGER` | `True` | `0` |

### `album`

- Primary key columns: `id`
- Field count: `15`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `playlistId` | `playlistId` | `TEXT` | `False` | `None` |
| `title` | `title` | `TEXT` | `True` | `None` |
| `year` | `year` | `INTEGER` | `False` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `themeColor` | `themeColor` | `INTEGER` | `False` | `None` |
| `songCount` | `songCount` | `INTEGER` | `True` | `None` |
| `duration` | `duration` | `INTEGER` | `True` | `None` |
| `explicit` | `explicit` | `INTEGER` | `True` | `0` |
| `lastUpdateTime` | `lastUpdateTime` | `INTEGER` | `True` | `None` |
| `bookmarkedAt` | `bookmarkedAt` | `INTEGER` | `False` | `None` |
| `likedDate` | `likedDate` | `INTEGER` | `False` | `None` |
| `inLibrary` | `inLibrary` | `INTEGER` | `False` | `None` |
| `isLocal` | `isLocal` | `INTEGER` | `True` | `false` |
| `isUploaded` | `isUploaded` | `INTEGER` | `True` | `false` |

### `playlist`

- Primary key columns: `id`
- Field count: `13`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `name` | `name` | `TEXT` | `True` | `None` |
| `browseId` | `browseId` | `TEXT` | `False` | `None` |
| `createdAt` | `createdAt` | `INTEGER` | `False` | `None` |
| `lastUpdateTime` | `lastUpdateTime` | `INTEGER` | `False` | `None` |
| `isEditable` | `isEditable` | `INTEGER` | `True` | `true` |
| `bookmarkedAt` | `bookmarkedAt` | `INTEGER` | `False` | `None` |
| `remoteSongCount` | `remoteSongCount` | `INTEGER` | `False` | `None` |
| `playEndpointParams` | `playEndpointParams` | `TEXT` | `False` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `shuffleEndpointParams` | `shuffleEndpointParams` | `TEXT` | `False` | `None` |
| `radioEndpointParams` | `radioEndpointParams` | `TEXT` | `False` | `None` |
| `isLocal` | `isLocal` | `INTEGER` | `True` | `false` |

### `song_artist_map`

- Primary key columns: `songId`, `artistId`
- Field count: `3`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `artistId` | `artistId` | `TEXT` | `True` | `None` |
| `position` | `position` | `INTEGER` | `True` | `None` |

### `song_album_map`

- Primary key columns: `songId`, `albumId`
- Field count: `3`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `albumId` | `albumId` | `TEXT` | `True` | `None` |
| `index` | `index` | `INTEGER` | `True` | `None` |

### `album_artist_map`

- Primary key columns: `albumId`, `artistId`
- Field count: `3`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `albumId` | `albumId` | `TEXT` | `True` | `None` |
| `artistId` | `artistId` | `TEXT` | `True` | `None` |
| `order` | `order` | `INTEGER` | `True` | `None` |

### `playlist_song_map`

- Primary key columns: `id`
- Field count: `5`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `INTEGER` | `True` | `None` |
| `playlistId` | `playlistId` | `TEXT` | `True` | `None` |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `position` | `position` | `INTEGER` | `True` | `None` |
| `setVideoId` | `setVideoId` | `TEXT` | `False` | `None` |

### `search_history`

- Primary key columns: `id`
- Field count: `2`
- Indices: `1`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `INTEGER` | `True` | `None` |
| `query` | `query` | `TEXT` | `True` | `None` |

### `format`

- Primary key columns: `id`
- Field count: `10`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `itag` | `itag` | `INTEGER` | `True` | `None` |
| `mimeType` | `mimeType` | `TEXT` | `True` | `None` |
| `codecs` | `codecs` | `TEXT` | `True` | `None` |
| `bitrate` | `bitrate` | `INTEGER` | `True` | `None` |
| `sampleRate` | `sampleRate` | `INTEGER` | `False` | `None` |
| `contentLength` | `contentLength` | `INTEGER` | `True` | `None` |
| `loudnessDb` | `loudnessDb` | `REAL` | `False` | `None` |
| `playbackUrl` | `playbackUrl` | `TEXT` | `False` | `None` |
| `streamClient` | `streamClient` | `TEXT` | `False` | `None` |

### `lyrics`

- Primary key columns: `id`
- Field count: `3`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `lyrics` | `lyrics` | `TEXT` | `True` | `None` |
| `provider` | `provider` | `TEXT` | `False` | `None` |

### `event`

- Primary key columns: `id`
- Field count: `4`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `INTEGER` | `True` | `None` |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `timestamp` | `timestamp` | `INTEGER` | `True` | `None` |
| `playTime` | `playTime` | `INTEGER` | `True` | `None` |

### `related_song_map`

- Primary key columns: `id`
- Field count: `3`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `INTEGER` | `True` | `None` |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `relatedSongId` | `relatedSongId` | `TEXT` | `True` | `None` |

### `set_video_id`

- Primary key columns: `videoId`
- Field count: `2`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `videoId` | `videoId` | `TEXT` | `True` | `None` |
| `setVideoId` | `setVideoId` | `TEXT` | `False` | `None` |

### `playCount`

- Primary key columns: `song`, `year`, `month`
- Field count: `4`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `song` | `song` | `TEXT` | `True` | `None` |
| `year` | `year` | `INTEGER` | `True` | `None` |
| `month` | `month` | `INTEGER` | `True` | `None` |
| `count` | `count` | `INTEGER` | `True` | `None` |

### `artist_whitelist`

- Primary key columns: `artistId`
- Field count: `12`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `artistId` | `artistId` | `TEXT` | `True` | `None` |
| `artistName` | `artistName` | `TEXT` | `True` | `None` |
| `addedAt` | `addedAt` | `INTEGER` | `True` | `None` |
| `source` | `source` | `TEXT` | `True` | `None` |
| `lastSyncedAt` | `lastSyncedAt` | `INTEGER` | `True` | `None` |
| `isFemale` | `isFemale` | `INTEGER` | `True` | `None` |
| `isChasid` | `isChasid` | `INTEGER` | `True` | `None` |
| `isGenZ` | `isGenZ` | `INTEGER` | `True` | `None` |
| `isKids` | `isKids` | `INTEGER` | `True` | `None` |
| `isKidZone` | `isKidZone` | `INTEGER` | `True` | `None` |
| `displayName` | `displayName` | `TEXT` | `False` | `None` |
| `altName` | `altName` | `TEXT` | `False` | `None` |

### `recognition_history`

- Primary key columns: `id`
- Field count: `7`
- Indices: `2`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `INTEGER` | `True` | `None` |
| `songId` | `songId` | `TEXT` | `True` | `None` |
| `title` | `title` | `TEXT` | `True` | `None` |
| `artist` | `artist` | `TEXT` | `True` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `artistIds` | `artistIds` | `TEXT` | `True` | `None` |
| `recognizedAt` | `recognizedAt` | `INTEGER` | `True` | `None` |

### `podcast_whitelist`

- Primary key columns: `channelId`
- Field count: `8`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `channelId` | `channelId` | `TEXT` | `True` | `None` |
| `name` | `name` | `TEXT` | `True` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `isFemale` | `isFemale` | `INTEGER` | `True` | `None` |
| `isKidZone` | `isKidZone` | `INTEGER` | `True` | `None` |
| `isVerified` | `isVerified` | `INTEGER` | `True` | `None` |
| `showCount` | `showCount` | `INTEGER` | `True` | `None` |
| `lastSyncedAt` | `lastSyncedAt` | `INTEGER` | `True` | `None` |

### `podcast`

- Primary key columns: `id`
- Field count: `7`
- Indices: `0`

| Field path | Column | Affinity | Not null | Default |
| --- | --- | --- | --- | --- |
| `id` | `id` | `TEXT` | `True` | `None` |
| `title` | `title` | `TEXT` | `True` | `None` |
| `author` | `author` | `TEXT` | `False` | `None` |
| `thumbnailUrl` | `thumbnailUrl` | `TEXT` | `False` | `None` |
| `channelId` | `channelId` | `TEXT` | `False` | `None` |
| `bookmarkedAt` | `bookmarkedAt` | `INTEGER` | `False` | `None` |
| `lastUpdateTime` | `lastUpdateTime` | `INTEGER` | `True` | `None` |

## DAO method inventory

| Method | Parameters | Return/type text |
| --- | --- | --- |
| `songsByRowIdAsc` | `` | `Flow<List<Song>>` |
| `songsByCreateDateAsc` | `` | `Flow<List<Song>>` |
| `songsByNameAsc` | `` | `Flow<List<Song>>` |
| `songsByPlayTimeAsc` | `` | `Flow<List<Song>>` |
| `songs` | `sortType: SongSortType, descending: Boolean,` | `` |
| `likedSongsByRowIdAsc` | `` | `Flow<List<Song>>` |
| `likedSongsByCreateDateAsc` | `` | `Flow<List<Song>>` |
| `likedSongsByNameAsc` | `` | `Flow<List<Song>>` |
| `likedSongsByPlayTimeAsc` | `` | `Flow<List<Song>>` |
| `likedSongs` | `sortType: SongSortType, descending: Boolean,` | `` |
| `likedSongsCount` | `` | `Flow<Int>` |
| `albumSongs` | `albumId: String` | `Flow<List<Song>>` |
| `playlistSongs` | `playlistId: String` | `Flow<List<PlaylistSong>>` |
| `artistSongsByCreateDateAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongsByNameAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongsByPlayTimeAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongs` | `artistId: String, sortType: ArtistSongSortType, descending: Boolean,` | `` |
| `artistSongsPreview` | `artistId: String, previewSize: Int = 3,` | `Flow<List<Song>>` |
| `quickPicks` | `now: Long = System.currentTimeMillis(` | `): Flow<List<Song>>` |
| `getRecommendationAlbum` | `now: Long = System.currentTimeMillis(` | `,` |
| `mostPlayedSongsStats` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now(` | `.toInstant(ZoneOffset.UTC).toEpochMilli(),` |
| `mostPlayedSongs` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now(` | `.toInstant(ZoneOffset.UTC).toEpochMilli(),` |
| `mostPlayedArtists` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now(` | `.toInstant(ZoneOffset.UTC).toEpochMilli(),` |
| `mostPlayedAlbums` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now(` | `.toInstant(ZoneOffset.UTC).toEpochMilli(),` |
| `artistAlbumsPreview` | `artistId: String, previewSize: Int = 6` | `Flow<List<Album>>` |
| `getLifetimePlayCount` | `songId: String?` | `Flow<Int>` |
| `getPlayCountByYear` | `songId: String?, year: Int` | `Flow<Int>` |
| `getPlayCountByMonth` | `songId: String?, year: Int, month: Int` | `Flow<Int>` |
| `forgottenFavorites` | `now: Long = System.currentTimeMillis(` | `): Flow<List<Song>>` |
| `recommendedAlbum` | `now: Long = System.currentTimeMillis(` | `,` |
| `song` | `songId: String?` | `Flow<Song?>` |
| `getSongByIdBlocking` | `songId: String` | `Song?` |
| `getSongsByIds` | `songIds: List<String>` | `List<Song>` |
| `videos` | `` | `Flow<List<Song>>` |
| `downloadedVideos` | `` | `Flow<List<Song>>` |
| `downloadedVideosByCreateDateAsc` | `` | `Flow<List<Song>>` |
| `downloadedVideosByNameAsc` | `` | `Flow<List<Song>>` |
| `downloadedVideosByPlayTimeAsc` | `` | `Flow<List<Song>>` |
| `downloadedVideosSorted` | `sortType: SongSortType, descending: Boolean` | `Flow<List<Song>>` |
| `songArtistMap` | `songId: String` | `List<SongArtistMap>` |
| `allSongs` | `` | `Flow<List<Song>>` |
| `allArtistsByPlayTime` | `` | `Flow<List<Artist>>` |
| `getSetVideoId` | `videoId: String` | `SetVideoIdEntity?` |
| `format` | `id: String?` | `Flow<FormatEntity?>` |
| `lyrics` | `id: String?` | `Flow<LyricsEntity?>` |
| `purgeUntrustedLyrics` | `` | `Int` |
| `artistsByCreateDateAsc` | `` | `Flow<List<Artist>>` |
| `artistsByNameAsc` | `` | `Flow<List<Artist>>` |
| `allWhitelistedArtistsByName` | `` | `Flow<List<Artist>>` |
| `allKidsArtistsByName` | `` | `Flow<List<Artist>>` |
| `artistsBySongCountAsc` | `` | `Flow<List<Artist>>` |
| `artistsByPlayTimeAsc` | `` | `Flow<List<Artist>>` |
| `artistsBookmarkedByCreateDateAsc` | `` | `Flow<List<Artist>>` |
| `artistsBookmarkedByNameAsc` | `` | `Flow<List<Artist>>` |
| `artistsBookmarkedBySongCountAsc` | `` | `Flow<List<Artist>>` |
| `artistsBookmarkedByPlayTimeAsc` | `` | `Flow<List<Artist>>` |
| `artists` | `sortType: ArtistSortType, descending: Boolean` | `` |
| `artistsBookmarked` | `sortType: ArtistSortType, descending: Boolean` | `` |
| `artist` | `id: String` | `Flow<Artist?>` |
| `albumsByCreateDateAsc` | `` | `Flow<List<Album>>` |
| `albumsByNameAsc` | `` | `Flow<List<Album>>` |
| `albumsByYearAsc` | `` | `Flow<List<Album>>` |
| `albumsBySongCountAsc` | `` | `Flow<List<Album>>` |
| `albumsByLengthAsc` | `` | `Flow<List<Album>>` |
| `albumsByPlayTimeAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedByCreateDateAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedByNameAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedByYearAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedBySongCountAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedByLengthAsc` | `` | `Flow<List<Album>>` |
| `albumsLikedByPlayTimeAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedByCreateDateAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedByNameAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedByYearAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedBySongCountAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedByLengthAsc` | `` | `Flow<List<Album>>` |
| `albumsUploadedByPlayTimeAsc` | `` | `Flow<List<Album>>` |
| `albums` | `sortType: AlbumSortType, descending: Boolean,` | `` |
| `albumsLiked` | `sortType: AlbumSortType, descending: Boolean,` | `` |
| `albumsUploaded` | `sortType: AlbumSortType, descending: Boolean,` | `` |
| `album` | `id: String` | `Flow<Album?>` |
| `albumUnfiltered` | `id: String` | `Flow<Album?>` |
| `albumWithSongs` | `albumId: String` | `Flow<AlbumWithSongs?>` |
| `albumArtistMaps` | `albumId: String` | `List<AlbumArtistMap>` |
| `playlistsByCreateDateAsc` | `` | `Flow<List<Playlist>>` |
| `playlistsByUpdatedDateAsc` | `` | `Flow<List<Playlist>>` |
| `playlistsByNameAsc` | `` | `Flow<List<Playlist>>` |
| `playlistsBySongCountAsc` | `` | `Flow<List<Playlist>>` |
| `randomPlaylistsByArtists` | `artistIds: List<String>, limit: Int = 6` | `List<Playlist>` |
| `playlists` | `sortType: PlaylistSortType, descending: Boolean,` | `` |
| `playlist` | `playlistId: String` | `Flow<Playlist?>` |
| `editablePlaylistsByCreateDateAsc` | `` | `Flow<List<Playlist>>` |
| `playlistByBrowseId` | `browseId: String` | `Flow<Playlist?>` |
| `checkInPlaylist` | `playlistId: String, songId: String,` | `Int` |
| `playlistDuplicates` | `playlistId: String, songIds: List<String>,` | `List<String>` |
| `addSongToPlaylist` | `playlist: Playlist, songIds: List<String>` | `` |
| `downloadedSongs` | `sortType: SongSortType, descending: Boolean` | `Flow<List<Song>>` |
| `downloadedSongsByCreateDateAsc` | `` | `Flow<List<Song>>` |
| `downloadedSongsByNameAsc` | `` | `Flow<List<Song>>` |
| `downloadedSongsByPlayTimeAsc` | `` | `Flow<List<Song>>` |
| `updateDownloadedInfo` | `songId: String, downloaded: Boolean, date: LocalDateTime?` | `@Query("UPDATE song SET isVideo` |
| `setIsVideo` | `songId: String, isVideo: Boolean` | `@Transaction` |
| `uploadedSongsByCreateDateAsc` | `` | `Flow<List<Song>>` |
| `uploadedSongsByNameAsc` | `` | `Flow<List<Song>>` |
| `uploadedSongsByPlayTimeAsc` | `` | `Flow<List<Song>>` |
| `uploadedSongsByRowIdAsc` | `` | `Flow<List<Song>>` |
| `uploadedSongs` | `sortType: SongSortType, descending: Boolean,` | `` |
| `searchArtists` | `query: String, previewSize: Int = Int.MAX_VALUE,` | `Flow<List<Artist>>` |
| `searchAlbums` | `query: String, previewSize: Int = Int.MAX_VALUE,` | `Flow<List<Album>>` |
| `searchPlaylists` | `query: String, previewSize: Int = Int.MAX_VALUE,` | `Flow<List<Playlist>>` |
| `events` | `` | `Flow<List<EventWithSong>>` |
| `firstEvent` | `` | `Flow<EventWithSong?>` |
| `clearListenHistory` | `` | `@Transaction` |
| `searchHistory` | `query: String = ""` | `Flow<List<SearchHistory>>` |
| `clearSearchHistory` | `` | `@Query("UPDATE song SET totalPlayTime` |
| `incrementTotalPlayTime` | `songId: String, playTime: Long` | `@Query("UPDATE playCount SET count` |
| `incrementPlayCount` | `songId: String, year: Int, month: Int` | `/**` |
| `incrementPlayCount` | `songId: String` | `` |
| `inLibrary` | `songId: String, inLibrary: LocalDateTime?,` | `@Transaction` |
| `addLibraryTokens` | `songId: String, libraryAddToken: String?, libraryRemoveToken: String?,` | `@Transaction` |
| `hasRelatedSongs` | `songId: String` | `Boolean` |
| `getRelatedSongs` | `songId: String` | `Flow<List<Song>>` |
| `relatedSongs` | `songId: String` | `List<Song>` |
| `move` | `playlistId: String, fromPosition: Int, toPosition: Int,` | `@Transaction` |
| `clearPlaylist` | `playlistId: String` | `@Transaction` |
| `artistByName` | `name: String` | `ArtistEntity?` |
| `getArtistById` | `id: String` | `ArtistEntity?` |
| `getAllArtistIdsSync` | `` | `List<String>` |
| `insert` | `song: SongEntity` | `Long` |
| `insert` | `artist: ArtistEntity` | `@Insert(onConflict` |
| `insertArtists` | `artists: List<ArtistEntity>` | `@Insert(onConflict` |
| `insert` | `album: AlbumEntity` | `Long` |
| `insert` | `playlist: PlaylistEntity` | `@Insert(onConflict` |
| `insert` | `map: SongArtistMap` | `@Insert(onConflict` |
| `insert` | `map: SongAlbumMap` | `@Insert(onConflict` |
| `insert` | `map: AlbumArtistMap` | `@Insert(onConflict` |
| `insert` | `map: PlaylistSongMap` | `@Insert(onConflict` |
| `insert` | `searchHistory: SearchHistory` | `@Insert(onConflict` |
| `insert` | `event: Event` | `@Insert(onConflict` |
| `insert` | `map: RelatedSongMap` | `@Insert(onConflict` |
| `insert` | `playCountEntity: PlayCountEntity` | `Long` |
| `insert` | `mediaMetadata: MediaMetadata, block: (SongEntity` | `-> SongEntity` |
| `insert` | `albumPage: AlbumPage` | `` |
| `update` | `song: Song, mediaMetadata: MediaMetadata,` | `` |
| `update` | `song: SongEntity` | `@Update` |
| `update` | `artist: ArtistEntity` | `@Update` |
| `update` | `album: AlbumEntity` | `@Update` |
| `update` | `playlist: PlaylistEntity` | `@Update` |
| `update` | `map: PlaylistSongMap` | `@Transaction` |
| `update` | `artist: ArtistEntity, artistPage: ArtistPage` | `` |
| `update` | `album: AlbumEntity, albumPage: AlbumPage, artists: List<ArtistEntity>? = emptyList(` | `,` |
| `update` | `playlistEntity: PlaylistEntity, playlistItem: PlaylistItem` | `` |
| `upsert` | `map: SongAlbumMap` | `@Upsert` |
| `upsert` | `lyrics: LyricsEntity` | `@Upsert` |
| `upsert` | `format: FormatEntity` | `@Upsert` |
| `upsert` | `song: SongEntity` | `@Delete` |
| `delete` | `song: SongEntity` | `@Delete` |
| `delete` | `songArtistMap: SongArtistMap` | `@Delete` |
| `delete` | `artist: ArtistEntity` | `@Delete` |
| `delete` | `album: AlbumEntity` | `@Delete` |
| `delete` | `albumArtistMap: AlbumArtistMap` | `@Delete` |
| `delete` | `playlist: PlaylistEntity` | `@Delete` |
| `delete` | `playlistSongMap: PlaylistSongMap` | `@Query("DELETE FROM playlist WHERE browseId` |
| `deletePlaylistById` | `browseId: String` | `@Delete` |
| `delete` | `lyrics: LyricsEntity` | `@Delete` |
| `delete` | `searchHistory: SearchHistory` | `@Delete` |
| `delete` | `event: Event` | `@Transaction` |
| `playlistSongMaps` | `songId: String` | `List<PlaylistSongMap>` |
| `playlistSongMaps` | `playlistId: String, from: Int,` | `List<PlaylistSongMap>` |
| `raw` | `supportSQLiteQuery: SupportSQLiteQuery` | `Int` |
| `checkpoint` | `` | `` |
| `upsert` | `whitelist: ArtistWhitelistEntity` | `@Insert(onConflict` |
| `insertWhitelist` | `whitelistEntries: List<ArtistWhitelistEntity>` | `@Query("SELECT artistId FROM artist_whitelist")` |
| `getAllWhitelistedArtistIds` | `` | `Flow<List<String>>` |
| `getAllWhitelistedArtistIdsSync` | `` | `List<String>` |
| `getWhitelistEntry` | `artistId: String` | `ArtistWhitelistEntity?` |
| `getWhitelistEntriesSync` | `` | `List<ArtistWhitelistEntity>` |
| `isArtistWhitelisted` | `artistId: String` | `Boolean` |
| `getRandomWhitelistedArtistIds` | `limit: Int` | `List<String>` |
| `getWhitelistedArtistIdsMissingThumb` | `limit: Int` | `List<String>` |
| `clearWhitelist` | `` | `@Query("DELETE FROM artist_whitelist WHERE artistId` |
| `removeFromWhitelist` | `artistId: String` | `// Artist deletion methods` |
| `getSongIdsByArtist` | `artistId: String` | `List<String>` |
| `getAlbumIdsByArtist` | `artistId: String` | `List<String>` |
| `deletePlayCountBySong` | `songId: String` | `@Query("DELETE FROM format WHERE id` |
| `deleteFormatBySong` | `songId: String` | `@Query("DELETE FROM lyrics WHERE id` |
| `deleteLyricsBySong` | `songId: String` | `@Query("DELETE FROM song WHERE id` |
| `deleteSongById` | `songId: String` | `@Query("SELECT COUNT(*) FROM song_album_map WHERE albumId` |
| `getAlbumSongCount` | `albumId: String` | `Int` |
| `deleteAlbumById` | `albumId: String` | `@Query("DELETE FROM artist WHERE id` |
| `deleteArtistById` | `artistId: String` | `// Batch operations for efficiency` |
| `deletePlayCountBySongs` | `songIds: List<String>` | `@Query("DELETE FROM format WHERE id IN (:songIds)")` |
| `deleteFormatBySongs` | `songIds: List<String>` | `@Query("DELETE FROM lyrics WHERE id IN (:songIds)")` |
| `deleteLyricsBySongs` | `songIds: List<String>` | `@Query("DELETE FROM song WHERE id IN (:songIds)")` |
| `deleteSongsByIds` | `songIds: List<String>` | `@Query("DELETE FROM album WHERE id IN (:albumIds)")` |
| `deleteAlbumsByIds` | `albumIds: List<String>` | `@Query("DELETE FROM artist WHERE id IN (:artistIds)")` |
| `deleteArtistsByIds` | `artistIds: List<String>` | `}` |
