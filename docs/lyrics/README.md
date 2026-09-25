# Lyrics

Files: `lyrics/` (provider chain core), `lyrics/{zemer,simpmusic,lrclib,youtube}/` (one package per source:
client + models + `LyricsProvider`), `ui/component/Lyrics.kt` (the pane), `ui/component/lyrics/LyricsComponents.kt`
(source header, now-playing bar, line-extra row), `ui/player/LyricsScreen.kt` (the lyrics view, hosted by
`ui/player/Player.kt`), `db/entities/LyricsEntity.kt`.

## Provider chain (`lyrics/LyricsHelper.kt`)
Default order (`LyricsProviderRegistry`, user-reorderable in Content settings): `ZemerLyricsProvider` →
`SimpMusicLyricsProvider` → `LrcLibLyricsProvider` → `YouTubeSubtitleLyricsProvider` → `YouTubeLyricsProvider`.
`LyricsHelper.getLyrics` returns `Fetched(lyrics, provider, lineExtras)`; the provider label is part of every
provider result (`LyricsProvider.getLabeledLyrics` → `LabeledLyrics`), so every path persists the same string in
`LyricsEntity.provider` (nullable; Room `AutoMigration(35, 36)`). Every fetch-and-persist goes through
`lyrics/LyricsStore` (`ensure` = the cache decision + chain + row policy, `refetch` = the menu's explicit
delete-then-refresh, single-flight per videoId); the service prefetch, the lyrics screen, the menu and the
download-completion prefetch (`MediaStoreDownloadManager`) call it and none re-implements the policy (`LyricsStoreTest`). `LyricsHelper` keys the videoId-based providers by
`MediaMetadata.id` (never `setVideoId`, which is a playlist-entry token).

