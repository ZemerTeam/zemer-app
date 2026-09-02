# Lyrics

Code-derived. Files: `lyrics/` (provider chain), `lyrics/zemer/` (Zemer resolver provider + parser ports),
`ui/component/Lyrics.kt` (the pane), `ui/component/lyrics/InlineLyrics.kt` (lyrics mode inside the Player),
`ui/player/Player.kt` (hosts it), `db/entities/LyricsEntity.kt`.

## Provider chain (`lyrics/LyricsHelper.kt`)
Order: `ZemerLyricsProvider` → `SimpMusicLyricsProvider` → `LrcLibLyricsProvider` → `YouTubeSubtitleLyricsProvider`
→ `YouTubeLyricsProvider`. The first success wins; `LyricsHelper.getLyrics` returns `Fetched(lyrics, provider)` and
the provider label is persisted in `LyricsEntity.provider` (nullable; Room `AutoMigration(35, 36)`).

* **Zemer** (`lyrics/zemer/`): `ZemerLyricsClient.resolve(videoId)` → `GET {ZEMER_LYRICS_BASE_URL}/lyrics/resolve`
  (BuildConfig; gradle `-PzemerLyricsBaseUrl=`; default `https://search.zemer.io`). The server returns SOURCE
  POINTERS, not third-party text; the app fetches each source itself: jkaraoke feed page → `JkaraokeLrc`
  (line-synced LRC, measured times), Jyrics page → `JyricsParser` (plain), booklet/canonical text inline.
  Both parsers are byte-identical ports of the server's, pinned by golden files under
  `app/src/test/resources/lyrics/` (`JyricsParserGoldenTest`, `JkaraokeLrcGoldenTest`, `SyncIntegrationTest`).
  Provider label: `Zemer · <source>[ ✓]` (✓ = two independent sources agreed).
* **SimpMusic**: keyed by videoId; prefers `richSyncLyrics` (word tags) > `syncedLyrics` > `plainLyric`.
* **LrcLib**: title/artist keyed. `LrcLib.identityMatches` requires title ≥ 0.75 AND artist ≥ 0.75 similarity AND
  duration within 3 s — a duration-only match served a Japanese song for a Baruch Levine track before this gate
  (`lrclib/src/test/.../LrcLibIdentityTest.kt`).

## Sync rendering (`lyrics/LyricsUtils.kt`, `ui/component/Lyrics.kt`)
* Line sync: LRC `[mm:ss.xx]`; `findCurrentLineIndex` with `LINE_LOOKAHEAD_MS = 150`.
* Word sync: enhanced LRC `<mm:ss.xx>` tags → `LyricsEntry.words`; `sungWordCount` + the `LyricsWordSyncKey`
  toggle (Appearance). Only MEASURED word times are ever rendered (no estimates).
* `LyricsSyncOffsetKey` (Appearance slider, ±1500 ms) is added to the position before line/word lookup.
* Layout: lyrics start at the top; the active synced line is held at `ACTIVE_LINE_ANCHOR` (30% of the pane).

## Lyrics mode (`ui/player/Player.kt` + `ui/component/lyrics/InlineLyrics.kt`)
There is NO separate lyrics screen. The queue bar's lyrics button toggles `showInlineLyrics`; the Player's art slot
(both orientations) then shows `InlineLyrics` — `LyricsSourceHeader` ("Lyrics from X · synced", only once a real
body exists) + the lyrics menu + the shared `Lyrics` pane — while the Player's own title row, slider and transport
are reused untouched. Back leaves lyrics mode first. Entering lyrics mode reverts inline video to audio (DESIGN §4).

## Cache hygiene
`MusicService` re-fetches a cached row whose `provider` is null, and runs a one-time data-only purge
(`DatabaseDao.purgeUntrustedLyrics`, flag `LyricsCachePurgeDoneKey`) of rows with provider null or `LrcLib`.
No further DB migrations are to be added for lyrics without an explicit decision.
