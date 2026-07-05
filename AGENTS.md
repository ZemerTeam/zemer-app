# Working with Zemer as an AI agent

Zemer is a "Kosher" YouTube Music client for Android (Kotlin, Jetpack Compose, Material 3), forked from [Metrolist](https://github.com/MetrolistGroup/Metrolist) with content-filtering layered on top (artist whitelist, KidZone, per-artist flags like `isFemale`/`isChasid`). The shared library modules keep the **`com.metrolist.*`** package namespace while the app is **`com.jtech.zemer`** — that split is intentional, don't "fix" it.

## Project rules

1. Pull the latest `main` before starting, to minimize merge conflicts.
2. Commit messages follow `type(scope): short description` (e.g. `fix(player): skip HEAD validation for WEB_REMIX`, `feat(ui): add history button`); the scope is optional.
3. User-facing strings: add/edit **only** the default English `app/src/main/res/values/metrolist_strings.xml`. Do **not** edit `strings.xml` or any translated `metrolist_strings.xml` — other locales are managed separately.
4. Database schema changes (`app/.../db/MusicDatabase.kt` + entities) require a versioned Room migration and are high-risk — confirm with a human before changing the schema.
5. Don't rename the `com.metrolist.*` library namespace, and don't bump the app version — version bumps are a release-team decision.
6. Follow Kotlin/Android best practices; prioritize performance, battery, and maintainability.

## Working agreement

- **Do not commit, push, or merge unless explicitly asked in the current request.** When you are authorized, doing so is fine and the responsibility lies with the requester. Never rewrite git history, force-push (except rebasing your own branch), or delete branches without explicit instruction.
- **Never commit secrets** — `innertube_cookie.txt`, cookies / poTokens, `release.keystore`, `google-services.json` are gitignored; keep them that way.
- Edit README / docs only when that is the task, not as a side effect.
- Ask a human when requirements are unclear; don't assume. Add comments only for complex or non-obvious logic.

## Engineering rules (non-negotiable)

- **Regression tests are required** for every behavioral change or bug fix wherever a test does not demand heavy new infrastructure (plain JVM/unit tests, Robolectric, or the `tests/` streaming harness for stream/cipher/poToken work). "It builds" and "I watched it work once" are not regression protection. If a fix genuinely cannot be tested without heavy new infrastructure, say so explicitly in the change description instead of skipping silently.
- **Keep code modular.** No new god files: split by responsibility (screen scaffolding vs. business logic vs. data access). New logic goes behind small, single-purpose functions/classes — not appended to `MainActivity.kt`, `OnboardingScreen.kt`, `MusicService.kt`, or other existing giants; shrink them when touching them.
- **Keep it professional.** Code must pass the bar of an external staff-engineer review: layering respected (UI does not run database/network calls inline), errors handled rather than swallowed, user-facing strings localized, no copy-pasted near-duplicates, no dead code left behind.

## Build & run

- **JDK 21**, `compileSdk`/`targetSdk` 36, `minSdk` 26. Native code targets `arm64-v8a` + `armeabi-v7a` only (NDK 27). There are no product flavors.
- `./gradlew :app:assembleDebug` — debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:assembleRelease` — release APK. **Build BOTH after any change**: release runs R8 (`isMinifyEnabled = true`) and catches shrink/keep-rule breakage that debug never will.
- Submodules are required: `git submodule update --init --recursive` (`cipher/` and the native `app/src/main/cpp/bento4`). CI pulls a prebuilt bento4 from `ZemerTeam/zemer-bento4`.
- Install to a connected device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Stream resolution logs under logcat tag `YTPlayerUtils` (also `PoTokenWebView`, `Zemer_CipherFnExtract`).
- CI: `.github/workflows/release-build.yml` builds a signed release on push to `main` / PRs (skips `docs/**`, `tests/**`, `**.md`); keystore + `google-services.json` come from base64 secrets.

## Architecture & the danger zones

### The streaming pipeline (the core; where things break)

`app/.../utils/YTPlayerUtils.kt` `playerResponseForPlayback()` is the heart of the app. It:
1. Tries `WEB_REMIX` (main client), then a user-configurable `STREAM_FALLBACK_CLIENTS` list (VISIONOS, WEB_CREATOR, ANDROID_VR, TVHTML5, IOS/IPADOS, …) — order/enable-state are settable in the Stream Sources setting.
2. For web clients, deciphers the `signatureCipher` (sig + n-transform) via the **`cipher` submodule**, then appends a BotGuard `pot=` token.
3. Validates, then hands the URL to ExoPlayer in `MusicService`.

Two hard-won facts that govern this area — always verify against the live CDN via `tests/`, never reason from convention (the convention was wrong here):
- **googlevideo serves the first 1 MiB of a stream free, then 403s every new connection** unless the URL's `&pot=` is bound to the **videoId** (not visitorData). Clients whose attestation the web poToken can't satisfy (IOS/IPADOS — and MWEB, which was removed for this reason) 403 past the wall under every binding.
- **`validateStatus` does a HEAD that false-negatives** (403 on URLs that GET fine), so WEB_REMIX intentionally skips it.

### Cipher / player rotation (the most common future break)

The `cipher` submodule (package `com.zemer.cipher`, repo `ZemerTeam/zemer-cipher`) deciphers YouTube's `player_ias` signatures in an Android WebView and mints poTokens. It's wired **two ways**: a git submodule *and* a Gradle composite build — `includeBuild("cipher")` in `settings.gradle.kts` substitutes `com.zemer:cipher` → the local `:library`, so the app always builds the working tree.

YouTube rotates `player_ias` frequently. Player configs live in **one JSON file**: `cipher/library/src/main/assets/player_configs.json` — per player the sig call expression (e.g. `mP(4,155,INPUT)`), the n-transform URL class (e.g. `Yx`), the STS, and the md5-of-first-10000-bytes alias. That single file is (1) bundled in the APK as the offline default, (2) **fetched at runtime from raw zemer-cipher `master`** by `PlayerConfigStore` (6 h TTL + ETag, plus a forced refresh + one retry the moment an unknown hash breaks deciphering), and (3) read by the `tests/` harness — so **a config pushed to cipher `master` fixes deployed apps within minutes, no APK release**. Parsing/validation is `PlayerConfigParser` (strict regexes; the n-IIFE is built from a local template — remote data can never inject free-form JS into the WebView; invalid entries are skipped, invalid files — including any duplicate hash/alias key — are rejected wholesale and the previous table kept). The validation rules exist in TWO readers (the Kotlin parser and `tests/player-configs.mjs`); file-level accept/reject verdicts and the n-IIFE template are pinned byte-for-byte by shared fixtures in `cipher/library/src/test/resources/config-parity/` — a rule change must update both readers AND the fixtures, or one of the two test suites goes red. When adding a config:
- **Validate empirically**: `node tests/validate-player-config.mjs <hash>` deciphers a real stream and checks the CDN returns **HTTP 206**. That 206 is ground truth, not regex extraction — multiple constant pairs can decipher correctly, only the live response confirms which the server accepts. It prints a paste-ready JSON entry (and re-validates the committed entry first if one exists).
- Add the entry (with its MD5 alias) to `player_configs.json` only — there are no Kotlin/harness mirrors to sync anymore; unit tests in `cipher/library/src/test/` guard the file's shape. Then run `node tests/gen-player-dates.mjs` to refresh `player_dates.json` (a **separate, cosmetic** file mapping each hash to the commit date support was added, shown in the song-details sheet via `PlayerDatesStore`). It is deliberately decoupled: old apps never fetch it, and a malformed/missing dates file only blanks a UI label — deciphering is never affected.
- **Push to cipher `master` is the deploy**: that is the URL devices fetch. Bump the submodule pointer in `zemer-app` afterwards so bundled defaults stay fresh (push order: `zemer-cipher` first, then the pointer — reverse breaks fresh clones / CI).
- A cipher *scheme* change (new config shape, not just a new hash) still needs code + an APK; bump `schemaVersion` only on breaking shape changes — old apps reject newer schema files and keep their last-good table.
- `.github/workflows/player-monitor.yml` checks hourly: it fetches the live raw `master` file once (the submodule copy is only a warned-about fallback) and **multi-samples** the live player surfaces via `tests/scan-live-players.mjs` (30× `iframe_api` + `music.youtube.com`) so a low-rate A/B **canary** — served ~1/6 of the time, which a single sample misses ~83% of the time — is caught the first hour it appears, not once it has already rotated in. "Known" is still decided by the harness loader (`parsePlayerConfigs`, the app's validation rules) against real keys + md5 aliases, so a pushed-but-invalid entry counts as UNKNOWN and still alerts. Opens one issue per unknown hash + a summary email, but does **not** auto-commit — the config is added by hand.

### Accounts: personal vs anonymous (pooled) — `SAPISID` ≠ logged in

There are two signed-in states and telling them apart is non-obvious. A **personal** Google login sets a **`dataSyncId`**. The **"anonymous"** login signs into a **shared, pooled** account: its cookie **does** carry `SAPISID`, but the flow deliberately clears `dataSyncId` (`App.kt` / `LoginGateScreen` — `onBehalfOfUser`/dataSyncId breaks the pooled player request). So `parseCookieString(cookie).containsKey("SAPISID")` / `Context.isUserLoggedIn()` are **true for anonymous** and must **never** gate remote *account* reads or writes — doing so leaks the pooled account's library/likes/subscriptions across every anonymous user.

- The correct discriminator is **`com.jtech.zemer.extensions.AccountState`**: `isPersonalAccountSignedIn` (= non-empty `YouTube.dataSyncId`, usable from context-free entity code) and the reactive `Context.isPersonalAccountFlow()`. Gate remote account sync/writes on these — never on `SAPISID`.
- Already gated: `SyncUtils` account syncs + `likeSong`, the entity `toggleLike` remote side-effects (`Song/Artist/Album/PlaylistEntity`), and the add/remove/create/rename/delete-playlist + library/history-feedback writes in the menus. **Local DB writes always run**, so anonymous keeps likes/subscribes/playlists locally; personal logins are unaffected (each gate is a no-op when the predicate is true). The **Firebase artist-whitelist sync (`syncArtistWhitelist`) is account-independent and stays on for anon** — it powers content filtering.
- **UI account display is gated too** (#137): the Settings → Account "Signed in as" card (name/email/handle/avatar) and the **"More content"** + **"Auto sync with account"** switches render only when `isPersonalAccountSignedIn` (`AccountSettings.kt`) — never the SAPISID-based `isLoggedIn`, which would show the *pooled* account's identity and account-personalization controls to every anonymous user. The Anonymous-login button is hidden once signed in, so there is a single Logout control, not a duplicate.
- **Synced playlists reconcile non-destructively and stay 100% whitelisted** (`SyncUtils.syncSavedPlaylists`/`syncPlaylist`, #130): keep a song only if a whitelisted artist is resolvable from the playlist renderer **or the local DB row** (`filterWhitelistedWithLocalArtists`). Do **not** restore the old `clearPlaylist()` + strict `filterWhitelisted` rebuild — it wiped user-added songs whose YTM renderer carried sparse/topic-channel artist ids while they stayed in YouTube Music. A failed/empty/partial remote read must never delete a playlist or its songs.
- Still personalized-to-the-pool for anon (NOT yet gated): `YouTube.home()` (the Home feed and Android-Auto browse) uses the pooled cookie, so anon's Home can surface the pooled account's mixes (e.g. a "My top 50" row). Note the *Library* "My top 50" is a different thing — a **local** most-played auto-playlist (`mostPlayedSongs`), not a leak.

### Content filtering (whitelist, conditional id overrides, filtered covers)

The "Kosher" guarantee runs through one chokepoint — `utils/WhitelistFilter.kt` `filterWhitelisted`
(applied on every YouTube/browse/playback surface) over the artist whitelist it reads. Two layers on top
are non-obvious and regression-prone; full detail in `docs/whitelist/README.md`:

- **Conditional id overrides** (`blockedContentIds` Firestore collection → `utils/BlockedIdsCache.kt`,
  #161): a server-listed, read-only table of specific ids hidden *conditionally* by a **reason** —
  `female` hides only when `!allowFemaleSingers`, `global` (and any unknown reason) hides for everyone,
  all inert when filtering is off. The surgical complement to the artist whitelist: a *mixed* channel
  stays whitelisted while specific items from it are dropped by id. Applied centrally in
  `filterWhitelisted` **and** in `search/ZemerResultMapper.dropBlocked()` — the artist-membership
  whitelist is deliberately never run over raw Zemer search results (it would clip legitimate
  Hebrew/community hits), but a specific-id drop is safe there. Synced inside `syncArtistWhitelist` (no
  user interaction), persisted to DataStore, loaded at startup; a failed sync keeps the previous table
  (never unblocks). The `blockedContentIds` collection is managed by the separate **zemer-admin** app.
- **Playlist covers come from the filtered tracks, never the raw curator image.** A community/online
  playlist's `playlist.thumbnail` is YouTube's curator art and bypasses the filter, so a mostly-female
  playlist would otherwise show a female cover even when female is blocked.
  `ui/screens/playlist/filteredPlaylistCover(songs)` derives both the opened-playlist header cover and
  the saved-to-Library cover from the first *content-filtered* track (`songs` is already
  `filterWhitelisted`-filtered), falling back to the neutral `queue_music` placeholder / null
  `thumbnailUrl` — **never** `playlist.thumbnail`. Mirrors the local-playlist screens; don't revert
  either site.

### Zemer curated playlists (the Home "Zemer Playlists" shelf)

Hand-curated playlists served ready-to-render by the search server's `/zemer-playlists` endpoint;
full detail in `docs/zemer_playlists/README.md`. The rules that must not regress:

- **Ids are server slugs (`"acapella"`), never YouTube playlist ids** — they get their own screens
  (`zemer_playlist/{id}` detail, `zemer_playlists` see-all) and must never enter a YouTube-playlist
  code path (`online_playlist/…`, save-to-library, playlist menus).
- **All three content flags are sent explicitly on every request** (the server is default-OPEN;
  `zemerCuratedPlaylistsParameters()` is the unit-tested contract), and the repository deliberately
  does **not** cache — a plain re-fetch per screen-open is the endpoint's freshness contract and
  guarantees a response fetched under one flag set is never shown under another. No client
  re-filtering beyond the usual `dropBlocked` + `hideExplicit`.
- **Covers are server-generated SVGs at relative URLs** — resolved by `resolveZemerUrl()` and
  decoded by the `SvgDecoder` registered in `App.newImageLoader` (that's why `coil-svg` exists).
- Empty list = hidden section (normal state); detail 404 = back out + Home re-fetch. The Home shelf
  is backed by its own `ZemerCuratedPlaylistsViewModel` (LatestReleases isolation pattern) so a feed
  failure can never affect the rest of Home.
- The detail screen's **All/Albums/Songs chips reuse `LatestReleaseFilter`** and split on the
  server's `fromAlbum`/`albums` fields (both optional — an old server just empties the Albums chip).
  Rows, Play and Shuffle all read the same filtered list; rows never pass `albumIndex` (the shared
  row renders a number *instead of* artwork).
- App↔server field changes travel as request docs in `~/zemer-fix/handoff-docs/`, never as direct
  edits to the zemer-search repo.

### Tracking (anonymous usage telemetry)

Five events (`open`/`search`/`play`/`click`/`action`) POSTed to `tracking.zemer.io`; full detail in
`docs/tracking/README.md`. The rules that must not regress:

- **Telemetry may never break the app**: every `Tracker` entry point is a fire-and-forget
  `scope.launch`; failures are silent; the on-disk queue caps at 500 dropping oldest; a 400 drops
  the batch. It's a JSONL file under `filesDir` — deliberately NOT a Room table.
- **Identity is one random UUID** (`TrackingDeviceIdKey`) and nothing else — the server 400s
  non-canonical ids, so only `UUID.randomUUID()` output is ever sent. Never add account/device/
  location identifiers to an event.
- **Debug builds send `debug: true` in the batch envelope and the SERVER discards them** — the
  client path is identical in debug and release; never gate the tracker on `BuildConfig.DEBUG`.
- **`play` fires for EVERY listen, however short** (`MusicService.onPlaybackStatsReady`), one per
  listen when it ends; `source` comes from `Queue.playSource` + `Tracker.playSources`
  (context vs radio-fill vs other) — new queue types/surfaces must declare their source, and radio
  continuation must keep registering as `radio`.
- **`action` hooks live at chokepoints** (entity `toggleLike()`s, `DownloadUtil` download entry
  with `fromUser=false` for machine enqueues, `DatabaseDao.addSongToPlaylist`, share buttons) —
  don't add per-surface duplicates, and keep machine-initiated work out of the user-intent signal.
- One `search` event per executed query (the per-query ViewModel guard) — never per keystroke or
  per chip switch. Everything is tracked (KidZone and the YouTube engine included), no opt-out —
  a product decision, 2026-07-05.

### The player background system (one effective style, one extractor)

The full player (`ui/player/Player.kt`) and the mini player (`ui/player/MiniPlayer.kt`) share a
single source of truth in **`ui/player/PlayerBackground.kt`** — never re-derive any of this per
surface (the two drifting out of sync is exactly what bit a past change):

- **`PlayerBackgroundStyle.effective()`** downgrades **BLUR → DEFAULT below Android 12**. The blur is
  a `RenderEffect`, a no-op before API 31, so a raw BLUR there renders the bright artwork under the
  light-on-dark transport — illegible. Every *render* decision (background, text/icon colors, status
  bar, gradient enable) must read the **effective** style. `Player.kt` shadows the preference
  (`val playerBackground = playerBackgroundPref.effective()`) so all downstream sites are covered for
  free; the settings list hides BLUR when **`isBlurSupported`** is false. `effective(blurSupported)`
  takes the flag explicitly so the rule is unit-tested without an Android runtime
  (`app/src/test/.../ui/player/PlayerBackgroundTest.kt`).
- **`rememberPlayerGradient(mediaId, thumbnailUrl, enabled, fallbackColor)`** is the *only* gradient
  extractor: one bitmap-decode + Palette pass per track, memoised in a shared bounded `LruCache`, so
  the two surfaces never decode the same artwork twice and the cache can't grow unbounded. The
  previous palette is held while a new one extracts (and on a decode failure) to avoid a flash.
- **`playerGradientStops(colors)`** is the *only* place the gradient color stops are built (3-stop
  for ≥3 swatches, else a single-hue fade to black) — both surfaces call it so the gradient shape
  can never drift between them.
- **Light (white) content only when a dark backdrop is actually painted.** A blur layer needs a
  `thumbnailUrl`; a gradient layer needs non-empty `gradientColors`. Until then the surface stays on
  the solid `surfaceContainer` with theme-colored text — flipping to white before the backdrop
  exists puts white text over the light Home screen showing through the (transparent) mini bar.
- Status-bar legibility is a `DisposableEffect` in `Player.kt` keyed on **(background, theme,
  `state.isExpanded`)**: it forces light icons only while the sheet is **expanded** (the dark
  background actually covers the screen); collapsed/dragging follows the theme. It hands the bar back
  to the theme-correct appearance — matching `MainActivity.setSystemBarAppearance`
  (`isAppearanceLightStatusBars = !isDark`) — on dispose, never a stale captured snapshot.
- The new-design transport cluster caps the labelled play button via `BoxWithConstraints` (to the
  width left after the two skip buttons + gaps) so it shrinks to fit narrow widths instead of
  overflowing; `TransportSkipButton` cancels its long-press repeat the moment the press is released.

This UI is **Material 3 *standard*** (`MaterialTheme`, not `MaterialExpressiveTheme`): Expressive-only
APIs (e.g. `LinearWavyProgressIndicator`) need a newer material3 and are deliberately not used. New
transport buttons reuse `TransportSkipButton` + the accent focus border; new D-pad rows reuse
`Modifier.focusBorder()`. `scripts/ui-audit.sh` ratchets raw `Modifier.blur(` in `ui/` (R12) — route
player blur through the effective style.

### The download system (ONE unified path — never fork it)

Downloads go **exclusively** through `MediaStoreDownloadManager` (file saved to MediaStore, durable
truth is `SongEntity.isDownloaded` + `mediaStoreUri`; live progress in its in-memory `downloadStates`).
The legacy ExoPlayer download map (`DownloadUtil.downloads` / `getDownload()`) is **dead** for
status — nothing the UI reads should touch it.

Every download/progress affordance reads ONE path; do not re-implement per surface:
- **State (pure, tested):** `playback/DownloadStateResolver.kt` — `forSong`/`aggregateSongs`/
  `aggregateByIds` combine persisted `isDownloaded` **OR** live MediaStore state (so a download
  survives a process restart — reading the live map *alone* is the bug that makes downloads "vanish"
  after relaunch). `songProgress`/`aggregateProgress[ByIds]` for the progress fraction.
- **UI:** `ui/component/DownloadStatusUi.kt` — `rememberSongDownloadStatus/Progress`,
  `SongDownloadBadge` (default song-row badge), `AggregateDownloadButton` (album/playlist header),
  `DownloadStatusIcon`.
- **Menu rows:** `ui/menu/DownloadMenuItems.kt` `downloadMenuItem(...)`, decided by
  `playback/DownloadMenuLogic.kt` (`songRow`/`collectionRow`, pure + tested). A download row **never
  dismisses the menu** (it animates Download → progress → Remove in place). Videos use the same path
  (`DOWNLOAD_VIDEO`, hidden when videos blocked).
- **A collection NEVER shows a FAILED/retry row** (`collectionRow` takes only the aggregate status —
  REMOVE / DOWNLOADING / DOWNLOAD). A failed member just leaves the aggregate NOT_DOWNLOADED, so the
  collection offers DOWNLOAD again, which re-enqueues only the not-yet-downloaded members (= retry)
  and stays removable once everything is on disk. A dedicated collection "retry" row is a **dead end**
  — it hid Download AND Remove and re-failed the dead track forever with no escape. Only *single*
  songs get a FAILED row (`songRow`). Don't reintroduce an `anyFailed` arg on `collectionRow`.
- **Downloading a collection** whose songs load async (online album/playlist/selection menus):
  resolve/fetch the songs **at click time** (fetch-if-empty) so the first tap downloads — a
  captured-empty list is the "press once does nothing, twice works" bug. **EVERY action in that menu
  — Download, Remove, *and* the aggregate status — must read the SAME resolved/fetched list, never the
  original (possibly-empty) `songs` prop.** A Remove that iterates the empty prop while Download
  iterates the fetched list silently removes nothing (was a real bug on the Home long-press playlist
  menu). For online items aggregate by videoId (`aggregateByIds` + a persisted-downloaded id set) so
  progress animates without Room entities, and on Download **persist each `MediaMetadata`
  (`database.insert`/`transaction { insert(...) }`) THEN download** — a bare `database.song(id).first()`
  returns null for a not-yet-persisted id and the tap silently no-ops.
- **Playback of a downloaded file** (`MusicService.createDataSourceFactory`): use the local file when
  it opens; if it's genuinely gone, **stream this play AND re-enqueue a download to self-repair** —
  never crash with ENOENT, and never silently delete the `isDownloaded` flag (that makes downloads
  vanish from the Downloaded playlist). Two non-obvious rules here: (1) the self-repair must **skip
  re-enqueueing a download whose live state is already FAILED this session** (check
  `downloadUtil.mediaStoreDownloadState(id)`) — the manager only no-ops for active/complete, not
  FAILED, so a permanently-unrecoverable source would otherwise fire a fresh full download on *every*
  play; (2) the file-open probe (`downloadedFileOpens`) returns false on **any** open failure
  (FileNotFound *or* SecurityException/other) so playback streams — handing ExoPlayer a URI we just
  failed to open only fails again.
- **`database.query {}` is fire-and-forget** (it posts to an executor, doesn't suspend). NEVER split a
  single logical mutation across two `query {}` blocks that touch the same row — they race and the
  wrong one can land last. The download-mark bug was exactly this: `markSongAsDownloaded` upserted the
  row twice (relations with `isDownloaded=false`, then `isDownloaded=true`), so a downloaded song
  intermittently persisted `isDownloaded=0` with no `mediaStoreUri` — the file saved but it "didn't
  download" / streamed / vanished. Do the whole mutation in one `database.transaction {}` whose final
  write is authoritative.
- **`markSongAsDownloaded` must NOT clobber user state.** It bases the persisted row on the **existing
  DB row** (read first) and overwrites only the download-owned columns (`isDownloaded`, `dateDownload`,
  `mediaStoreUri`, `isVideo`) — a full-row `@Upsert` of the caller's `Song` would silently reset
  `liked` / `inLibrary` / library tokens when the caller handed a stale/partial `Song` (e.g. an
  album-page entity, or the like-then-auto-download race). It also backfills `duration` AND
  `thumbnailUrl` only when the existing row lacks them.
- **Backfill `duration` AND `thumbnailUrl` from the playback response** in `performDownload`
  (`playbackData.videoDetails`) — songs reached via an album/playlist page, and standalone videos
  opened from the Video player, often carry neither (showed "0:00" / no artwork in the Downloaded list).
- **A per-download video bitrate must survive a failed attempt.** `requestedVideoBitrate` is cleared on
  success / cancel / delete, **never** in the per-attempt `finally` — else `retryDownload` re-issues the
  download with no bitrate and silently falls back to best/default quality (a large file over a metered
  connection the user explicitly capped).
- **Remove must delete the actual file on EVERY backend.** A custom download path saves a SAF document
  uri; `ContentResolver.delete` silently no-ops on those, so `MediaStoreHelper.deleteFromMediaStore`
  routes document uris through `DocumentsContract.deleteDocument`.

Enforcement (so this can't regress): `scripts/check-download-unification.sh` (whole-app, wired into
the UI-audit workflow) + `scripts/ui-audit.sh` rule **R13** fail CI on any `downloadUtil.downloads` /
`getDownload(` read, any `Download.STATE_*` outside the legacy infra (`DownloadUtil.kt` /
`ExoDownloadService.kt`), or any per-surface `Icon.Download(`. Full rules: `docs/ui/standards.md §12`.
When you touch downloads run both scripts and add pure regression tests next to the resolver/menu
logic (the manager/playback layer needs Robolectric, which the project does not have — say so rather
than skip silently).

### tests/ — the hard-data streaming harness

Node ≥20 scripts (deps vendored in `tests/node_modules`, no install needed) that reproduce the app's *exact* stream path (same `/player` request as `InnerTube.kt`, same cipher run in jsdom, same poTokens) against the live CDN — so playback is measured, not guessed. Needs `innertube_cookie.txt` at the repo root (a dumped logged-in session; **gitignored**, never commit).

- Run one: `node tests/cipher.mjs` (live player health), `node tests/validate-player-config.mjs <hash>`, `node tests/web-remix-stream.mjs`. Pin a player with `PLAYER_HASH=<hash>`.
- `tests/README.md` + `tests/INVESTIGATION.md` are the methodology and the symptom-indexed runbook — read them first when streaming breaks.
- The harness mirrors app constants on purpose; when `YouTubeClient.kt` / `PoTokenGenerator.kt` change, update the matching mirror (`clients.mjs` / `potoken.mjs`). Player configs are **not** mirrored — `tests/player-configs.mjs` reads the same `player_configs.json` the app bundles (requires the cipher submodule checked out; if missing, scripts fail with an actionable message).
- Loader unit tests (no cookie or network needed): `node --test tests/player-configs.test.mjs` — validation rules, collision rejection, the `config-covers.mjs` CLI, and the cross-language parity fixtures shared with the cipher repo's Kotlin tests.
- **`tests/search/`** is the same idea for the *search* path: faithful Node ports of the app's four search functions (`searchSuggestions`/`searchSummary`/`search(filter)`×6/`searchContinuation`) run against live YouTube Music — `node tests/search/run.mjs [query...]`. It reproduces the app's exact request (WEB_REMIX, `setLogin=false` → visitorData only, no cookie/auth) and reports any error: a strict-deserialization break (a non-null field YouTube dropped → whole response fails → "No results"), a parser drop (with the exact field), the `searchContinuation` NPE, or an empty result. `node --test tests/search/self-test.mjs` proves the checker catches breaks (no network). The kotlinx strict-field table in `tests/search/schema.mjs` is transcribed from the innertube models — keep it in sync when their nullability changes. Zemer's artist-whitelist filter runs *after* these functions (needs the app DB) and is the next suspect when they're healthy but search still looks empty. See `tests/search/README.md`.

### Modules & app layout

- **`:app`** (`com.jtech.zemer`) — single-activity Jetpack Compose UI, Hilt DI (`App.kt` `@HiltAndroidApp`, modules under `di/`), Media3. `MainActivity` + `NavigationBuilder.kt` host the Compose nav graph; `MusicService` (a Media3 `MediaLibraryService`) owns ExoPlayer and is bridged to the UI by `PlayerConnection`, with `playback/queues/` implementations. State is Room (`db/MusicDatabase.kt`, `song.db`) + DataStore preferences (`utils/DataStore.kt` — holds the auth cookie / visitorData / dataSyncId and all settings). Content-filtering (whitelist, KidZone) lives in `sync/` + `utils/SyncUtils.kt`. Downloads via Media3 `ExoDownloadService` plus a MediaStore path. Crash/error telemetry is Firebase Crashlytics: `utils/CrashReportingTree.kt` (planted in `App.kt`) turns every Timber log (DEBUG+) into a breadcrumb and `reportException()` calls into non-fatal issues — so report errors via `reportException()`/`Timber`, never `printStackTrace`; release CI uploads R8 mappings and native symbols automatically.
- **`:innertube`** (`com.metrolist.innertube`) — the YouTube Music InnerTube API client (Ktor): request building, auth context, page parsers that turn YouTube renderer trees into typed models. Holds the `YouTubeClient` definitions and the NewPipe bridge for signatureTimestamp.
- **`:lrclib`** / **`:simpmusic`** (`com.metrolist.*`) — lyrics provider clients (LrcLib.net and api-lyrics.simpmusic.org).
- **`cipher`** — see "Cipher / player rotation" above.

## Documentation

`docs/` is a **code-derived docset** — most of it is generated, not hand-written:

- `docs/generate.py` regenerates `docs/repository-map.md`, `docs/build-release.md`, and `docs/reference/*.md` from tracked source (file inventory; Gradle / CI / native / JVM-module facts). It is idempotent — converges in one run — and needs PyYAML (`pip install pyyaml`) for `build-release.md`. **Never hand-edit those generated files**; change the source or the generator.
- `.github/workflows/docs-regenerate.yml` runs the generator on every push to `main` and commits any change back (`[skip ci]`), so the generated docs stay current automatically. Running `python3 docs/generate.py` locally before a commit is still good practice.
- Hand-authored docs are the exception — this `AGENTS.md`, `docs/ui/standards.md` (the UI rulebook), and prose/rationale carry intent a generator can't derive.

## Verifying your changes

- **Build both** `:app:assembleDebug` and `:app:assembleRelease` (release catches R8/shrink breakage).
- **Streaming / cipher / poToken changes** must be proven with the `tests/` harness against the live CDN (HTTP 206 / whole-song drain), and ideally confirmed on-device via the `YTPlayerUtils` logcat (`Playback: client=…, itag=…`).
- **UI changes** must comply with `docs/ui/standards.md` (the UI rulebook — Material 3 standard, design tokens, shared `Dialog.kt` dialogs, shared grouped-list components `Material3SettingsGroup`/`Material3MenuItem` per section 11) and stay 100% D-pad navigable — any new row/list component must carry the `.focusable()` + focus-border treatment, since upstream (Metrolist) rows omit it. Update the doc when a rule changes. Run `bash scripts/ui-audit.sh` — it ratchets sections 5, 7, 8 and 11 (no *new* hardcoded user-facing strings, raw `AlertDialog`s, raw font sizes, hardcoded hex colors, or raw `ListItem(` action rows under `ui/menu/`; strings and dialogs are baselined at zero, menus build from `Material3MenuGroup`).
