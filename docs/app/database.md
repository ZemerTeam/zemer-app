# App database documentation

## Room database facts

| Fact | Value |
| --- | --- |
| Database class | `InternalDatabase` (top-level `abstract class` in `db/MusicDatabase.kt`) |
| Wrapper class | `MusicDatabase` delegates `DatabaseDao` to `delegate.dao` |
| Schema version | `36` |
| Identity hash | `e6157b9dfb31273b8dad37b1ca5af7d0` |
| Entity count in schema 36 | `19` |
| View count in schema 36 | `3` |
| Schema files tracked | `36` |
| DAO file | `app/src/main/kotlin/com/jtech/zemer/db/DatabaseDao.kt` |
| DAO `fun` declarations found by parser | `228` (`192` distinct names) |

## DAO annotation counts

| Annotation | Count |
| --- | ---: |
| `@Query` | 157 |
| `@Transaction` | 110 |
| `@Insert` | 16 |
| `@Delete` | 11 |
| `@Upsert` | 7 |
| `@Update` | 7 |
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

## Schema 36 views

Declared in `@Database(views = [SortedSongArtistMap::class, SortedSongAlbumMap::class, PlaylistSongMapPreview::class])`.

| View | Create SQL (from `36.json`) |
| --- | --- |
| `sorted_song_artist_map` | `` CREATE VIEW `${VIEW_NAME}` AS SELECT * FROM song_artist_map ORDER BY position `` |
| `sorted_song_album_map` | `` CREATE VIEW `${VIEW_NAME}` AS SELECT * FROM song_album_map ORDER BY `index` `` |
| `playlist_song_map_preview` | `` CREATE VIEW `${VIEW_NAME}` AS SELECT * FROM playlist_song_map WHERE position <= 3 ORDER BY position `` |

## DAO method inventory

Every `fun` in `db/DatabaseDao.kt` in source order (overloads repeat the name). Return `(inferred)` = expression body without a declared type; `Unit` = block body without a declared type.