* **Zemer** (`lyrics/zemer/`): `ZemerLyricsClient.resolve(videoId)` → `GET {ZEMER_LYRICS_BASE_URL}/lyrics/resolve`
  (BuildConfig; gradle `-PzemerLyricsBaseUrl=`; default `https://search.zemer.io`). The server returns SOURCE
  POINTERS, not third-party text; the app fetches each source itself (`ZemerLyricsProvider.bodies`):
  - `jkaraoke` feed page → `JkaraokeLrc` (line-synced LRC, measured times). The resolver's `offsetSec` (the song's
    own measured lead OR the fleet default) is added to every line (`ZemerLyricsProvider.jkaraokeOffset`); the
    default is applied too because it reduces the mean cue error across measured recordings. The feed sanity
    rules run on the raw starts.
  - `jyrics` → `JyricsParser`, `shironet` → `ShironetParser`, `zingmusic` track → `ZingParser` (all plain);
    `tab4u` chord sheet → `Tab4uParser` (plain, ≥ `MIN_LINES` = 6 lyric lines); `zemirotdb` → `ZemirotDbParser`
    (plain, the server's ≥ `MIN_WORDS` = 12 gate; a piyut may be ONE comma-joined line by design).
  - `youtube`: the YouTube Music lyrics tab by the server-vouched `browseId` → `ZemerLyricsClient.youtubeLyricsTab`
    (a direct browse, no `next()`). Trusted at rank 2 inside the resolver — a deliberate policy, the server
    verified the tab for that videoId — unlike the chain's own low-trust `YouTubeLyricsProvider`.
  - `lrclib` record by id → `ZemerLyricsClient.lrclibBody` (the server hands out only rows its audio check
    confirmed; LRC preferred, plain as fallback, instrumental or thin records yield nothing).
  - `zemer`: Zemer's own certified text, `richSync` (enhanced LRC with `<mm:ss.xx>` word tags) > `syncedLrc` >
    `plain`; a line without word tags is deliberately line-only.
  - `apple` row by `catalogId` → `AppleTtmlLrc` (paxsenix mirror `/apple-music/lyrics?id=`): a synced reply
    (`type: "Line"`) serves its ready `lrc` when `LyricsUtils.cleanLrc` accepts it (`[by:…]` credit dropped,
    monotonic timed lines only), else its TTML, each `<p begin>` becoming `[mm:ss.xx] text` (inner word `<span>`s
    dropped, Apple's own line times, ≥ 4 monotonic lines). An UNSYNCED reply (`type: "None"`) goes STRAIGHT to its
    `plain` text with the bracketed section labels dropped (≥ 4 lines) — its TTML is never consulted, so a mirror
    stamping `begin="0:00.000"` on every line can never show it synced at zero. Goldens:
    `apple-1571752969.json` → `.expected.lrc`, `apple-unsynced-reply.json`.
  - `simpmusic` row by `entryId` → `SimpMusicLyrics.getLyricsByEntry` (the catalog named by the pointer's additive
    `videoId`, else the track's own; the exact audio-verified entry picked by id, no duration matching; timings
    served only under the server's `synced` flag, else plain — `entryBody`, and a synced pointer whose entry lost
    its timings yields nothing, never plain text in the synced slot; a missing entry yields nothing).
  - `kugou` row by `hash` + `krcId` → `KugouLrc` (re-runs the krcs search by hash, takes that exact candidate's
    accesskey, decodes the `fmt=lrc` body).
  - `lyricstranslate`: the server-inlined `plain` text, else the page → `LyricsTranslateParser`.
  - `booklet`/`manual`/`canonical`/`community`: inline text.

  **Walk order** (`ZemerLyricsProvider.order`): synced first (`synced`, an inline `syncedLrc`/`richSync`, or the
  one pointer the resolver's `lineTimes` were measured against), then `rank`: `zemer` 0 > `jkaraoke`/`apple` 1 >
  `lrclib`/`kugou`/`simpmusic`/`zingmusic`/`youtube` 2 > `jyrics`/`shironet`/`tab4u`/`zemirotdb`/`lyricstranslate`
  3 > `booklet`/`manual`/`canonical`/`community` 4 (inline bodies stay behind the pointers: the pointer is the
  fresher copy, the inline text the outage fallback); unknown types are skipped, never guessed; the server's order
  breaks ties. Each source's fetch/parse runs under `runCatching` (rethrowing `CancellationException`), so a dead
  or throwing source is skipped, never the walk. `bodies(firstOnly = true)` stops at the first source that yields
  text, so the auto-fetch path does not download every source. The informational extras (`publicDomain`,
  `borrowedFrom`, `syncedTruncated`, `wordSyncPartial`, `provenance`, `admittedBy`, `Resolved.syncTruncated`,
  `LineTimes.offsetSec` — already folded into `times`) are parsed and not acted on.

  The page parsers are byte-identical ports of the server's, pinned by golden files under
  `app/src/test/resources/lyrics/` (`JyricsParserGoldenTest`, `ShironetParserGoldenTest`, `ZingParserGoldenTest`,
  `JkaraokeLrcGoldenTest`, `Tab4uParserGoldenTest`, `ZemirotDbParserGoldenTest`, `AppleTtmlLrcGoldenTest`,
  `SyncIntegrationTest`). `HtmlEntities.unescape` is shared by `JyricsParser`, `ShironetParser`, `AppleTtmlLrc` and
  `LyricsTranslateParser`; the `LyricsUtils.hasLyricBody` gate (`MIN_LYRIC_LINES` = 4 non-blank lines) applies to
  jyrics / shironet / zingmusic / youtube / simpmusic / lyricstranslate bodies.

  **Label:** `Zemer · <source>` (the lyrics header shows just "Zemer"; the sub-source stays in the stored label for
  reports); Zemer's own text is just `Zemer`; a `manual` row's suffix is its `origin` display name
  (`ZemerLyricsProvider.originName`: `Telegram`, `verified` for `asrverified`, `Apple Music`, `YouTube`, an unknown
  slug such as `forum` as-is).
* **`lineTimes`** (`lyrics/zemer/LineTimesLrc.kt`, `LineTimesLrcTest`): measured line START times for a pointer's
  OWN text (`{"type", "count", "times": [s], "keys": [8-hex]}`). No text travels: each timed line has a text-free
  key — `lineKey` = NFC → strip Hebrew points/cantillation U+0591..U+05C7 → lowercase → keep only Unicode letters
  (L*) and numbers (N*) → SHA-1 → first 8 hex, the server's `corpus/lyrics.mjs#lineKey`, pinned by vectors.
  `apply(plain, lineTimes)` keys the body's non-blank lines and pairs them MONOTONELY (a repeated chorus takes
  successive timed occurrences; output is always in time order), only to the body of the source `lineTimes.type`
  names (never another pointer's text, never an already-synced body). It syncs only when ≥ `MIN_MATCHED_SHARE`
  (85 %, the server's rule — below it the server sends `syncTruncated` instead) of the BODY's lines found a time (a
  timed line absent from a drifted-shorter body costs nothing), else the body stays plain. An unmatched line inside
  a passing body rides the preceding matched line's tag (a leading one rides the first) — the equal-time
  continuation the server's own `syncedLrc` bodies use — so no text is dropped and no line is ever given an
  estimated time. Keys, not counts: `zing-1340.json` + `resolve-zingmusic-linetimes.json` pin a body that drifted
  since it was timed and still syncs with every line kept.
* **`lineExtras`** (`lyrics/LineExtras.kt` + `lyrics/LineExtrasStore.kt`; `LineExtrasTest` / `LineExtrasStoreTest` /
  `LyricsStoreTest`; contract `handoff-docs/zemer-app-line-extras.md`): a per-line translation / romanization
  (`{"keys": [8-hex], "en"?/"he"?/"yi"?/"roman"?: [...], "source": "machine"}`, additive; `""` where a line
  has none). Paired by the SAME `lineKey` as `lineTimes`, never by index (`LineExtras.forLines`); an unkeyed line
  gets nothing. Rendered by the shared `LyricsLineExtra` under the sung line, ONE language at a time from
  `LyricsLineExtrasKey` (`LineExtrasLanguage`: OFF / ENGLISH / HEBREW / YIDDISH / ROMANIZED, **default OFF** — nothing
  changes until the user picks one, in Appearance → lyrics or the lyrics menu's `ListPickerDialog`);
  `source: "machine"` adds ONE " · machine translation" to the source header (`LyricsSourceHeader(machineTranslation)`),
  never a per-line label.
  - **Storage:** one JSON file per videoId under `filesDir/lyrics-extras/` (NO lyrics-table migration). A record
    never outlives its row: every chain answer re-records or clears it, refetch deletes it first.
  - **Resolve-time extras on demand:** a row cached before the feature (or answered by another provider) gets ONE
    resolver call (`LyricsStore.ensureResolveExtras`, from `LyricsLineExtrasViewModel.bind`, only once a language
    is picked and the song has a body); a "none" record is re-asked after `EMPTY_TTL_MS` (7 days), a record with
    extras only on refetch.
  - **Aligned extras:** the resolve-time field pairs only where the displayed text is the server's own, so the pane
    also asks `POST /lyrics/extras` with the lines EXACTLY as displayed (any provider's body) plus `lang`
    (`LineExtrasLanguage.wireLang`: `en`/`he`/`yi`; ROMANIZED rides the `en` request and reads `roman`) and gets
    arrays parallel to those lines (`ZemerLyricsClient.extrasForLines`, `LyricsStore.ensureExtras`), once per song +
    language + displayed body (`LineExtras.linesHash`); a 404 is a dated negative, a network failure records
    nothing. Stored as `LineExtrasRecord.aligned` in the same file (kept across chain answers, dropped on refetch);
    `LineExtrasStore.flow(videoId, language, lines)` serves the aligned reply for the current body when there is
    one, else the resolve-time extras.
  - **One lock:** every extras read → ask → write, and a refetch's delete → record, run under ONE per-videoId lock
    (`LyricsStore.withExtrasLock`): concurrent binds resolve a song once, and an ask that raced a refetch can never
    land a stale record after the delete.
* **SimpMusic** (`lyrics/simpmusic/`): keyed by videoId. A track is this recording only when its duration is known
  and within `IDENTITY_TOLERANCE_SEC` (5 s) of ours (`sameRecording`); anything else is a miss, never plain text —
  an unverifiable entry is never "probably right". Known hole, NOT closable client-side: the catalog is
  community-filled, so a wrong-text upload matching title, artist and duration passes every gate; only Report and
  the Zemer server's own text remedy it. Prefers `richSyncLyrics` > `syncedLyrics` > `plainLyrics`, and a synced
  body only within `SYNC_TOLERANCE_SEC` (1 s) — otherwise plain (`syncAllowed`; an unknown duration never syncs).
  Downloads embed only this identity-exact body, through `LyricsUtils.stripWordTags` (plain LRC, never `<mm:ss.xx>`
  word tags; `MediaStoreDownloadManager`).
* **LrcLib** (`lyrics/lrclib/`): title/artist keyed. `LrcLib.identityMatches` requires title ≥ 0.75 AND artist
  ≥ 0.75 similarity AND (when the player knows it) duration within 3 s — duration alone once served a wrong-language
  song (`LrcLibIdentityTest`). The artist side passes when ANY credited artist matches (`creditedArtists` splits a
  joined credit). `LrcLib.pickBody` serves a synced body only from a `syncable` track (non-blank, within 1 s); a
  track inside the identity gate but outside the sync gate yields plain text or nothing.

## Content settings (`ui/screens/settings/LyricsProviderDialogs.kt`)
Provider selection = one shared `SwitchPreference` row per toggle (D-pad focusable); provider priority = the shared
`ReorderableList` over the pure `LyricsProviderOrdering` (enabled providers only; a drag keeps disabled providers
behind the enabled ones; `LyricsProviderOrderingTest`, `LyricsProviderRegistryTest`).

## Sync rendering (`lyrics/LyricsUtils.kt`, `ui/component/Lyrics.kt`)
* Line sync: LRC `[mm:ss.xx]`; `findCurrentLineIndex` with `LINE_LOOKAHEAD_MS = 150`.
* Word sync: enhanced LRC `<mm:ss.xx>` tags → `LyricsEntry.words`; `sungWordCount` + the `LyricsWordSyncKey`
  toggle (Appearance). Only MEASURED word times are rendered (no estimates).
* `LyricsSyncOffsetKey` (Appearance slider, ±1.5 s in 50 ms steps, Reset → 0) is added to the position before
  line/word lookup.
* Layout: lyrics start at the top; the active synced line is held at `ACTIVE_LINE_ANCHOR` (30% of the pane).
* `LyricsUtils.cleanLrc` formats with `Locale.US` (a comma-decimal locale produces unparseable LRC).

## Lyrics view (`ui/player/LyricsScreen.kt`, hosted by `ui/player/Player.kt`)
The Player's lyrics bottom sheet (`lyricsSheetState`, expanded by `onShowLyrics`) hosts `LyricsScreen`: `LyricsSourceHeader` ("Lyrics from X ·
synced", only once a real body exists) + the lyrics menu + the shared `Lyrics` pane, reusing the Player's own
transport row and slider (`PlayerTransportRow`) and the `LyricsComponents.kt` pieces — never re-roll them. Its
repeat button's content description follows `repeatModeContentDescriptionRes(repeatMode)`.

## Pick rule (`lyrics/SyncedFirstPicker`) and walk schedule (`lyrics/LyricsChainWalk`), both JVM-tested
Providers come in the user's order from ONE DataStore snapshot (`LyricsHelper.enabledProviders(prefs)`: the order
key plus every provider's `enabledKey`), never a blocking read per provider. Among trusted providers the pick stops
at the first SYNCED body and otherwise serves the first plain one. The YouTube providers are `lowTrust` (an
auto-caption transcript is timestamped but not identity-gated), so they are served only when no trusted provider
answered — never over a curated Zemer plain body (`SyncedFirstPickerTest`). `LyricsChainWalk` decides WHEN each
provider is asked without changing that answer (`LyricsChainWalkTest` pins the equivalence): (1) the primary trusted
provider alone — a synced answer ends the walk with no other request; (2) the remaining trusted providers
CONCURRENTLY, offered in priority order; (3) the low-trust providers only when NO trusted provider answered,
concurrently — so they are deferred even when the user order lists them first. Each provider's elapsed time is a
`Lyrics <name> answered|no answer in N ms` Timber breadcrumb.

The walk runs STRUCTURED under its caller (`LyricsHelper.getLyrics` → `LyricsChainWalk.run`, its concurrent stages in a `coroutineScope`):
a cancelled caller (a prefetch skipped to the next track) cancels the in-flight provider fetches, and the fetch
lambda rethrows `CancellationException` so a skipped track is never reported as a provider failure. There is no
in-memory lyrics cache; Room via `LyricsStore` is the cache.

## Feedback (`lyrics/zemer/LyricsFeedback`, JVM-tested)
"Report wrong lyrics" (after the shared `ConfirmDialog`) and a saved edit POST through
`LyricsMenuViewModel.feedback` on `viewModelScope` (`ZemerLyricsClient.submitLyrics/reportLyrics`). Never launch
them on the menu sheet's `rememberCoroutineScope`: it is cancelled the frame the sheet is dismissed, and the report
action dismisses first (`LyricsFeedbackTest`).

## Cache hygiene (`LyricsEntity.needsFetch` / `LyricsEntity.resolved`, applied by `LyricsStore`)
`LyricsScreen` (on open) calls `LyricsStore.ensure`, and `MusicService` PREFETCHES on every track start, pane open
or not: `LyricsStore.prefetch(current, next, connected)` runs `ensure` for the playing song and the next queue item
(`LYRICS_PREFETCH_DELAY_MS` = 3 s after the track change so the chain never competes with stream resolution;
`collectLatest` drops a pending prefetch when the track changes first; skipped offline so no not-found rows are
minted that would hide lyrics once online). Opening the pane is then a Room read — never regress it to a live walk.

* `ensure` runs the chain only when `needsFetch`: nothing cached, or a legacy row (provider null) with a real body.
* A `LYRICS_NOT_FOUND` row is a negative cache and is never re-fetched by `ensure`.
* A legacy PLAIN body is always kept and stamped `provider = "legacy"` (shown as unknown provenance): pre-provider
  manual entries are indistinguishable from old auto-cached rows and must not be silently replaced. A legacy SYNCED
  body is replaced when the chain answers: nobody types timestamps, so it is an old ungated LrcLib match
  (`LyricsCachePolicyTest`).
* The menu's Refetch (`LyricsStore.refetch`) DELETES the row first (the pane visibly clears and reloads even when the
  chain answers the same body) and stores a fresh answer; the open screen's own `ensure` joins the same single-flight
  walk (`LyricsStoreTest`).
* **Cache refresh is by CHAIN GENERATION**, not one-off booleans: `LyricsEntity.CHAIN_GENERATION` (currently 2;
  bump it when the chain gains sources or sync it did not have) vs the persisted `LyricsChainGenerationKey`; an
  install below it queues `DatabaseDao.purgeRefreshableLyrics` once (fire-and-forget `database.query`) — drops
  not-found rows (the chain may cover the song now) and auto-cached PLAIN rows (it may sync them now), keeps every
  synced body, `manual` and `legacy` rows — and stores the generation without waiting for the purge to run. The DAO
  query is Room and not JVM-testable here.
* No further DB migrations for lyrics without an explicit decision (the 35→36 `provider` column is the one that
  exists). New lyrics strings go in the default-English files only (`values-iw/` is managed separately).
