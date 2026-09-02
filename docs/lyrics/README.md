# Lyrics

Code-derived. Files: `lyrics/` (provider chain), `lyrics/zemer/` (Zemer resolver provider + parser ports),
`ui/component/Lyrics.kt` (the pane), `ui/component/lyrics/LyricsComponents.kt` (source header + now-playing bar),
`ui/player/LyricsScreen.kt` (the lyrics view), `ui/player/Player.kt` (hosts it), `db/entities/LyricsEntity.kt`.

## Provider chain (`lyrics/LyricsHelper.kt`)
Order: `ZemerLyricsProvider` → `SimpMusicLyricsProvider` → `LrcLibLyricsProvider` → `YouTubeSubtitleLyricsProvider`
→ `YouTubeLyricsProvider`. The first success wins; `LyricsHelper.getLyrics` returns `Fetched(lyrics, provider)` and
the provider label is persisted in `LyricsEntity.provider` (nullable; Room `AutoMigration(35, 36)`). The label is
part of every provider result (`LyricsProvider.getLabeledLyrics`/`getAllLabeledLyrics` → `LabeledLyrics`), so the
auto-fetch path and the picker persist the same string for the same source. `LyricsHelper` keys the videoId-based
providers by `MediaMetadata.id` (never `setVideoId`, which is a playlist-entry token).

* **Zemer** (`lyrics/zemer/`): `ZemerLyricsClient.resolve(videoId)` → `GET {ZEMER_LYRICS_BASE_URL}/lyrics/resolve`
  (BuildConfig; gradle `-PzemerLyricsBaseUrl=`; default `https://search.zemer.io`). The server returns SOURCE
  POINTERS, not third-party text; the app fetches each source itself: jkaraoke feed page → `JkaraokeLrc`
  (line-synced LRC, measured times), Jyrics page → `JyricsParser` (plain), booklet/canonical text inline.
  Both parsers are byte-identical ports of the server's, pinned by golden files under
  `app/src/test/resources/lyrics/` (`JyricsParserGoldenTest`, `JkaraokeLrcGoldenTest`, `SyncIntegrationTest`).
  Provider label: `Zemer · <source>[ ✓]` (✓ = two independent sources agreed). `bodies(firstOnly = true)` stops at the
  first source that yields text, so the auto-fetch path does not download every source.
* **SimpMusic**: keyed by videoId; prefers `richSyncLyrics` (word tags) > `syncedLyrics` > `plainLyric`, but a synced/word
  body is used only when the source track is within `SYNC_TOLERANCE_SEC` (1 s) of ours — otherwise plain text
  (`syncAllowed`; an unknown `durationSeconds` is accepted, the entry is the same recording by videoId). Downloads embed
  `LyricsUtils.stripWordTags(...)` of that body: plain LRC, never `<mm:ss.xx>` word tags.
* **LrcLib**: title/artist keyed. `LrcLib.identityMatches` requires title ≥ 0.75 AND artist ≥ 0.75 similarity AND
  duration within 3 s — a duration-only match served a Japanese song for a Baruch Levine track before this gate
  (`lrclib/src/test/.../LrcLibIdentityTest.kt`). `LrcLib.pickBody` serves a synced body only from a `syncable` track
  (non-blank, within 1 s); a track inside the identity gate but outside the sync gate yields plain text or nothing.

## Sync rendering (`lyrics/LyricsUtils.kt`, `ui/component/Lyrics.kt`)
* Line sync: LRC `[mm:ss.xx]`; `findCurrentLineIndex` with `LINE_LOOKAHEAD_MS = 150`.
* Word sync: enhanced LRC `<mm:ss.xx>` tags → `LyricsEntry.words`; `sungWordCount` + the `LyricsWordSyncKey`
  toggle (Appearance). Only MEASURED word times are ever rendered (no estimates).
* `LyricsSyncOffsetKey` (Appearance `SliderPreference`, ±1500 ms in 50 ms steps, Reset → 0) is added to the position
  before line/word lookup.
* Layout: lyrics start at the top; the active synced line is held at `ACTIVE_LINE_ANCHOR` (30% of the pane).

## Lyrics view (`ui/player/LyricsScreen.kt`, hosted by `ui/player/Player.kt`)
The `ShowLyricsKey` preference (`showLyrics`) opens `LyricsScreen` inside the Player: `LyricsSourceHeader` ("Lyrics
from X · synced", only once a real body exists) + the lyrics menu + the shared `Lyrics` pane, with the Player's own
transport row and slider reused through `PlayerTransportRow` and the `ui/component/lyrics/LyricsComponents.kt`
pieces. Its repeat button's content description follows `repeatModeContentDescriptionRes(repeatMode)`.

## Cache hygiene (`LyricsEntity.needsFetch` / `LyricsEntity.resolved`)
`MusicService` runs the chain only past the `showLyrics`/`isEpisode` guard and only when `needsFetch`: nothing cached,
or a legacy row (provider null) with a real body. A `LYRICS_NOT_FOUND` row is a negative cache and is never re-fetched.
A legacy row that the chain cannot resolve is kept and stamped `provider = "legacy"` (shown as unknown provenance),
because pre-provider manual entries are indistinguishable from old auto-cached rows and must not be discarded.
The one-time purge (`DatabaseDao.purgeUntrustedLyrics`, flag `LyricsCachePurgeDoneKey`) deletes only `LrcLib` rows
(old duration-only match) and legacy not-found rows.
No further DB migrations are to be added for lyrics without an explicit decision.