| Method | Parameters | Return/type text |
| --- | --- | --- |
| `songsByRowIdAsc` |  | `Flow<List<Song>>` |
| `songsByCreateDateAsc` |  | `Flow<List<Song>>` |
| `songsByNameAsc` |  | `Flow<List<Song>>` |
| `songsByPlayTimeAsc` |  | `Flow<List<Song>>` |
| `songs` | `sortType: SongSortType, descending: Boolean` | `(inferred)` |
| `likedSongsByRowIdAsc` |  | `Flow<List<Song>>` |
| `likedSongsByCreateDateAsc` |  | `Flow<List<Song>>` |
| `likedSongsByNameAsc` |  | `Flow<List<Song>>` |
| `likedSongsByPlayTimeAsc` |  | `Flow<List<Song>>` |
| `likedSongs` | `sortType: SongSortType, descending: Boolean` | `(inferred)` |
| `likedSongsCount` |  | `Flow<Int>` |
| `albumSongs` | `albumId: String` | `Flow<List<Song>>` |
| `playlistSongs` | `playlistId: String` | `Flow<List<PlaylistSong>>` |
| `artistSongsByCreateDateAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongsByNameAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongsByPlayTimeAsc` | `artistId: String` | `Flow<List<Song>>` |
| `artistSongs` | `artistId: String, sortType: ArtistSongSortType, descending: Boolean` | `(inferred)` |
| `artistSongsPreview` | `artistId: String, previewSize: Int = 3` | `Flow<List<Song>>` |
| `quickPicks` | `now: Long = System.currentTimeMillis()` | `Flow<List<Song>>` |
| `mostPlayedSongsStats` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()` | `Flow<List<SongWithStats>>` |
| `mostPlayedSongs` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()` | `Flow<List<Song>>` |
| `mostPlayedArtists` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()` | `Flow<List<Artist>>` |
| `mostPlayedAlbums` | `fromTimeStamp: Long, limit: Int = 6, offset: Int = 0, toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()` | `Flow<List<Album>>` |
| `artistAlbumsPreview` | `artistId: String, previewSize: Int = 6` | `Flow<List<Album>>` |
| `getPlayCountByMonth` | `songId: String?, year: Int, month: Int` | `Flow<Int>` |
| `forgottenFavorites` | `now: Long = System.currentTimeMillis()` | `Flow<List<Song>>` |
| `song` | `songId: String?` | `Flow<Song?>` |
| `getSongByIdBlocking` | `songId: String` | `Song?` |
| `getSongsByIdsChunk` | `songIds: List<String>` | `List<Song>` |
| `getSongsByIds` | `songIds: List<String>` | `List<Song>` |
| `videos` |  | `Flow<List<Song>>` |
| `downloadedVideos` |  | `Flow<List<Song>>` |
| `downloadedVideosByCreateDateAsc` |  | `Flow<List<Song>>` |
| `downloadedVideosByNameAsc` |  | `Flow<List<Song>>` |
| `downloadedVideosByPlayTimeAsc` |  | `Flow<List<Song>>` |
| `downloadedVideosSorted` | `sortType: SongSortType, descending: Boolean` | `Flow<List<Song>>` |
| `songArtistMap` | `songId: String` | `List<SongArtistMap>` |
| `allSongs` |  | `Flow<List<Song>>` |
| `allArtistsByPlayTime` |  | `Flow<List<Artist>>` |
| `getSetVideoId` | `videoId: String` | `SetVideoIdEntity?` |
| `upsertSetVideoId` | `entity: SetVideoIdEntity` | `Unit` |
| `format` | `id: String?` | `Flow<FormatEntity?>` |
| `lyrics` | `id: String?` | `Flow<LyricsEntity?>` |
| `purgeRefreshableLyrics` |  | `Int` |
| `artistsByCreateDateAsc` |  | `Flow<List<Artist>>` |
| `artistsByNameAsc` |  | `Flow<List<Artist>>` |
| `allWhitelistedArtistsByName` |  | `Flow<List<Artist>>` |
| `allKidsArtistsByName` |  | `Flow<List<Artist>>` |
| `artistsBySongCountAsc` |  | `Flow<List<Artist>>` |
| `artistsByPlayTimeAsc` |  | `Flow<List<Artist>>` |
| `artistsBookmarkedByCreateDateAsc` |  | `Flow<List<Artist>>` |
| `artistsBookmarkedByNameAsc` |  | `Flow<List<Artist>>` |
| `artistsBookmarkedBySongCountAsc` |  | `Flow<List<Artist>>` |
| `artistsBookmarkedByPlayTimeAsc` |  | `Flow<List<Artist>>` |
| `artists` | `sortType: ArtistSortType, descending: Boolean` | `(inferred)` |
| `artistsBookmarked` | `sortType: ArtistSortType, descending: Boolean` | `(inferred)` |
| `artist` | `id: String` | `Flow<Artist?>` |
| `albumsByCreateDateAsc` |  | `Flow<List<Album>>` |
| `albumsByNameAsc` |  | `Flow<List<Album>>` |
| `albumsByYearAsc` |  | `Flow<List<Album>>` |
| `albumsBySongCountAsc` |  | `Flow<List<Album>>` |
| `albumsByLengthAsc` |  | `Flow<List<Album>>` |
| `albumsByPlayTimeAsc` |  | `Flow<List<Album>>` |
| `albumsLikedByCreateDateAsc` |  | `Flow<List<Album>>` |
| `albumsLikedByNameAsc` |  | `Flow<List<Album>>` |
| `albumsLikedByYearAsc` |  | `Flow<List<Album>>` |
| `albumsLikedBySongCountAsc` |  | `Flow<List<Album>>` |
| `albumsLikedByLengthAsc` |  | `Flow<List<Album>>` |
| `albumsLikedByPlayTimeAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedByCreateDateAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedByNameAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedByYearAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedBySongCountAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedByLengthAsc` |  | `Flow<List<Album>>` |
| `albumsUploadedByPlayTimeAsc` |  | `Flow<List<Album>>` |
| `albums` | `sortType: AlbumSortType, descending: Boolean` | `(inferred)` |
| `albumsLiked` | `sortType: AlbumSortType, descending: Boolean` | `(inferred)` |
| `albumsUploaded` | `sortType: AlbumSortType, descending: Boolean` | `(inferred)` |
| `album` | `id: String` | `Flow<Album?>` |
| `albumUnfiltered` | `id: String` | `Flow<Album?>` |
| `albumWithSongs` | `albumId: String` | `Flow<AlbumWithSongs?>` |
| `albumArtistMaps` | `albumId: String` | `List<AlbumArtistMap>` |
| `playlistsByCreateDateAsc` |  | `Flow<List<Playlist>>` |
| `playlistsByUpdatedDateAsc` |  | `Flow<List<Playlist>>` |
| `playlistsByNameAsc` |  | `Flow<List<Playlist>>` |
| `playlistsBySongCountAsc` |  | `Flow<List<Playlist>>` |
| `playlists` | `sortType: PlaylistSortType, descending: Boolean` | `(inferred)` |
| `playlist` | `playlistId: String` | `Flow<Playlist?>` |
| `editablePlaylistsByCreateDateAsc` |  | `Flow<List<Playlist>>` |
| `playlistByBrowseId` | `browseId: String` | `Flow<Playlist?>` |
| `playlistDuplicatesChunk` | `playlistId: String, songIds: List<String>` | `List<String>` |
| `playlistDuplicates` | `playlistId: String, songIds: List<String>` | `List<String>` |
| `addSongToPlaylist` | `playlist: Playlist, songIds: List<String>` | `Unit` |
| `downloadedSongs` | `sortType: SongSortType, descending: Boolean, includeVideos: Boolean = true` | `Flow<List<Song>>` |
| `downloadedSongsByCreateDateAsc` | `includeVideos: Boolean` | `Flow<List<Song>>` |
| `downloadedSongsWhitelistedByCreateDateAsc` | `includeVideos: Boolean` | `Flow<List<Song>>` |
| `downloadedSongsByNameAsc` | `includeVideos: Boolean` | `Flow<List<Song>>` |
| `downloadedSongsByPlayTimeAsc` | `includeVideos: Boolean` | `Flow<List<Song>>` |
| `downloadedEpisodes` | `sortType: SongSortType, descending: Boolean` | `Flow<List<Song>>` |
| `downloadedEpisodesByCreateDateAsc` |  | `Flow<List<Song>>` |
| `downloadedEpisodesByNameAsc` |  | `Flow<List<Song>>` |
| `downloadedEpisodesByPlayTimeAsc` |  | `Flow<List<Song>>` |
| `updateDownloadedInfo` | `songId: String, downloaded: Boolean, date: LocalDateTime?` | `Unit` |
| `stampCacheDownloadDate` | `songId: String, date: LocalDateTime` | `Unit` |
| `setIsVideo` | `songId: String, isVideo: Boolean` | `Unit` |
| `uploadedSongsByCreateDateAsc` |  | `Flow<List<Song>>` |
| `uploadedSongsByNameAsc` |  | `Flow<List<Song>>` |
| `uploadedSongsByPlayTimeAsc` |  | `Flow<List<Song>>` |
| `uploadedSongsByRowIdAsc` |  | `Flow<List<Song>>` |
| `uploadedSongs` | `sortType: SongSortType, descending: Boolean` | `(inferred)` |
| `searchArtists` | `query: String, previewSize: Int = Int.MAX_VALUE` | `Flow<List<Artist>>` |
| `searchAlbums` | `query: String, previewSize: Int = Int.MAX_VALUE` | `Flow<List<Album>>` |
| `searchPlaylists` | `query: String, previewSize: Int = Int.MAX_VALUE` | `Flow<List<Playlist>>` |
| `events` |  | `Flow<List<EventWithSong>>` |
| `eventCount` |  | `Flow<Int>` |
| `eventsForBackfill` | `afterId: Long, maxId: Long, limit: Int` | `List<Event>` |
| `maxEventId` |  | `Long` |
| `likedSongsForBackfill` |  | `List<ActionSnapshotRow>` |
| `downloadedSongsForBackfill` |  | `List<ActionSnapshotRow>` |
| `firstEvent` |  | `Flow<EventWithSong?>` |
| `clearListenHistory` |  | `Unit` |
| `searchHistory` | `query: String = ""` | `Flow<List<SearchHistory>>` |
| `clearSearchHistory` |  | `Unit` |
| `incrementTotalPlayTime` | `songId: String, playTime: Long` | `Unit` |
| `updateEpisodePosition` | `songId: String, positionMs: Long` | `Int` |
| `episodePosition` | `songId: String` | `Long?` |
| `episodeResumePositions` |  | `Flow<Map<@MapColumn("") String, @MapColumn("") Long>>` |
| `incrementPlayCount` | `songId: String, year: Int, month: Int` | `Unit` |
| `incrementPlayCount` | `songId: String` | `Unit` |
| `inLibrary` | `songId: String, inLibrary: LocalDateTime?` | `Unit` |
| `addLibraryTokens` | `songId: String, libraryAddToken: String?, libraryRemoveToken: String?` | `Unit` |
| `hasRelatedSongs` | `songId: String` | `Boolean` |
| `getRelatedSongs` | `songId: String` | `Flow<List<Song>>` |
| `move` | `playlistId: String, fromPosition: Int, toPosition: Int` | `Unit` |
| `clearPlaylist` | `playlistId: String` | `Unit` |
| `artistByName` | `name: String` | `ArtistEntity?` |
| `artistEntity` | `id: String` | `Flow<ArtistEntity?>` |
| `bookmarkedPodcastChannels` |  | `Flow<List<ArtistEntity>>` |
| `getAllArtistIdsSync` |  | `List<String>` |
| `insert` | `song: SongEntity` | `Long` |
| `insert` | `artist: ArtistEntity` | `Unit` |
| `insertArtists` | `artists: List<ArtistEntity>` | `Unit` |
| `insert` | `album: AlbumEntity` | `Long` |
| `insert` | `playlist: PlaylistEntity` | `Unit` |
| `insert` | `map: SongArtistMap` | `Unit` |
| `insert` | `map: SongAlbumMap` | `Unit` |
| `insert` | `map: AlbumArtistMap` | `Unit` |
| `insert` | `map: PlaylistSongMap` | `Unit` |
| `insert` | `searchHistory: SearchHistory` | `Unit` |
| `insert` | `event: Event` | `Unit` |
| `insert` | `map: RelatedSongMap` | `Unit` |
| `insert` | `playCountEntity: PlayCountEntity` | `Long` |
| `insert` | `mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it }` | `Unit` |
| `insert` | `albumPage: AlbumPage` | `Unit` |
| `update` | `song: Song, mediaMetadata: MediaMetadata` | `Unit` |
| `update` | `song: SongEntity` | `Unit` |
| `update` | `artist: ArtistEntity` | `Unit` |
| `updateArtistThumbnailUrl` | `artistId: String, thumbnailUrl: String` | `Unit` |
| `applyWhitelistDisplayNames` |  | `Unit` |
| `whitelistDisplayNameSync` | `artistId: String` | `String?` |
| `replaceArtistThumbnailUrl` | `artistId: String, thumbnailUrl: String` | `Unit` |
| `update` | `album: AlbumEntity` | `Unit` |
| `update` | `playlist: PlaylistEntity` | `Unit` |
| `update` | `map: PlaylistSongMap` | `Unit` |
| `update` | `artist: ArtistEntity, artistPage: ArtistPage` | `Unit` |
| `update` | `album: AlbumEntity, albumPage: AlbumPage, artists: List<ArtistEntity>? = emptyList()` | `Unit` |
| `update` | `playlistEntity: PlaylistEntity, playlistItem: PlaylistItem` | `Unit` |
| `upsert` | `map: SongAlbumMap` | `Unit` |
| `upsert` | `lyrics: LyricsEntity` | `Unit` |
| `upsert` | `format: FormatEntity` | `Unit` |
| `upsert` | `song: SongEntity` | `Unit` |
| `delete` | `song: SongEntity` | `Unit` |
| `delete` | `songArtistMap: SongArtistMap` | `Unit` |
| `delete` | `artist: ArtistEntity` | `Unit` |
| `delete` | `album: AlbumEntity` | `Unit` |
| `delete` | `albumArtistMap: AlbumArtistMap` | `Unit` |
| `delete` | `playlist: PlaylistEntity` | `Unit` |
| `delete` | `playlistSongMap: PlaylistSongMap` | `Unit` |
| `delete` | `lyrics: LyricsEntity` | `Unit` |
| `delete` | `searchHistory: SearchHistory` | `Unit` |
| `delete` | `event: Event` | `Unit` |
| `playlistSongMaps` | `songId: String` | `List<PlaylistSongMap>` |
| `playlistSongMaps` | `playlistId: String, from: Int` | `List<PlaylistSongMap>` |
| `raw` | `supportSQLiteQuery: SupportSQLiteQuery` | `Int` |
| `checkpoint` |  | `Unit` |
| `upsert` | `whitelist: ArtistWhitelistEntity` | `Unit` |
| `insertWhitelist` | `whitelistEntries: List<ArtistWhitelistEntity>` | `Unit` |
| `getAllWhitelistedArtistIds` |  | `Flow<List<String>>` |
| `getAllWhitelistedArtistIdsSync` |  | `List<String>` |
| `getWhitelistEntry` | `artistId: String` | `ArtistWhitelistEntity?` |
| `getWhitelistEntriesSync` |  | `List<ArtistWhitelistEntity>` |
| `isArtistWhitelisted` | `artistId: String` | `Boolean` |
| `whitelistedArtistIdsSyncChunk` | `ids: List<String>` | `List<String>` |
| `whitelistedArtistIdsSync` | `ids: List<String>` | `List<String>` |
| `artistsByNameSync` | `name: String` | `List<ArtistEntity>` |
| `getWhitelistedArtistIdsMissingThumb` | `limit: Int` | `List<String>` |
| `clearWhitelist` |  | `Unit` |
| `recognitionHistory` |  | `Flow<List<RecognitionHistoryEntity>>` |
| `insertRecognitionHistory` | `entity: RecognitionHistoryEntity` | `Long` |
| `deleteRecognitionHistoryBySong` | `songId: String` | `Unit` |
| `deleteRecognitionHistory` | `entity: RecognitionHistoryEntity` | `Unit` |
| `clearRecognitionHistory` |  | `Unit` |
| `getSongIdsByArtist` | `artistId: String` | `List<String>` |
| `getAlbumIdsByArtist` | `artistId: String` | `List<String>` |
| `songAlbumIndex` | `songId: String, albumId: String` | `Int?` |
| `getAlbumSongCount` | `albumId: String` | `Int` |
| `deleteArtistById` | `artistId: String` | `Unit` |
| `deletePlayCountBySongsChunk` | `songIds: List<String>` | `Unit` |
| `deletePlayCountBySongs` | `songIds: List<String>` | `(inferred)` |
| `deleteFormatBySongsChunk` | `songIds: List<String>` | `Unit` |
| `deleteFormatBySongs` | `songIds: List<String>` | `(inferred)` |
| `deleteLyricsBySongsChunk` | `songIds: List<String>` | `Unit` |
| `deleteLyricsBySongs` | `songIds: List<String>` | `(inferred)` |
| `deleteSongsByIdsChunk` | `songIds: List<String>` | `Unit` |
| `deleteSongsByIds` | `songIds: List<String>` | `(inferred)` |
| `deleteAlbumsByIdsChunk` | `albumIds: List<String>` | `Unit` |
| `deleteAlbumsByIds` | `albumIds: List<String>` | `(inferred)` |
| `insertPodcastWhitelist` | `whitelistEntries: List<PodcastWhitelistEntity>` | `Unit` |
| `getAllWhitelistedPodcastIdsSync` |  | `List<String>` |
| `allWhitelistedPodcastsByName` |  | `Flow<List<PodcastWhitelistEntity>>` |
| `getPodcastWhitelistEntriesSync` |  | `List<PodcastWhitelistEntity>` |
| `clearPodcastWhitelist` |  | `Unit` |
| `subscribedPodcasts` |  | `Flow<List<PodcastEntity>>` |
| `podcast` | `id: String` | `Flow<PodcastEntity?>` |
| `upsertPodcast` | `podcast: PodcastEntity` | `Unit` |
| `updatePodcast` | `podcast: PodcastEntity` | `Unit` |
| `savedEpisodes` |  | `Flow<List<Song>>` |
| `continueListeningEpisodes` | `limit: Int = 20` | `Flow<List<Song>>` |
